# Customer Pipeline

## Overview

| Property | Value |
|---|---|
| **Pipeline ID** | `customer-sync` |
| **Version** | `1.0.0` |
| **Purpose** | Synchronize customer master data from the CRM system to the central customer database |
| **Status** | Active |
| **Owner** | Customer Data Team |

The Customer pipeline fetches customer records from the CRM REST API (JSON format), normalizes the master data to corporate standards, validates data quality with strict rules, and upserts the results into the `customers` PostgreSQL table.

Customer data quality is critical because it drives customer communications, fraud detection, KYC compliance, and reporting. The 1% error threshold reflects this criticality: any significant data quality issue at the source should block the load rather than propagate corrupt data downstream.

---

## Schedule

| Property | Value |
|---|---|
| **Schedule** | Daily at **01:00 UTC** |
| **Timezone** | UTC |
| **Allowed Window** | 00:30 UTC – 03:00 UTC |
| **Trigger Type** | SCHEDULED (can also be triggered manually for CRM synchronization events) |
| **Days** | Monday through Sunday (daily) |

The pipeline runs at 01:00 UTC — before the Sales pipeline (02:00 UTC) — to ensure customer records are up-to-date before sales transactions are validated against the customers table.

---

## Source Configuration

| Property | Value |
|---|---|
| **Type** | `API` |
| **Method** | `GET` |
| **URL** | `https://crm.internal/api/v3/customers` |
| **Auth** | Bearer token (environment variable: `CRM_API_TOKEN`) |
| **Content-Type** | `application/json` |
| **Response Structure** | JSON object with `customers` array and `meta.next_cursor` for pagination |
| **Pagination Type** | Cursor-based |
| **Page Size** | `200` customers per request |
| **Filter** | `?updated_since={last_successful_run_timestamp}` (incremental sync) |
| **Full Sync Mode** | Triggered manually or when `forceFullSync = true` parameter is passed |
| **Timeout** | 30 seconds per request |
| **Retry on 5xx** | Yes (up to 5 attempts before classifying as `EXTERNAL_INTEGRATION` failure) |

### Incremental vs. Full Sync

The pipeline supports two modes:

- **Incremental (default):** Fetches only customers created or updated since the last successful execution's `finishedAt` timestamp. Stored in `etl_pipeline_executions` for reference.
- **Full sync:** Fetches all active customers from the CRM. Triggered by passing `mode=FULL` parameter in the execution request, or when no previous successful execution exists (first run).

---

## Input Schema

| Field | Type | Mandatory | Description |
|---|---|---|---|
| `customer_id` | STRING | Yes | CRM-internal customer identifier. Used as source reference (not the destination business key). |
| `document_type` | STRING | Yes | Legal document type code: `"CC"`, `"CE"`, `"NIT"`, `"PASS"`, `"PPN"`. |
| `document_number` | STRING | Yes | Legal document number. Used with `document_type` as the business key. |
| `first_name` | STRING | Yes | Customer's given name. |
| `last_name` | STRING | Yes | Customer's family name. |
| `email` | STRING | Yes | Primary email address. |
| `phone` | STRING | No | Primary phone number (any format). |
| `country_code` | STRING | Yes | Two-letter or three-letter country code from CRM. |
| `registration_date` | DATE | Yes | Date the customer registered. Format: ISO 8601 (`yyyy-MM-dd`). |
| `status` | STRING | Yes | CRM status code: `"A"` (Active), `"I"` (Inactive), `"S"` (Suspended), `"C"` (Closed). |
| `preferred_language` | STRING | No | BCP 47 language tag (e.g., `"es-CO"`, `"en-US"`). |
| `birth_date` | DATE | No | Date of birth. Format: `yyyy-MM-dd`. |
| `gender` | STRING | No | CRM gender code. |
| `customer_type` | STRING | No | `"INDIVIDUAL"` or `"BUSINESS"`. Defaults to `"INDIVIDUAL"` if absent. |

---

## Transformations

### CommonTransformer

1. **Trim all string fields** (Rule TR-004).
2. **Normalize column names to snake_case** (Rule TR-001).
3. **Parse `registration_date`** as `DATE`, convert to UTC (Rule TR-003).
4. **Parse `birth_date`** as `DATE` if present (optional field).
5. **Map `status` codes** to internal catalog values (Rule TR-005):

   | CRM Code | Internal Status |
   |---|---|
   | `"A"` | `"ACTIVE"` |
   | `"I"` | `"INACTIVE"` |
   | `"S"` | `"SUSPENDED"` |
   | `"C"` | `"CLOSED"` |

6. **Default `customer_type`** to `"INDIVIDUAL"` if absent.

