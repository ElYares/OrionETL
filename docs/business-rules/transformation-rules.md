# Transformation Rules

Transformation rules define how raw, source-specific data is normalized into the canonical format required by OrionETL's destination schema. These rules are applied during the `TRANSFORM` step by the transformer chain. `CommonTransformer` applies rules 1–10 universally; pipeline-specific transformers may apply additional or overriding logic.

---

## Rule 1: Column Names Must Be Normalized to Corporate Standard (snake_case)

**Rule ID:** `TR-001`
**Transformer:** `CommonTransformer`

All output column names must conform to `snake_case` naming convention, regardless of how they appear in the source.

**Examples:**

| Source Column Name | Normalized Name |
|---|---|
| `TransactionID` | `transaction_id` |
| `CustomerName` | `customer_name` |
| `SALE DATE` | `sale_date` |
| `sale-date` | `sale_date` |
| `SaleDate` | `sale_date` |
| `ProductId` | `product_id` |

**Algorithm:**
1. Trim leading and trailing whitespace.
2. Replace spaces, hyphens, and dots with underscores.
3. Convert camelCase and PascalCase to snake_case using regex: `([A-Z]+)([A-Z][a-z])` and `([a-z\d])([A-Z])`.
4. Collapse consecutive underscores into a single underscore.
5. Convert to lowercase.

**Override:** If `sourceConfig.headerMapping` is configured, the explicit mapping takes precedence over this normalization algorithm.

---

## Rule 2: Monetary Amounts Must Be Normalized to Base Currency

**Rule ID:** `TR-002`
**Transformer:** `CommonTransformer`

All monetary amount fields must be converted from their source currency to the pipeline's configured base currency (typically USD or the organization's reporting currency).

**Process:**
1. Identify the source currency from the `currency` field in the record (or from a fixed source currency configured in `transformationConfig`).
2. Look up the conversion rate from the currency rate provider (configurable: static rates in YAML, or a live rate API).
3. Apply the conversion: `amount_in_base_currency = amount * exchange_rate`.
4. Round the result according to **Rule 10** (monetary rounding policy).
5. Store the original amount and original currency alongside the converted values for traceability.

**Output fields produced:**
- `amount` → the converted amount in base currency.
- `amount_original` → the original amount (before conversion), for audit.
- `currency_original` → the original currency code (e.g., `"EUR"`).
- `currency` → the base currency code (e.g., `"USD"`).

**If currency field is missing or unknown:**
- If currency is mandatory (`validationConfig.mandatoryColumns` includes `currency`), record is rejected (Rule DQ-002).
- If currency is optional and absent: use configured default currency without conversion.

---

## Rule 3: Timestamps Must Be Converted to UTC

**Rule ID:** `TR-003`
**Transformer:** `CommonTransformer`

All date and timestamp fields must be stored in UTC timezone, regardless of the timezone present in the source data.

**Process:**
1. Parse the source value using the configured `dateFormat` (Rule DQ-003).
2. If the parsed value includes timezone information (e.g., ISO 8601 with offset `+05:30`), convert directly to UTC.
3. If the parsed value has no timezone information (naive datetime), assume the timezone specified in `transformationConfig.sourceTimezone` and convert to UTC.
4. Output as `Instant` (nanosecond-precise UTC epoch) internally. Stored as `TIMESTAMPTZ` in PostgreSQL.

**Configuration:**

```yaml
transformation-config:
  source-timezone: "America/Bogota"
  date-fields:
    - sale_date
    - created_at
    - last_updated
```

**Important edge cases:**
- Daylight saving time transitions: handled by Java's `ZoneId` correctly when using named zones (e.g., `"America/New_York"`) instead of fixed offsets.
- Ambiguous times (during clock fallback): the earlier occurrence is used by default. Configurable.

---

## Rule 4: String Fields Must Be Trimmed

**Rule ID:** `TR-004`
**Transformer:** `CommonTransformer`

