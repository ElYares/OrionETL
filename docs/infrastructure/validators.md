# Validators

Validators enforce data quality rules on extracted and transformed records. They implement the `DataValidator` domain contract and live in `infrastructure/validator/`. Three validators run in sequence during each pipeline execution, forming the validation chain.

---

## Domain Contract

```java
// domain/contract/DataValidator.java
public interface DataValidator {
    /**
     * Validates a list of records against the provided configuration.
     *
     * @param records   Records to validate (RawRecord for SchemaValidator,
     *                  ProcessedRecord for BusinessValidator and QualityValidator)
     * @param config    Validation configuration for this pipeline
     * @param execution The active pipeline execution (for context and step tracking)
     * @return ValidationResult containing validity flag, errors, warnings, and error rate
     */
    ValidationResult validate(
        List<?> records,
        ValidationConfig config,
        PipelineExecution execution
    );
}
```

---

## ValidationResult Structure

```java
public record ValidationResult(
    boolean isValid,            // True if no CRITICAL errors found
    List<ValidationError> errors,   // All errors (any severity)
    List<ValidationError> warnings, // WARN-severity errors (subset of errors list)
    long totalChecked,          // Total records evaluated
    long validCount,            // Records with no errors
    long invalidCount,          // Records with at least one CRITICAL error
    double errorRate,           // invalidCount / totalChecked
    DataQualityReport report    // Full quality report (from QualityValidator)
) {}
```

---

## Validation Chain

The three validators run in this fixed sequence:

```
[1] SchemaValidator      (VALIDATE_SCHEMA step)
         │
    passes? ─NO─► accumulate rejections, check threshold, possibly abort
         │ YES
[2] BusinessValidator    (VALIDATE_BUSINESS step — first phase)
         │
[3] QualityValidator     (VALIDATE_BUSINESS step — second phase: aggregate + threshold check)
         │
    errorRate > threshold? ─YES─► abort
         │ NO
    Proceed to LOAD
```

---

## SchemaValidator

**Location:** `infrastructure/validator/schema/SchemaValidator.java`
**Step:** `VALIDATE_SCHEMA`
**Input:** `List<RawRecord>` (records as extracted, before transformation)

### What It Validates

#### 1. Mandatory Column Presence

Checks that every column listed in `validationConfig.mandatoryColumns` exists as a key in each `RawRecord.data` map.

If a mandatory column is absent from the file header (i.e., missing from ALL records), this is treated as a critical structural failure:

```java
Set<String> missingColumns = mandatoryColumns.stream()
    .filter(col -> !headerColumns.contains(col))
    .collect(Collectors.toSet());

if (!missingColumns.isEmpty()) {
    // All records fail — abort immediately
    throw new ValidationException(
        "Mandatory columns missing from source: " + missingColumns
    );
}
```

#### 2. Mandatory Field Non-Nullness

For fields in `mandatoryColumns`, verifies that each record's value for that field is not null, empty string, or whitespace-only (after trimming).

```java
for (String field : mandatoryColumns) {
    Object value = record.data().get(field);
    if (isNullOrBlank(value)) {
        errors.add(new ValidationError(
            field, value, "NOT_NULL_OR_EMPTY",
            "Mandatory field is null or empty",
            ErrorSeverity.CRITICAL, record.rowNumber()
        ));
    }
}
```

#### 3. Type Compatibility

For each field with a type declaration in `validationConfig.columnTypes`, verifies that the raw string value can be parsed to the expected type.

Supported types and their parsers:

| Type Key | Java Type | Parser |
|---|---|---|
| `STRING` | `String` | No parsing — always valid if non-null. |
| `INTEGER` | `Integer` / `Long` | `Long.parseLong()` — rejects if contains non-numeric chars. |
| `DECIMAL` | `BigDecimal` | `new BigDecimal(value)` — strict, rejects locale-formatted numbers. |
| `DATE` | `LocalDate` | `LocalDate.parse(value, formatter)` — uses configured `dateFormat`. |
| `DATETIME` | `LocalDateTime` | `LocalDateTime.parse(value, formatter)`. |
| `BOOLEAN` | `Boolean` | Accepts `"true"`, `"false"`, `"1"`, `"0"`, `"yes"`, `"no"` (case-insensitive). |

Parsing failure generates a `CRITICAL` `ValidationError` with rule `TYPE_CONVERSION`.