### CustomerTransformer (customer-specific)

7. **Normalize full name** (applied to both `first_name` and `last_name`):
   - Trim.
   - Title case: `"JUAN PABLO"` → `"Juan Pablo"`, `"maría elena"` → `"María Elena"`.
   - Collapse consecutive spaces: `"Juan  Pablo"` → `"Juan Pablo"`.
   - Remove non-alphabetic characters from name fields (except hyphens and apostrophes — valid in names): `"O'Brien"` preserved, `"John123"` → `"John"` with WARNING.

8. **Standardize email address:**
   - Lowercase: `"JOHN@EXAMPLE.COM"` → `"john@example.com"`.
   - Trim.
   - Validate RFC 5322 format (see Business Rules).
   - Normalize common aliases (configurable): `"john+test@example.com"` kept as-is (plus addressing is valid).

9. **Normalize phone to E.164 format:**
   - Remove all non-numeric characters except leading `+`.
   - Apply country code prefix if not present, using `country_code` field for default country dial code.
   - Examples: `"3001234567"` (Colombia) → `"+573001234567"`, `"(555) 123-4567"` (US) → `"+15551234567"`.
   - If phone cannot be normalized to E.164: set to `null` with WARNING (phone is not mandatory).

10. **Map `country_code` to ISO 3166-1 alpha-2 standard:**
    - CRM may provide alpha-2 (`"CO"`), alpha-3 (`"COL"`), or numeric (`"170"`) codes.
    - All are normalized to alpha-2 (e.g., `"COL"` → `"CO"`, `"170"` → `"CO"`).
    - If country code is unrecognized: reject record (country is mandatory).

11. **Map `document_type`** to internal canonical values if CRM codes differ from internal values (Rule TR-005).

---

## Business Rules

| Rule | Field(s) | Condition | Action |
|---|---|---|---|
| `BR-CUST-001` | `email` | Must match RFC 5322 email format after normalization | Reject record |
| `BR-CUST-002` | `document_number` | Must be unique across all active customers with the same `document_type` in the destination table | Reject duplicates (UPSERT resolves this for updates; new records conflict triggers CRITICAL) |
| `BR-CUST-003` | `document_type + document_number` | Must be unique within the current batch | Reject duplicates (Rule DQ-005) |
| `BR-CUST-004` | `registration_date` | Must be a valid date and must not be a future date | Reject record |
| `BR-CUST-005` | `status` | Active customer (`status = "ACTIVE"`) cannot have a duplicate `document_number` as another active customer | Reject record with CRITICAL |
| `BR-CUST-006` | `birth_date` | If present, must represent an age >= 0 and <= 120 years | Reject record |
| `BR-CUST-007` | `country_code` | Must resolve to a valid ISO 3166-1 alpha-2 code | Reject record |
| `BR-CUST-008` | `email` | Must not be a disposable/temporary email domain (if `blockedDomains` list is configured) | Reject record with WARNING or CRITICAL per config |
| `BR-CUST-009` | `document_number` | Length and format must match the rules for the given `document_type` (e.g., CC is numeric, PASS has specific format) | Reject record with CRITICAL |
| `BR-CUST-010` | `registration_date` | Must not be more than 100 years in the past | Reject record with WARNING |

---

## Validation Configuration

| Property | Value |
|---|---|
| **Error Threshold** | `1.0%` — abort if more than 1% of records are invalid |
| **Allow Partial Load** | `true` — load valid customers even if some are rejected |
| **Mandatory Columns** | `customer_id`, `document_type`, `document_number`, `first_name`, `last_name`, `email`, `country_code`, `registration_date`, `status` |

The 1% error threshold is strict because customer master data drives KYC compliance and customer communications. A 1% error rate in a dataset of 10,000 customers means up to 100 incorrectly represented customers — which is actionable.

---

## Target Configuration

| Property | Value |
|---|---|
| **Type** | `DATABASE` |
| **Table** | `customers` |
| **Schema** | `public` |
| **Staging Table** | `customers_staging` |
| **Load Strategy** | `UPSERT` |
| **Business Key** | `document_type + document_number` (composite) |
| **Chunk Size** | `200` records per transaction |
| **Rollback Strategy** | `DELETE_BY_EXECUTION` |
| **Closed Record Guard** | Enabled — do not overwrite customers with `status = "CLOSED"` (Rule LOAD-004) |

### Target Table Column Mapping

