# Sales Pipeline

## Overview

| Property | Value |
|---|---|
| **Pipeline ID** | `sales-daily` |
| **Version** | `1.0.0` |
| **Purpose** | Process daily sales transaction data from field sales systems and e-commerce platforms |
| **Status** | Active |
| **Owner** | Revenue Operations Team |

The Sales pipeline ingests raw sales transaction records from either a CSV export (from the ERP system) or a REST API endpoint (from the e-commerce platform). It normalizes the data to the corporate standard, validates business rules, and loads confirmed transactions into the `sales_transactions` PostgreSQL table.

---

## Schedule

| Property | Value |
|---|---|
| **Schedule** | Daily at **02:00 UTC** |
| **Timezone** | UTC |
| **Allowed Window** | 01:30 UTC – 04:00 UTC |
| **Trigger Type** | SCHEDULED (can also be triggered manually via API) |
| **Days** | Monday through Sunday (daily) |

The pipeline runs at 02:00 UTC to minimize overlap with peak transactional hours in any timezone. The allowed window extends slightly before and after the scheduled time to accommodate late triggers.

---

## Source Configuration

### Option A: CSV File

| Property | Value |
|---|---|
| **Type** | `CSV` |
| **File Location** | Configurable (default: `/data/incoming/sales/sales_{yyyy-MM-dd}.csv`) |
| **Delimiter** | `,` (comma) |
| **Encoding** | `UTF-8` |
| **Has Headers** | `true` |
| **Quote Character** | `"` (double quote) |

### Option B: REST API

| Property | Value |
|---|---|
| **Type** | `API` |
| **Method** | `GET` |
| **URL** | `https://api.ecommerce.internal/v2/transactions` |
| **Auth** | Bearer token (configured via environment variable `ECOMMERCE_API_TOKEN`) |
| **Pagination** | Cursor-based (`?cursor={next_cursor}&limit=500`) |
| **Response Format** | JSON array under key `transactions` |
| **Retry on 5xx** | Yes (3 attempts, 30s delay) |

The active source type is determined by `sourceConfig.type` in `pipelines/sales.yml`. Both sources produce records with the same input schema.

---

## Input Schema

| Field | Type | Mandatory | Description |
|---|---|---|---|
| `transaction_id` | STRING | Yes | Unique transaction identifier from source system. |
| `customer_id` | STRING | Yes | Customer identifier (reference to customers table). |
| `product_id` | STRING | Yes | Product identifier (reference to products catalog). |
| `amount` | DECIMAL | Yes | Gross transaction amount in the `currency` field's denomination. |
| `currency` | STRING | Yes | ISO 4217 currency code (e.g., `"USD"`, `"EUR"`, `"COP"`). |
| `sale_date` | DATE | Yes | Date of the transaction. Format: `yyyy-MM-dd`. |
| `salesperson_id` | STRING | Yes | Identifier of the salesperson who made the sale. |
| `channel` | STRING | Yes | Sales channel code from source system (e.g., `"1"`, `"ONLINE"`, `"STORE"`). |
| `product_quantity` | INTEGER | No | Number of units sold. Defaults to 1 if absent. |
| `discount_rate` | DECIMAL | No | Discount percentage applied (0–100). Defaults to 0. |
| `notes` | STRING | No | Free-text transaction notes. |

---

## Transformations

Transformations are applied in the following order: `CommonTransformer` first, then `SalesTransformer`.

### CommonTransformer (applied to all fields)

1. **Trim all string fields** (Rule TR-004).
2. **Normalize column names to snake_case** (Rule TR-001) — source columns may be camelCase or PascalCase.
3. **Parse `sale_date`** as `DATE` using format `yyyy-MM-dd`, convert to UTC Instant (Rule TR-003).
4. **Convert `amount` to USD** using configured exchange rates (Rule TR-002). Original `amount` and `currency` are preserved in `amount_original` and `currency_original`.
5. **Round `amount` to 2 decimal places** using `HALF_UP` policy (Rule TR-010).

### SalesTransformer (sales-specific)

6. **Map `channel`** from source codes to internal catalog values (Rule TR-005):

   | Source Code | Internal Value |
   |---|---|
   | `"1"` or `"ONLINE"` | `"ONLINE"` |
   | `"2"` or `"STORE"` | `"IN_STORE"` |
   | `"3"` or `"PHONE"` | `"PHONE"` |
   | `"4"` or `"PARTNER"` | `"PARTNER"` |
   | `"5"` or `"MARKETPLACE"` | `"MARKETPLACE"` |

7. **Default `product_quantity`** to `1` if absent or null.
8. **Default `discount_rate`** to `0.0` if absent or null.
9. **Calculate `subtotal`** (derived column, Rule TR-009):
   ```
   subtotal = amount * product_quantity * (1 - discount_rate / 100)
   ```
10. **Calculate `tax_amount`** (derived column, using configured tax rate per channel/product type — default 19%):
    ```
    tax_amount = subtotal * tax_rate
    ```
11. **Calculate `total_amount`**:
    ```
    total_amount = subtotal + tax_amount
    ```
12. **Round** `subtotal`, `tax_amount`, `total_amount` to 2 decimal places (Rule TR-010).

---

## Business Rules