#### 4. String Length Validation

If `maxLength` is configured for a field, strings exceeding the maximum length are flagged with `WARNING` (or `CRITICAL` if `lengthViolationSeverity = CRITICAL`).

#### 5. Regex Pattern Validation

If a `pattern` (regex) is configured for a field (e.g., for `document_number` format validation), the raw string is checked against the pattern.

### Output

Returns a `ValidationResult` split into:
- `validRecords`: passed all schema checks.
- `schemaRejectedRecords`: failed one or more CRITICAL schema checks (added to the `rejectedRecords` accumulator).

---

## BusinessValidator

**Location:** `infrastructure/validator/business/BusinessValidator.java`
**Step:** `VALIDATE_BUSINESS` (first phase)
**Input:** `List<ProcessedRecord>` (records after transformation)

### What It Validates

#### 1. Business Key Uniqueness Within Batch (Rule DQ-005)

Detects duplicate business keys within the current processing batch.

```java
Set<String> seenKeys = new HashSet<>();
for (ProcessedRecord record : records) {
    String key = buildBusinessKey(record, config.getBusinessKeyFields());
    if (!seenKeys.add(key)) {
        errors.add(new ValidationError(
            "business_key", key, "UNIQUE_BUSINESS_KEY",
            "Duplicate business key in batch: " + key,
            ErrorSeverity.CRITICAL, record.rawRecordRef()
        ));
    }
}
```

#### 2. Catalog Lookup Validation (Rule DQ-007)

For each field with a `CATALOG_LOOKUP` business rule, performs a bulk existence check:

```java
// Efficient: single query for all values in the batch
Set<String> productIds = records.stream()
    .map(r -> (String) r.transformedData().get("product_id"))
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());

Set<String> existingProductIds = productCatalogRepository.findExistingIds(productIds, activeOnly);

for (ProcessedRecord record : records) {
    String productId = (String) record.transformedData().get("product_id");
    if (productId != null && !existingProductIds.contains(productId)) {
        errors.add(new ValidationError(
            "product_id", productId, "CATALOG_LOOKUP",
            "product_id not found in products catalog: " + productId,
            ErrorSeverity.CRITICAL, record.rawRecordRef()
        ));
    }
}
```

#### 3. Range Checks (Rule DQ-008)

For each `RANGE_CHECK` rule in `validationConfig.businessRules`:

```java
BigDecimal value = (BigDecimal) record.transformedData().get(rule.getField());
if (value.compareTo(rule.getMin()) < 0 || value.compareTo(rule.getMax()) > 0) {
    errors.add(new ValidationError(..., "RANGE_CHECK", ...));
}
```

#### 4. Cross-Field Validation

For rules that compare multiple fields (e.g., `sale_date <= today`, `quantity_reserved <= quantity_on_hand`):

```java
// Example: quantity_reserved <= quantity_on_hand
Integer onHand = (Integer) record.transformedData().get("quantity_on_hand");
Integer reserved = (Integer) record.transformedData().get("quantity_reserved");
if (reserved != null && onHand != null && reserved > onHand) {
    errors.add(new ValidationError(..., "CROSS_FIELD_CONSTRAINT", ...));
}
```

#### 5. Future Date Validation

For date fields with a `FUTURE_DATE_NOT_ALLOWED` rule:

```java
Instant saleDate = (Instant) record.transformedData().get("sale_date");
if (saleDate.isAfter(Instant.now())) {
    errors.add(new ValidationError(..., "FUTURE_DATE_NOT_ALLOWED", ...));
}
```

#### 6. Active Status Check

For reference fields that require the referenced entity to be in `ACTIVE` status (e.g., `salesperson_id`):

```java
Set<String> activeSalespersonIds = salespersonRepository.findActiveIds();
// Check each record...
```

### Output

Returns a `ValidationResult` with business validation errors. Records with CRITICAL errors are moved to the `rejectedRecords` accumulator.

---

## QualityValidator

**Location:** `infrastructure/validator/quality/QualityValidator.java`
**Step:** `VALIDATE_BUSINESS` (second phase — runs after `BusinessValidator`)
**Input:** `List<ProcessedRecord>` (remaining valid records) + accumulated `rejectedRecords` count

### What It Does

The `QualityValidator` does not validate individual records. Instead, it aggregates all validation results from both prior steps and produces a `DataQualityReport` with the overall quality assessment.

