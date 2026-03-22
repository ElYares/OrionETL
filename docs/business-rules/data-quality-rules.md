# Data Quality Rules

Data quality rules define the standards that input data must meet to be accepted and loaded by OrionETL. These rules are enforced by `SchemaValidator` (structural rules) and `BusinessValidator` / `QualityValidator` (semantic and quality rules). Violations result in records being rejected and logged in the `etl_rejected_records` table with full context.

---

## Rule 1: All Mandatory Columns Must Be Present

**Rule ID:** `DQ-001`
**Validator:** `SchemaValidator`
**Severity:** CRITICAL

Every record in the source dataset must contain all columns listed in `validationConfig.mandatoryColumns`. A missing column is not the same as a null value — a missing column means the field is entirely absent from the source row.

**What is checked:**
- For CSV sources: all header columns in `mandatoryColumns` must be present in the file's header row. If the header row is missing a mandatory column, **all records** in the file fail this rule.
- For API/JSON sources: each JSON object in the response must contain the mandatory keys.
- For Excel sources: the header row of the configured sheet must contain all mandatory column names.

**Behavior:**
- If a mandatory column is absent from the file header: abort VALIDATE_SCHEMA immediately (100% failure, threshold always exceeded).
- If a mandatory column key is missing from an individual record (unusual for tabular sources, possible for JSON): reject that individual record.

**Configuration:**

```yaml
validation-config:
  mandatory-columns:
    - transaction_id
    - customer_id
    - amount
    - sale_date
```

---

## Rule 2: Mandatory Fields Cannot Be Null or Empty

**Rule ID:** `DQ-002`
**Validator:** `SchemaValidator`
**Severity:** CRITICAL

A mandatory field that is present in the structure but has a null, empty string, or whitespace-only value is still considered invalid.

**What is checked:**
- `null` value → fails this rule.
- Empty string `""` → fails this rule.
- Whitespace-only string `"   "` → fails this rule (after trim).
- `"null"` as a literal string → configurable (default: treated as invalid, treated as null).
- `0` (zero) for numeric fields → configurable (default: valid, as zero is a meaningful numeric value).

**Behavior:**
- Record is rejected with `ValidationError`: field=`<fieldName>`, rule=`NOT_NULL_OR_EMPTY`, severity=CRITICAL.
- Rejection message includes the row number and field name for traceability.

---

## Rule 3: Dates Must Match the Configured Format

**Rule ID:** `DQ-003`
**Validator:** `SchemaValidator`
**Severity:** CRITICAL

Any field of type `DATE`, `DATETIME`, or `TIMESTAMP` in `validationConfig.columnTypes` must be parseable using the format string specified in the configuration.

**What is checked:**
- The raw string value of each date field is parsed using the configured `dateFormat` pattern (e.g., `"yyyy-MM-dd"`, `"dd/MM/yyyy HH:mm:ss"`).
- Partial dates (e.g., `"2024-13-45"`) that are syntactically conformant but semantically invalid (month 13, day 45) are rejected.
- ISO 8601 format is always accepted as a fallback if `acceptIso8601Fallback = true`.

**Configuration:**

```yaml
validation-config:
  column-types:
    sale_date: DATE
  date-format: "yyyy-MM-dd"
  accept-iso8601-fallback: true
```

**Behavior:**
- Unparseable date → reject record, `ValidationError`: rule=`DATE_FORMAT`, message=`"Expected format yyyy-MM-dd, got: 'march 2024'"`.

---

## Rule 4: Amounts Cannot Be Negative (Unless Explicitly Configured)

**Rule ID:** `DQ-004`
**Validator:** `BusinessValidator`
**Severity:** CRITICAL (by default)

Monetary amount fields must be non-negative values unless the pipeline is explicitly configured to accept negative amounts (e.g., for credit notes, refunds, or adjustments).