| Source Field | Target Column | Notes |
|---|---|---|
| `customer_id` | `crm_customer_id` | External reference (not the business key) |
| `document_type` | `document_type` | Business key (part 1) |
| `document_number` | `document_number` | Business key (part 2) |
| `first_name` | `first_name` | Title-cased |
| `last_name` | `last_name` | Title-cased |
| `email` | `email` | Lowercased and validated |
| `phone` | `phone` | E.164 format or null |
| `country_code` | `country_code` | ISO 3166-1 alpha-2 |
| `registration_date` | `registration_date` | UTC date |
| `status` | `status` | Internal status (`ACTIVE`, `INACTIVE`, `SUSPENDED`, `CLOSED`) |
| `preferred_language` | `preferred_language` | BCP 47 tag |
| `birth_date` | `birth_date` | Optional UTC date |
| `gender` | `gender` | Mapped to internal code |
| `customer_type` | `customer_type` | `INDIVIDUAL` or `BUSINESS` |
| _(injected)_ | `etl_execution_id` | Traceability |
| _(injected)_ | `etl_load_timestamp` | Traceability |
| _(injected)_ | `etl_source_file` | CRM API URL |

---

## Retry Policy

| Property | Value |
|---|---|
| **Max Retries** | `5` |
| **Retry Delay** | `2 minutes` (120,000 ms) |
| **Retry On Error Types** | `EXTERNAL_INTEGRATION` (CRM API failures), `TECHNICAL` (transient DB errors) |
| **No Retry On** | `DATA_QUALITY`, `FUNCTIONAL` |

The higher retry count (5) is because the CRM API is an external dependency with known transient availability issues. Aggressive retry with a short 2-minute delay gives the best chance of completing before the daily window closes.

---

## Special Considerations

### Incremental Sync State

The pipeline tracks the timestamp of the last successful run to use as the `updated_since` filter parameter. This timestamp is stored in the `etl_pipeline_executions` table and retrieved at the start of each run. If no previous successful run exists, the pipeline defaults to `full sync` mode.

### Customer Deduplication

The CRM occasionally sends the same customer multiple times in a single API response (e.g., customers who appear in multiple segments). The pipeline handles this as:

1. First occurrence in the batch: processed normally.
2. Subsequent occurrences of the same `document_type + document_number`: rejected with `NEAR_DUPLICATE` warning (Rule DQ-006).
3. The UPSERT strategy ensures that even if the same customer arrives in multiple consecutive daily runs, they are upserted cleanly.

### Closed Customer Protection

Customers with `status = "CLOSED"` in the destination table are protected by the closed record guard (Rule LOAD-004). If the CRM sends an update for a closed customer, the update is silently skipped and logged as INFO. This prevents re-activating customers who have been formally closed.

---

## Pipeline YAML Configuration Reference

```yaml
# resources/pipelines/customer.yml (referenced in config, not in resources/pipelines/)
pipeline:
  id: "customer-sync"
  name: "Customer Sync Pipeline"
  version: "1.0.0"
  description: "Daily customer master data sync from CRM REST API"
  status: ACTIVE

  source-config:
    type: API
    connection-details:
      url: "https://crm.internal/api/v3/customers"
      method: GET
      response-array-path: "customers"
      pagination-type: CURSOR
      cursor-field: "meta.next_cursor"
      page-size: 200
      filter-param: "updated_since"
    auth-config:
      type: BEARER
      token-env: "CRM_API_TOKEN"
    timeout-ms: 30000

  target-config:
    type: DATABASE
    table-name: "customers"
    schema: "public"
    load-strategy: UPSERT
    business-key:
      - document_type
      - document_number
    chunk-size: 200
    rollback-strategy: DELETE_BY_EXECUTION
    closed-record-guard:
      enabled: true
      closed-flag-column: status
      closed-flag-value: "CLOSED"

  validation-config:
    mandatory-columns:
      - customer_id
      - document_type
      - document_number
      - first_name
      - last_name
      - email
      - country_code
      - registration_date
      - status
    column-types:
      registration_date: DATE
      birth_date: DATE
    error-threshold-percent: 1.0
    allow-partial-load: true

  transformation-config:
    source-timezone: "UTC"
    date-format: "yyyy-MM-dd"
    name-handling:
      mode: SEPARATE
      apply-title-case: true
    phone-normalization:
      format: E164
      default-country-from-field: country_code
    mappings:
      - field: status
        on-missing: REJECT
        map:
          "A": "ACTIVE"
          "I": "INACTIVE"
          "S": "SUSPENDED"
          "C": "CLOSED"

  retry-policy:
    max-retries: 5
    retry-delay-ms: 120000
    retry-on-errors:
      - EXTERNAL_INTEGRATION
      - TECHNICAL

  schedule-config:
    cron: "0 0 1 * * *"
    timezone: "UTC"
    allowed-windows:
      - start: "00:30"
        end: "03:00"
```
