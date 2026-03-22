# Transformers

Transformers convert raw, source-specific data into normalized, canonical records ready for validation and loading. They implement the `DataTransformer` domain contract and live in `infrastructure/transformer/`.

Each pipeline uses a **transformer chain**: transformers are applied in sequence, and the output of each transformer is the input to the next. `CommonTransformer` is always the first in the chain, providing universal normalization. Pipeline-specific transformers follow.

---

## Domain Contract

```java
// domain/contract/DataTransformer.java
public interface DataTransformer {
    /**
     * Returns true if this transformer should be applied to the given pipeline type.
     * CommonTransformer always returns true. Pipeline-specific transformers return true
     * only for their pipeline.
     */
    boolean supports(String pipelineType);

    /**
     * Transforms a list of raw records into processed records.
     * Records that cannot be transformed are extracted and returned as rejected records.
     *
     * @param records   List of RawRecord objects from the extraction step
     * @param config    Transformation configuration for this pipeline
     * @param execution The active pipeline execution
     * @return TransformationResult containing processed records and any rejected records
     */
    TransformationResult transform(
        List<RawRecord> records,
        TransformationConfig config,
        PipelineExecution execution
    );
}
```

### `TransformationResult`

```java
public record TransformationResult(
    List<ProcessedRecord> processedRecords,
    List<RejectedRecord> rejectedRecords,
    long successCount,
    long rejectedCount,
    Duration duration
) {}
```

---

## Transformer Chain

The transformer chain is assembled by `TransformerChain` (a component in `infrastructure/transformer/`) which:

1. Injects all `DataTransformer` beans from the Spring context.
2. Filters to those where `supports(pipelineType)` returns true.
3. Sorts by `@Order` annotation (CommonTransformer is `@Order(1)`, pipeline-specific transformers have higher order values).
4. Applies them sequentially, passing the output of each as the input to the next.

```java
@Component
public class TransformerChain {

    private final List<DataTransformer> transformers;

    public TransformationResult applyChain(
            String pipelineType,
            List<RawRecord> records,
            TransformationConfig config,
            PipelineExecution execution) {

        List<RawRecord> current = new ArrayList<>(records);
        List<RejectedRecord> allRejected = new ArrayList<>();

        List<DataTransformer> applicableTransformers = transformers.stream()
            .filter(t -> t.supports(pipelineType))
            .sorted(Comparator.comparingInt(t -> AnnotationUtils.findAnnotation(t.getClass(), Order.class).value()))
            .toList();

        for (DataTransformer transformer : applicableTransformers) {
            TransformationResult result = transformer.transform(current, config, execution);
            // The next transformer receives only the successfully processed records
            current = result.processedRecords().stream()
                .map(pr -> new RawRecord(pr.rawRecordRef(), pr.transformedData(), ...))
                .toList();
            allRejected.addAll(result.rejectedRecords());
        }

        // Build final ProcessedRecord list from the final current list
        ...
    }
}
```

---

## CommonTransformer

**Location:** `infrastructure/transformer/common/CommonTransformer.java`
**Order:** `@Order(1)` — always first in the chain
**Applies to:** All pipelines (`supports()` always returns `true`)

### Responsibilities

`CommonTransformer` applies transformations that are universal across all pipelines. It encodes the general transformation rules (TR-001 through TR-010 from the transformation rules document).

#### 1. String Trimming (Rule TR-004)

All string values are trimmed of leading/trailing whitespace (including non-breaking spaces).

```java
private Object trimIfString(Object value) {
    if (value instanceof String s) {
        return s.strip();  // Java 11+ strip() handles Unicode whitespace
    }
    return value;
}
```

#### 2. Column Name Normalization (Rule TR-001)

All column/field names in the `data` map are converted to `snake_case`.

```java
private String toSnakeCase(String name) {
    return name
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
        .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
        .replaceAll("[\\s\\-.]", "_")
        .replaceAll("_+", "_")
        .toLowerCase()
        .strip();
}
```

#### 3. Date/Timestamp Parsing and UTC Conversion (Rule TR-003)

Fields listed in `transformationConfig.dateFields` are parsed using the configured format and converted to UTC `Instant`.