**What is checked:**
- Any field listed in `validationConfig.amountFields` is checked for negativity.
- `amount < 0` → fails this rule (default behavior).
- `amount == 0` → valid (zero-value transactions are allowed by default).

**Configuration:**

```yaml
validation-config:
  amount-fields:
    - amount
    - tax_amount
    - subtotal
  allow-negative-amounts: false   # set to true for credit/refund pipelines
```

**Behavior:**
- Negative amount → reject record with `ValidationError`: rule=`NON_NEGATIVE_AMOUNT`, severity=CRITICAL.
- The rejection message includes the actual value for debugging.

---

## Rule 5: Business Keys Must Be Unique Within the Batch

**Rule ID:** `DQ-005`
**Validator:** `BusinessValidator`
**Severity:** CRITICAL

The natural/business identifier of each record (e.g., `transaction_id`, `sku + warehouse_id`) must be unique within the current processing batch. Duplicate business keys in the same batch indicate a data extraction error or source system defect.

**What is checked:**
- The combination of fields listed in `targetConfig.businessKey` is computed for every record.
- If the same business key appears more than once in the batch, all occurrences after the first are flagged.
- The first occurrence is retained; subsequent duplicates are rejected (configurable: can be set to reject ALL occurrences of a duplicated key).

**Configuration:**

```yaml
target-config:
  business-key:
    - transaction_id
```

**Behavior on duplicate:**
- `duplicateHandling: REJECT_SUBSEQUENT` — keep first, reject all others.
- `duplicateHandling: REJECT_ALL` — reject all records sharing the duplicate key.
- `duplicateHandling: CONSOLIDATE` — merge records (pipeline-specific logic, e.g., sum quantities for inventory).
- Rejected duplicates: `ValidationError`: rule=`UNIQUE_BUSINESS_KEY`, message includes the duplicate key value and row numbers.

---

## Rule 6: Duplicate Records Must Be Rejected or Consolidated Per Config

**Rule ID:** `DQ-006`
**Validator:** `BusinessValidator`
**Severity:** WARNING (by default), CRITICAL if configured

Beyond exact business key duplicates (Rule 5), near-duplicate detection identifies records that represent the same business event across different rows (e.g., same customer, same date, same amount but different `transaction_id`).

**Behavior:**
- Near-duplicate detection is optional and configured per pipeline.
- When enabled, suspected near-duplicates are flagged with `ValidationError`: rule=`NEAR_DUPLICATE`, severity=WARNING.
- Warnings do not cause rejection unless `treatWarningsAsCritical = true`.

---

## Rule 7: Catalog References Must Exist Before Loading Foreign Keys

**Rule ID:** `DQ-007`
**Validator:** `BusinessValidator`
**Severity:** CRITICAL

Any field that references a catalog or lookup table (e.g., `product_id`, `warehouse_id`, `country_code`, `salesperson_id`) must have a corresponding active record in the referenced catalog before the record can be loaded.

**Rationale:** Loading a record with a non-existent foreign key reference will either fail with a database constraint error (if FK constraints are enforced) or silently create an orphaned record (if constraints are absent). Either outcome is unacceptable.

**What is checked:**
- Each `CATALOG_LOOKUP` business rule specifies the field name and the catalog table/endpoint to check against.
- Lookup is performed in bulk (batch SELECT) for efficiency — not per record.
- Results are cached for the duration of the validation step to avoid repeated queries.

**Configuration:**

```yaml
validation-config:
  business-rules:
    - type: CATALOG_LOOKUP
      field: product_id
      catalog: products
      active-only: true
    - type: CATALOG_LOOKUP
      field: warehouse_id
      catalog: warehouses
      active-only: false
```

**Behavior:**
- `product_id` not found in `products` catalog → reject record: rule=`CATALOG_LOOKUP`, field=`product_id`, value=`{actual_value}`.

---

## Rule 8: Percentages Must Be Between 0 and 100