| Rule | Field | Condition | Action |
|---|---|---|---|
| `BR-SALES-001` | `amount` | Must be >= 0 | Reject record (Rule DQ-004) |
| `BR-SALES-002` | `product_id` | Must exist in `products` catalog (active products only) | Reject record (Rule DQ-007) |
| `BR-SALES-003` | `customer_id` | Must exist in `customers` table | Reject record |
| `BR-SALES-004` | `sale_date` | Must not be a future date (> today UTC) | Reject record |
| `BR-SALES-005` | `salesperson_id` | Must exist in `salespeople` catalog AND status must be `ACTIVE` | Reject record |
| `BR-SALES-006` | `channel` | Must map to a known internal value (see transformation table above) | Reject record if `onMissingMapping: REJECT` |
| `BR-SALES-007` | `transaction_id` | Must be unique within the batch AND not already exist in `sales_transactions` (INSERT) | Reject duplicates (Rule DQ-005) |
| `BR-SALES-008` | `discount_rate` | If present, must be between 0 and 100 inclusive | Reject record (Rule DQ-008) |
| `BR-SALES-009` | `currency` | Must be a valid ISO 4217 code with available exchange rate | Reject record |
| `BR-SALES-010` | `sale_date` | Must not be more than 365 days in the past (no historical backdating beyond 1 year) | Reject record with WARNING (configurable) |

**Special policy for future sale_date:**
Certain partner channel transactions may have a booking date that is slightly in the future (up to 48 hours). This is allowed only for `channel = "PARTNER"` and only when `allowFutureDateForPartner = true` is configured.

---

## Validation Configuration

| Property | Value |
|---|---|
| **Error Threshold** | `5.0%` — abort if more than 5% of records are invalid |
| **Allow Partial Load** | `true` — load valid records even if some are rejected (below threshold) |
| **Mandatory Columns** | `transaction_id`, `customer_id`, `product_id`, `amount`, `currency`, `sale_date`, `salesperson_id`, `channel` |

---

## Target Configuration

| Property | Value |
|---|---|
| **Type** | `DATABASE` |
| **Table** | `sales_transactions` |
| **Schema** | `public` |
| **Staging Table** | `sales_transactions_staging` |
| **Load Strategy** | `UPSERT` |
| **Business Key** | `transaction_id` |
| **Chunk Size** | `500` records per transaction |
| **Rollback Strategy** | `DELETE_BY_EXECUTION` |

### Target Table Column Mapping

| Source Field | Target Column | Notes |
|---|---|---|
| `transaction_id` | `transaction_id` | Business key |
| `customer_id` | `customer_id` | FK → customers |
| `product_id` | `product_id` | FK → products |
| `amount_original` | `amount_original` | Pre-conversion amount |
| `currency_original` | `currency_original` | Pre-conversion currency |
| `amount` | `amount_usd` | Converted to USD |
| `currency` | `currency` | Always `"USD"` after transform |
| `sale_date` | `sale_date` | UTC date |
| `salesperson_id` | `salesperson_id` | FK → salespeople |
| `channel` | `channel` | Internal catalog value |
| `product_quantity` | `quantity` | Defaulted to 1 |
| `discount_rate` | `discount_rate` | Defaulted to 0 |
| `subtotal` | `subtotal` | Calculated |
| `tax_amount` | `tax_amount` | Calculated |
| `total_amount` | `total_amount` | Calculated |
| _(injected)_ | `etl_execution_id` | Traceability |
| _(injected)_ | `etl_load_timestamp` | Traceability |
| _(injected)_ | `etl_source_file` | Traceability |

---

## Retry Policy

| Property | Value |
|---|---|
| **Max Retries** | `3` |
| **Retry Delay** | `5 minutes` (300,000 ms) |
| **Retry On Error Types** | `EXTERNAL_INTEGRATION` (API failures), `TECHNICAL` (transient DB errors) |
| **No Retry On** | `DATA_QUALITY`, `FUNCTIONAL` (data must be corrected before reprocessing) |

---

## Pipeline YAML Configuration Reference

```yaml
# resources/pipelines/sales.yml
pipeline:
  id: "sales-daily"
  name: "Sales Daily Pipeline"
  version: "1.0.0"
  description: "Daily sales transaction ingestion from CSV or REST API"
  status: ACTIVE

  source-config:
    type: CSV
    connection-details:
      file-path: "/data/incoming/sales/sales_{date}.csv"
    delimiter: ","
    encoding: "UTF-8"
    has-headers: true

  target-config:
    type: DATABASE
    table-name: "sales_transactions"
    schema: "public"
    load-strategy: UPSERT
    business-key:
      - transaction_id
    chunk-size: 500
    rollback-strategy: DELETE_BY_EXECUTION

  validation-config:
    mandatory-columns:
      - transaction_id
      - customer_id
      - product_id
      - amount
      - currency
      - sale_date
      - salesperson_id
      - channel
    column-types:
      amount: DECIMAL
      sale_date: DATE
      product_quantity: INTEGER
      discount_rate: DECIMAL
    error-threshold-percent: 5.0
    allow-partial-load: true

  transformation-config:
    source-timezone: "UTC"
    base-currency: "USD"
    date-format: "yyyy-MM-dd"
    mappings:
      - field: channel
        on-missing: REJECT
        map:
          "1": "ONLINE"
          "2": "IN_STORE"
          "3": "PHONE"
          "4": "PARTNER"
    derived-columns:
      - name: subtotal
        type: ARITHMETIC
        formula: "amount * product_quantity * (1 - discount_rate / 100)"
      - name: tax_amount
        type: ARITHMETIC
        formula: "subtotal * 0.19"
      - name: total_amount
        type: ARITHMETIC
        formula: "subtotal + tax_amount"

  retry-policy:
    max-retries: 3
    retry-delay-ms: 300000
    retry-on-errors:
      - EXTERNAL_INTEGRATION
      - TECHNICAL

  schedule-config:
    cron: "0 0 2 * * *"
    timezone: "UTC"
    allowed-windows:
      - start: "01:30"
        end: "04:00"
```