```java
private Instant parseToUtc(String raw, String format, String sourceTimezone) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
    LocalDateTime localDt = LocalDateTime.parse(raw.strip(), formatter);
    ZoneId zone = ZoneId.of(sourceTimezone);
    return localDt.atZone(zone).toInstant();
}
```

#### 4. Currency Conversion (Rule TR-002)

Fields listed in `transformationConfig.amountFields` are converted from the record's `currency` field to the configured `baseCurrency`.

- Original value preserved in `{field}_original`.
- Original currency preserved in `currency_original`.
- Conversion uses `BigDecimal` arithmetic (no floating point).

#### 5. Code/Status Mapping (Rule TR-005)

Static in-config code mappings are applied to configured fields. Records with unmappable codes are rejected when `onMissingMapping = REJECT`.

#### 6. Null Value Normalization

Strings matching the configured `nullValues` list (e.g., `"NULL"`, `"N/A"`, `"-"`) are converted to Java `null`.

#### 7. Numeric Type Conversion

Fields with expected numeric types are converted from String to the appropriate Java type (`Integer`, `Long`, `BigDecimal`). Unconvertible values cause record rejection (Rule DQ-009).

#### 8. Derived Column Calculation (Rule TR-009)

Formulas configured in `transformationConfig.derivedColumns` are evaluated in order. Uses a simple expression evaluator or SpEL (Spring Expression Language) if enabled.

#### 9. Monetary Rounding (Rule TR-010)

All fields listed in `transformationConfig.monetaryRounding.applyToFields` are rounded using the configured `scale` and `RoundingMode`.

---

## SalesTransformer

**Location:** `infrastructure/transformer/sales/SalesTransformer.java`
**Order:** `@Order(10)`
**Applies to:** `pipelineType = "sales-daily"`

### Responsibilities

Applies sales-specific transformations after `CommonTransformer` has run.

#### 1. Quantity Defaulting

Sets `product_quantity = 1` if the field is null or absent.

#### 2. Discount Rate Defaulting

Sets `discount_rate = BigDecimal.ZERO` if null or absent.

#### 3. Sales Channel Mapping

Maps source channel codes to internal catalog values (see Sales Pipeline documentation for the full mapping table). Implemented via the common code mapping mechanism, but the mapping table is loaded from `sales.yml`.

#### 4. Subtotal Calculation

```java
BigDecimal subtotal = amount
    .multiply(new BigDecimal(quantity))
    .multiply(BigDecimal.ONE.subtract(discountRate.divide(HUNDRED, 10, RoundingMode.HALF_UP)))
    .setScale(2, RoundingMode.HALF_UP);
```

#### 5. Tax Calculation

```java
BigDecimal taxRate = resolveTaxRate(channel, productType, config);  // from config
BigDecimal taxAmount = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
```

#### 6. Total Amount Calculation

```java
BigDecimal totalAmount = subtotal.add(taxAmount);
```

#### 7. Future Date Validation for Partner Channel

If `allowFutureDateForPartner = true` and `channel = "PARTNER"`, allows `sale_date` up to 48 hours in the future. For all other channels, future dates cause rejection.

---

## InventoryTransformer

**Location:** `infrastructure/transformer/inventory/InventoryTransformer.java`
**Order:** `@Order(10)`
**Applies to:** `pipelineType = "inventory-sync"`

### Responsibilities

#### 1. SKU Normalization

```java
private String normalizeSku(String raw, SkuNormalizationConfig config) {
    String sku = raw.strip();
    if (config.isUppercase()) sku = sku.toUpperCase();
    if (config.isReplaceSpacesWithHyphens()) sku = sku.replaceAll("\\s+", "-");
    if (config.isRemoveLeadingZeros()) sku = removeLeadingZerosFromSegments(sku);
    return sku;
}
```

#### 2. Warehouse Code Translation

Translates WMS warehouse codes to internal warehouse UUIDs via the `warehouses` catalog. The catalog is loaded once at the start of the transformation step and cached for the duration.

#### 3. Quantity Defaulting

Sets `quantity_reserved = 0` if null or absent.

#### 4. Record Consolidation