#### 1. Error Rate Calculation

```java
double errorRate = (double) totalRejected / (double) totalRead * 100.0;
```

Where:
- `totalRejected` = schema rejections + business rejections + transform rejections.
- `totalRead` = original record count from EXTRACT step.

#### 2. Threshold Enforcement (Rule DQ-010)

```java
if (errorRate > config.getErrorThresholdPercent()) {
    log.error("Error rate {:.2f}% exceeds threshold {:.2f}%. Aborting execution.",
        errorRate, config.getErrorThresholdPercent());
    // Set abort flag in ValidationResult
    return ValidationResult.aborted(errorRate, report);
}
```

#### 3. DataQualityReport Generation

```java
public record DataQualityReport(
    long totalChecked,
    long validCount,
    long invalidCount,
    double errorRate,
    boolean thresholdExceeded,
    Map<String, Long> errorCountByRule,    // e.g., {"NOT_NULL_OR_EMPTY": 45, "CATALOG_LOOKUP": 12}
    Map<String, Long> errorCountByField,   // e.g., {"product_id": 30, "customer_id": 15}
    Map<String, Long> errorCountByStep,    // e.g., {"VALIDATE_SCHEMA": 20, "VALIDATE_BUSINESS": 37}
    List<String> topErrorMessages          // Top 5 most common error messages
) {}
```

The `DataQualityReport` is:
- Stored in the `ValidationResult` returned to the orchestrator.
- Included in the `AuditRecord.details` JSON for operational visibility.
- Accessible via the `GET /api/v1/executions/{id}/quality-report` endpoint (V2).

#### 4. Partial Load Decision

If `errorRate > 0` but `errorRate <= threshold` AND `config.allowPartialLoad() = true`, the validator marks the result as `VALID_WITH_REJECTIONS`. The orchestrator proceeds to LOAD but will mark the execution as `PARTIAL` in CLOSE.

---

## ValidationChain Integration

The three validators are coordinated by `ValidationChainExecutor` in the application layer:

```java
@Component
public class ValidationChainExecutor {

    private final SchemaValidator schemaValidator;
    private final BusinessValidator businessValidator;
    private final QualityValidator qualityValidator;

    public ChainValidationResult executeSchemaValidation(
            List<RawRecord> records,
            ValidationConfig config,
            PipelineExecution execution) {

        ValidationResult schemaResult = schemaValidator.validate(records, config, execution);
        List<RawRecord> validRecords = filterOutRejected(records, schemaResult);
        List<RejectedRecord> rejected = buildRejectedRecords(records, schemaResult, "VALIDATE_SCHEMA");
        return new ChainValidationResult(validRecords, rejected, schemaResult);
    }

    public ChainValidationResult executeBusinessValidation(
            List<ProcessedRecord> records,
            long totalRead,
            long alreadyRejected,
            ValidationConfig config,
            PipelineExecution execution) {

        ValidationResult businessResult = businessValidator.validate(records, config, execution);
        List<ProcessedRecord> validRecords = filterOutRejected(records, businessResult);
        List<RejectedRecord> rejected = buildRejectedRecords(records, businessResult, "VALIDATE_BUSINESS");

        long totalRejected = alreadyRejected + businessResult.invalidCount();
        ValidationResult qualityResult = qualityValidator.validate(
            totalRead, totalRejected, config, execution);

        return new ChainValidationResult(validRecords, rejected, qualityResult);
    }
}
```

---

## Summary: Which Validator Checks What

| Check | SchemaValidator | BusinessValidator | QualityValidator |
|---|---|---|---|
| Mandatory column presence | Yes | — | — |
| Mandatory field non-null | Yes | — | — |
| Type compatibility | Yes | — | — |
| String pattern (regex) | Yes | — | — |
| Business key uniqueness in batch | — | Yes | — |
| Catalog lookup | — | Yes | — |
| Range check | — | Yes | — |
| Cross-field constraint | — | Yes | — |
| Future date check | — | Yes | — |
| Active reference check | — | Yes | — |
| Error rate calculation | — | — | Yes |
| Threshold enforcement | — | — | Yes |
| DataQualityReport | — | — | Yes |
| Abort decision | Partial* | — | Yes |

*SchemaValidator can trigger an immediate abort if mandatory columns are missing from the file header (100% failure rate).