All string values must have leading and trailing whitespace removed before any further processing or validation.

**Applies to:** all fields with type `STRING` in `validationConfig.columnTypes`, and any field not explicitly typed (treated as STRING by default).

**What is removed:**
- Regular spaces (U+0020).
- Tabs (U+0009).
- Non-breaking spaces (U+00A0).
- Other Unicode whitespace characters.

**What is NOT modified:**
- Internal whitespace within multi-word values (e.g., `"John  Doe"` → not collapsed to `"John Doe"` unless `collapseInternalSpaces = true` is configured).

**Execution order:** Trimming is the **first** transformation applied to every string field, before any other transformation (type conversion, normalization, mapping).

---

## Rule 5: External Status Codes Must Be Mapped to Internal Catalog Values

**Rule ID:** `TR-005`
**Transformer:** `CommonTransformer` (with pipeline-specific mapping tables)

Source systems often use their own status codes, type codes, or category codes that differ from the internal canonical values used by OrionETL's destination schema. These must be mapped during transformation.

**Examples:**

| Source Value | Internal Value | Context |
|---|---|---|
| `"ACTV"` | `"ACTIVE"` | Customer status |
| `"C"` | `"CANCELLED"` | Order status |
| `"1"` | `"ONLINE"` | Sales channel |
| `"2"` | `"IN_STORE"` | Sales channel |
| `"PEN"` | `"PENDING"` | Transaction status |

**Process:**
1. For each field with a `CODE_MAPPING` transformation rule, load the mapping table from config.
2. Apply: `outputValue = mappingTable.get(rawValue)`.
3. If the raw value has no mapping entry:
   - `onMissingMapping: REJECT` → reject record with `ValidationError`: rule=`CODE_MAPPING`, message includes unmapped value.
   - `onMissingMapping: PASS_THROUGH` → keep original value (log WARNING).
   - `onMissingMapping: DEFAULT` → use configured default value (log WARNING).

**Configuration:**

```yaml
transformation-config:
  mappings:
    - field: sales_channel
      on-missing: REJECT
      map:
        "1": "ONLINE"
        "2": "IN_STORE"
        "3": "PHONE"
        "4": "PARTNER"
```

---

## Rule 6: Third-Party Codes Must Be Mapped to Internal Catalogs

**Rule ID:** `TR-006`
**Transformer:** `CommonTransformer` / pipeline-specific transformers

Third-party identifiers (codes from external systems, partner IDs, legacy system codes) must be translated to internal IDs before loading.

**Difference from Rule 5:** Rule 5 covers static in-config mappings for enumerable values. Rule 6 covers dynamic catalog lookups where the mapping table lives in the database (e.g., a `product_catalog` table that maps external product codes to internal product UUIDs).

**Process:**
1. Identify fields with `CATALOG_TRANSLATE` transformation type.
2. Load the translation table from the configured catalog source (DB table or reference API).
3. Replace each source value with its internal equivalent.
4. Cache catalog data for the duration of the transformation step.

**Behavior on missing translation:** Same options as Rule 5 (`REJECT`, `PASS_THROUGH`, `DEFAULT`).

---

## Rule 7: Name/Lastname Fields Handled Per Configuration

**Rule ID:** `TR-007`
**Transformer:** `CustomerTransformer`

Source data may provide customer names as a single combined field (`"John Doe"`) or as separate fields (`first_name`, `last_name`). The configuration defines how to handle each case.

**Split mode** (single source field → two target fields):

```yaml
transformation-config:
  name-handling:
    mode: SPLIT
    source-field: full_name
    first-name-field: first_name
    last-name-field: last_name
    split-strategy: FIRST_SPACE   # split on first space; everything before = first_name
```

**Combine mode** (two source fields → single target field):

```yaml
transformation-config:
  name-handling:
    mode: COMBINE
    first-name-field: first_name
    last-name-field: last_name
    target-field: full_name
    separator: " "
```