After individual record transformations, groups records by `(sku, warehouse_id)` and consolidates per the configured `duplicateHandling` policy:

- `CONSOLIDATE`: sum `quantity_on_hand` and `quantity_reserved`, use latest `last_updated` and corresponding `unit_cost`.
- `REJECT_SUBSEQUENT`: keep first, reject remaining duplicates.
- `REJECT_ALL`: reject all records where the `(sku, warehouse_id)` pair appears more than once.

```java
private List<ProcessedRecord> consolidate(
        List<ProcessedRecord> records,
        DuplicateHandling policy) {

    Map<BusinessKey, List<ProcessedRecord>> grouped = records.stream()
        .collect(Collectors.groupingBy(r -> buildSkuWarehouseKey(r)));

    return grouped.entrySet().stream()
        .flatMap(entry -> {
            List<ProcessedRecord> group = entry.getValue();
            return switch (policy) {
                case CONSOLIDATE -> Stream.of(mergeRecords(group));
                case REJECT_SUBSEQUENT -> Stream.concat(
                    Stream.of(group.getFirst()),
                    group.stream().skip(1).map(r -> toRejected(r, "DUPLICATE_CONSOLIDATED"))
                );
                case REJECT_ALL -> group.size() > 1
                    ? group.stream().map(r -> toRejected(r, "DUPLICATE_KEY"))
                    : group.stream();
            };
        })
        .filter(r -> r instanceof ProcessedRecord)
        .map(r -> (ProcessedRecord) r)
        .toList();
}
```

---

## CustomerTransformer

**Location:** `infrastructure/transformer/customer/CustomerTransformer.java`
**Order:** `@Order(10)`
**Applies to:** `pipelineType = "customer-sync"`

### Responsibilities

#### 1. Name Title-Casing

```java
private String toTitleCase(String name) {
    if (name == null || name.isBlank()) return name;
    return Arrays.stream(name.strip().split("\\s+"))
        .map(word -> word.isEmpty() ? word :
            Character.toTitleCase(word.charAt(0)) + word.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
}
```

Handles special cases:
- Hyphenated names: `"MARY-ANNE"` → `"Mary-Anne"`.
- Apostrophes: `"O'BRIEN"` → `"O'Brien"`.
- Particles (configurable): `"DE LA CRUZ"` → `"de la Cruz"` if particles list configured.

#### 2. Email Normalization

```java
private String normalizeEmail(String email) {
    return email.strip().toLowerCase();
}
```

RFC 5322 format validation is done in `BusinessValidator`, not here. Transformation only normalizes.

#### 3. Phone E.164 Normalization

Uses Google's `libphonenumber` library for robust phone parsing:

```java
private String normalizeToE164(String rawPhone, String countryCode) {
    if (rawPhone == null || rawPhone.isBlank()) return null;
    try {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber parsed = phoneUtil.parse(rawPhone, countryCode);
        if (phoneUtil.isValidNumber(parsed)) {
            return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        }
    } catch (NumberParseException e) {
        log.warn("Could not parse phone: '{}' for country: {}", rawPhone, countryCode);
    }
    return null;  // Invalid phone → null (phone is optional; not a rejection)
}
```

#### 4. Country Code ISO Normalization

Resolves alpha-3 and numeric country codes to alpha-2 using a static lookup table (loaded from `country_codes.json` in resources).

#### 5. Status Mapping

Maps CRM status codes (`"A"`, `"I"`, `"S"`, `"C"`) to internal values (`"ACTIVE"`, `"INACTIVE"`, `"SUSPENDED"`, `"CLOSED"`).

---

## Adding a New Transformer

To add a transformer for a new pipeline (e.g., `crypto-pipeline`):

1. Create `infrastructure/transformer/crypto/CryptoTransformer.java` implementing `DataTransformer`.
2. Annotate with `@Component` and `@Order(10)`.
3. Implement `supports()` to return `true` only for `"crypto-pipeline"`.
4. Register the pipeline type string as a constant in `shared/constants/PipelineTypes.java`.
5. Write unit tests in `test/unit/infrastructure/transformer/CryptoTransformerTest.java`.
6. No other configuration changes needed — `TransformerChain` automatically discovers the new bean.