**Rule ID:** `DQ-008`
**Validator:** `BusinessValidator`
**Severity:** CRITICAL

Fields representing percentages, rates, or proportions must fall within the range `[0, 100]` inclusive (or `[0.0, 1.0]` if configured as a decimal proportion).

**Configuration:**

```yaml
validation-config:
  business-rules:
    - type: RANGE_CHECK
      field: discount_rate
      min: 0.0
      max: 100.0
      inclusive: true
```

**Behavior:**
- Value outside range → reject record: rule=`RANGE_CHECK`, message includes field, value, and allowed range.

---

## Rule 9: Numeric Fields Must Convert Correctly

**Rule ID:** `DQ-009`
**Validator:** `SchemaValidator`
**Severity:** CRITICAL

Numeric fields (INTEGER, DECIMAL, LONG) must parse correctly from their string representation in the source. Silent type coercion (e.g., treating `"N/A"` as 0, or `"123abc"` as 123) is **strictly prohibited**.

**What is checked:**
- `"N/A"`, `"n/a"`, `"-"`, `"null"` in a numeric field → FAIL (not silently converted to 0 or null).
- `"1,234.56"` for a locale-formatted decimal → FAIL unless a locale parser is configured.
- `"1.5"` for an INTEGER field → FAIL (decimal truncation is not accepted silently).
- `"1e10"` (scientific notation) → configurable acceptance.

**Configuration:**

```yaml
validation-config:
  column-types:
    amount: DECIMAL
    quantity: INTEGER
  numeric-options:
    allow-locale-formatting: false
    allow-scientific-notation: false
    reject-on-truncation: true
```

**Behavior:**
- Unparseable numeric → reject record: rule=`NUMERIC_TYPE_CONVERSION`, field=`amount`, value=`"N/A"`.

---

## Rule 10: Batch Abort If Invalid Record Percentage Exceeds Threshold

**Rule ID:** `DQ-010`
**Class:** `ErrorThresholdRule` (see also `EXEC-008`)
**Severity:** (triggers execution abort)

If the percentage of invalid records across the entire batch exceeds `validationConfig.errorThresholdPercent`, the entire execution is aborted. No records — valid or invalid — are loaded to the final destination.

**Formula:**
```
invalidCount = (schemaRejected + businessRejected + transformRejected)
totalRead = records from EXTRACT step
errorRate = invalidCount / totalRead * 100

if errorRate > errorThresholdPercent → ABORT
```

**Rationale:** A high rejection rate is a signal that something is fundamentally wrong with the source data. Loading even the "valid" records would produce an incomplete and misleading dataset in the destination.

**Configuration (per pipeline):**

```yaml
validation-config:
  error-threshold-percent: 5.0   # abort if more than 5% invalid
  allow-partial-load: false       # if true, load valid records even with rejections (below threshold)
```

---

## Rule 11: Each Rejected Record Must Be Traceable

**Rule ID:** `DQ-011`
**Enforced by:** `RejectedRecordPersister` (infrastructure)

Every record that is rejected at any step must be persisted with sufficient information to:

1. **Identify the source:** `execution_id`, `pipeline_id`, source file name, row number.
2. **Identify the step:** `step_name` where rejection occurred.
3. **Identify the cause:** all `ValidationError`s with field names, values, rule names, and messages.
4. **Reproduce the raw data:** the complete `raw_data` JSONB column, preserving the original values exactly as extracted.
5. **Timestamp:** `rejected_at` for audit purposes.

**Why this matters:**
- Enables operators to fix the source data and resubmit rejected records.
- Enables data stewards to investigate systematic data quality issues in upstream systems.
- Provides a complete audit trail for regulatory compliance.

**What is forbidden:**
- Logging rejected records only (without persisting to DB).
- Persisting rejected records without the full raw data.
- Discarding rejection reasons after logging.
- Aggregating multiple rejections into a single record without individual traceability.