**Normalization applied to name fields (regardless of mode):**
- Trim (Rule 4).
- Title case: `"JOHN DOE"` → `"John Doe"`, `"john doe"` → `"John Doe"`.
- Collapse consecutive spaces.

**Edge cases:**
- Single-word name (no space for split): configurable — treat entire value as `last_name`, leave `first_name` empty, or reject.
- Empty after trim: reject (mandatory field).

---

## Rule 8: Composite Fields Must Be Split Per Schema Definition

**Rule ID:** `TR-008`
**Transformer:** `CommonTransformer`

Source data sometimes packs multiple values into a single field using a delimiter (e.g., `"TAG1|TAG2|TAG3"`, `"123-456-789"`). These must be decomposed per the configured schema.

**Process:**
1. Identify fields with `SPLIT` transformation type.
2. Apply the configured delimiter to produce a list.
3. Map each positional element to its target field name.
4. If the source field has fewer elements than expected: fill missing positions with `null` (or configured default).
5. If the source field has more elements than expected: configurable — truncate extras or reject.

**Configuration:**

```yaml
transformation-config:
  splits:
    - source-field: address_combined
      delimiter: ","
      targets:
        0: street
        1: city
        2: postal_code
        3: country
```

---

## Rule 9: Derived Columns Must Be Calculated Per Formula Configuration

**Rule ID:** `TR-009`
**Transformer:** `CommonTransformer` / pipeline-specific transformers

Some target columns do not exist in the source data but are derived from other fields using configured formulas. These derivations are applied after all other transformations (since they may depend on normalized values).

**Types of derivations supported:**

| Type | Example |
|---|---|
| `ARITHMETIC` | `subtotal = quantity * unit_price` |
| `PERCENTAGE` | `tax_amount = subtotal * tax_rate / 100` |
| `CONCATENATION` | `full_address = street + ", " + city + " " + postal_code` |
| `DATE_DIFF` | `days_since_registration = today - registration_date` |
| `CONDITIONAL` | `status_label = if status == "A" then "Active" else "Inactive"` |
| `LOOKUP` | `region_name = lookup(country_code, country_region_map)` |

**Configuration:**

```yaml
transformation-config:
  derived-columns:
    - name: subtotal
      type: ARITHMETIC
      formula: "quantity * unit_price"
    - name: tax_amount
      type: ARITHMETIC
      formula: "subtotal * 0.19"
    - name: total_amount
      type: ARITHMETIC
      formula: "subtotal + tax_amount"
```

**Execution order:** Derived columns are computed in the order listed in the configuration, so later formulas can reference earlier derived columns (e.g., `tax_amount` can reference `subtotal`).

**Error handling:** If a formula fails (e.g., division by zero, null operand), the record is rejected with `ValidationError`: rule=`DERIVED_COLUMN_CALCULATION`, unless the field is optional (then null is used).

---

## Rule 10: Rounding of Monetary Amounts Per Configured Policy

**Rule ID:** `TR-010`
**Transformer:** `CommonTransformer`

All monetary amounts must be rounded using a consistently configured rounding policy. Inconsistent rounding across records leads to balance mismatches and audit discrepancies.

**Configuration:**

```yaml
transformation-config:
  monetary-rounding:
    scale: 2              # number of decimal places
    mode: HALF_UP         # rounding mode (HALF_UP, HALF_EVEN, FLOOR, CEILING)
    apply-to-fields:
      - amount
      - subtotal
      - tax_amount
      - total_amount
```

**Java implementation:**
```java
BigDecimal rounded = rawAmount.setScale(scale, RoundingMode.HALF_UP);
```

**Important:** All monetary calculations (derived columns in Rule 9) must use `BigDecimal` arithmetic, never `double` or `float`. IEEE 754 floating-point arithmetic is explicitly prohibited for monetary calculations due to precision loss.

**Default policy:** If no rounding config is provided, the default is `scale=2, mode=HALF_UP` — suitable for most financial reporting contexts.
