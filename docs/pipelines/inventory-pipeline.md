# Inventory Pipeline

## Overview

| Property | Value |
|---|---|
| **Pipeline ID** | `inventory-sync` |
| **Version** | `1.0.0` |
| **Purpose** | Synchronize inventory levels from warehouse management systems to the central inventory database |
| **Status** | Active |
| **Owner** | Supply Chain Operations Team |

The Inventory pipeline ingests warehouse inventory snapshots from Excel or CSV exports provided by the Warehouse Management System (WMS). It normalizes SKU codes, consolidates quantities by SKU and warehouse, validates against catalog references, and upserts the results into the `inventory_levels` PostgreSQL table.

This pipeline runs four times daily to keep inventory data reasonably current without overloading the WMS export mechanism. The relatively strict 2% error threshold reflects the criticality of accurate inventory data for order fulfillment decisions.

---

## Schedule

| Property | Value |
|---|---|
| **Schedule** | Every 6 hours: 00:00, 06:00, 12:00, 18:00 UTC |
| **Timezone** | UTC |
| **Allowed Window** | `:00` – `:30` of each scheduled hour |
| **Trigger Type** | SCHEDULED (can also be triggered manually for ad-hoc syncs) |
| **Days** | Every day including weekends |

The 6-hour cadence is chosen to balance data freshness against system load. The WMS export is a batch process that takes approximately 10 minutes to generate, so the pipeline is triggered at the top of the hour.

---

## Source Configuration

### Option A: Excel File (Primary)

| Property | Value |
|---|---|
| **Type** | `EXCEL` |
| **File Location** | `/data/incoming/inventory/inventory_snapshot_{yyyy-MM-dd_HH}.xlsx` |
| **Sheet** | `"Inventory"` (by name) or index `0` as fallback |
| **Has Headers** | `true` (row 1) |
| **Data Start Row** | Row 2 |
| **File Formats Supported** | `.xlsx` (primary), `.xls` (legacy fallback) |

### Option B: CSV (Fallback)

| Property | Value |
|---|---|
| **Type** | `CSV` |
| **File Location** | `/data/incoming/inventory/inventory_snapshot_{yyyy-MM-dd_HH}.csv` |
| **Delimiter** | `;` (semicolon — WMS default export format) |
| **Encoding** | `ISO-8859-1` (WMS legacy encoding) |
| **Has Headers** | `true` |

The CSV fallback is used when the WMS cannot generate Excel format (e.g., during system upgrades). The `encoding: ISO-8859-1` is a known limitation of the WMS export module.

---

## Input Schema

| Field | Type | Mandatory | Description |
|---|---|---|---|
| `sku` | STRING | Yes | Stock Keeping Unit code. Source may have varied formatting (mixed case, extra spaces). |
| `warehouse_id` | STRING | Yes | Warehouse identifier from the WMS (may be numeric string or alpha code). |
| `quantity_on_hand` | INTEGER | Yes | Physical quantity currently in the warehouse location. |
| `quantity_reserved` | INTEGER | No | Quantity reserved for pending orders. Defaults to 0 if absent. |
| `unit_cost` | DECIMAL | No | Current unit cost in source currency. |
| `last_updated` | DATETIME | Yes | Timestamp of the last inventory movement for this SKU in the WMS. Format: `yyyy-MM-dd HH:mm:ss` |
| `warehouse_name` | STRING | No | Human-readable warehouse name (informational only, not loaded to destination). |
| `product_description` | STRING | No | Product description from WMS (not loaded to destination — available in products catalog). |

---

## Transformations

### CommonTransformer

1. **Trim all string fields** (Rule TR-004).
2. **Normalize column names to snake_case** (Rule TR-001).
3. **Parse `last_updated`** as `DATETIME`, convert to UTC (Rule TR-003). Source timezone is configured per WMS region.
4. **Default `quantity_reserved`** to `0` if absent or null.
5. **Default `unit_cost`** to `null` if absent (cost is optional for inventory sync).

### InventoryTransformer (inventory-specific)

6. **Normalize SKU format:**
   - Uppercase: `"abc-123"` → `"ABC-123"`.
   - Trim internal spaces and replace with hyphens: `"ABC 123"` → `"ABC-123"`.
   - Remove leading zeros from numeric SKU segments: `"ABC-00123"` → `"ABC-123"` (configurable).
   - Pad to minimum length if configured: `"ABC-1"` → `"ABC-001"` (useful for sort order).

7. **Map warehouse codes to internal IDs** (Rule TR-006):
   - WMS warehouse codes (e.g., `"WH001"`, `"CENTRAL"`, `"3"`) are translated to internal warehouse UUIDs via the `warehouses` catalog table.
   - If `onMissingMapping: REJECT`, records with unknown warehouse codes are rejected.

8. **Consolidate quantities per SKU + warehouse_id** (see special logic below):
   - If the same `(sku, warehouse_id)` pair appears multiple times in the same batch (e.g., different WMS locations within the same warehouse), consolidate by summing `quantity_on_hand` and `quantity_reserved`.
   - `unit_cost` in consolidated records: use the **most recent** `last_updated` value's unit cost.
   - `last_updated` in consolidated records: use the **maximum** (most recent) value.

9. **Convert `unit_cost` currency** to base currency (USD) if `unit_cost` and a `cost_currency` field are present (Rule TR-002).

---

## Duplicate / Consolidation Logic

The inventory pipeline has specific logic for handling multiple rows with the same `(sku, warehouse_id)` combination, which is a normal occurrence in WMS exports (multiple storage locations within one warehouse).

| Configuration | Behavior |
|---|---|
| `duplicateHandling: CONSOLIDATE` | Sum quantities, use latest cost and timestamp. This is the **default** for inventory. |
| `duplicateHandling: REJECT_SUBSEQUENT` | Keep first occurrence, reject others. Used for strict SKU uniqueness scenarios. |
| `duplicateHandling: REJECT_ALL` | Reject all records with duplicate `(sku, warehouse_id)`. Forces data cleaning at source. |

---

## Business Rules

| Rule | Field(s) | Condition | Action |
|---|---|---|---|
| `BR-INV-001` | `sku` | Must not be null or empty after normalization | Reject record (Rule DQ-002) |
| `BR-INV-002` | `quantity_on_hand` | Must be >= 0 (negative physical quantity is not meaningful) | Reject record (Rule DQ-004) |
| `BR-INV-003` | `quantity_reserved` | Must be >= 0 | Reject record |
| `BR-INV-004` | `quantity_reserved` | Must be <= `quantity_on_hand` (cannot reserve more than on hand) | Reject record with CRITICAL |
| `BR-INV-005` | `warehouse_id` | Must exist in the `warehouses` catalog and must be `ACTIVE` | Reject record (Rule DQ-007) |
| `BR-INV-006` | `sku` | Must exist in the `products` catalog | Reject record (Rule DQ-007) |
| `BR-INV-007` | `unit_cost` | If present, must be > 0 (zero cost allowed only if `allowZeroCost = true`) | Reject record |
| `BR-INV-008` | `last_updated` | Must not be a future timestamp (more than 5 minutes ahead of processing time) | Reject record |
| `BR-INV-009` | `(sku, warehouse_id)` | Post-consolidation: each combination must be unique in the batch | Consolidate or reject per config |
| `BR-INV-010` | `quantity_on_hand` | If `quantity_on_hand = 0` AND `quantity_reserved > 0`: data inconsistency | Reject record with CRITICAL |

---

## Validation Configuration

| Property | Value |
|---|---|
| **Error Threshold** | `2.0%` — abort if more than 2% of records are invalid |
| **Allow Partial Load** | `false` — do NOT load partial inventory data (all-or-nothing) |
| **Mandatory Columns** | `sku`, `warehouse_id`, `quantity_on_hand`, `last_updated` |

The `allowPartialLoad: false` setting is intentional. Partial inventory data is worse than stale inventory data, because downstream systems (order management, replenishment) may make incorrect decisions based on incomplete inventory snapshots.

---

## Target Configuration

| Property | Value |
|---|---|
| **Type** | `DATABASE` |
| **Table** | `inventory_levels` |
| **Schema** | `public` |
| **Staging Table** | `inventory_levels_staging` |
| **Load Strategy** | `UPSERT` |
| **Business Key** | `sku + warehouse_id` (composite) |
| **Chunk Size** | `1000` records per transaction |
| **Rollback Strategy** | `DELETE_BY_EXECUTION` |

### Target Table Column Mapping

| Source Field | Target Column | Notes |
|---|---|---|
| `sku` | `sku` | Normalized (uppercase, trimmed) — part of composite PK |
| `warehouse_id` | `warehouse_id` | Internal UUID from catalog — part of composite PK |
| `quantity_on_hand` | `quantity_on_hand` | Consolidated if duplicates |
| `quantity_reserved` | `quantity_reserved` | Defaulted to 0 |
| `unit_cost` | `unit_cost` | Optional |
| `last_updated` | `last_updated` | UTC timestamp |
| _(injected)_ | `etl_execution_id` | Traceability |
| _(injected)_ | `etl_load_timestamp` | Traceability |
| _(injected)_ | `etl_source_file` | File name traceability |

---

## Retry Policy

| Property | Value |
|---|---|
| **Max Retries** | `2` |
| **Retry Delay** | `10 minutes` (600,000 ms) |
| **Retry On Error Types** | `TECHNICAL` (file read error, DB timeout), `EXTERNAL_INTEGRATION` (catalog lookup failure) |
| **No Retry On** | `DATA_QUALITY`, `FUNCTIONAL` |

The lower retry count (2 vs. 3 for Sales) reflects the 6-hour schedule: if this run fails, the next run in 6 hours will pick up a fresh WMS export. Extended retrying is therefore less critical.

---

## Pipeline YAML Configuration Reference

```yaml
# resources/pipelines/inventory.yml
pipeline:
  id: "inventory-sync"
  name: "Inventory Sync Pipeline"
  version: "1.0.0"
  description: "Sync inventory levels from WMS Excel/CSV exports every 6 hours"
  status: ACTIVE

  source-config:
    type: EXCEL
    connection-details:
      file-path: "/data/incoming/inventory/inventory_snapshot_{datetime}.xlsx"
      sheet-name: "Inventory"
      data-start-row: 2
    has-headers: true

  target-config:
    type: DATABASE
    table-name: "inventory_levels"
    schema: "public"
    load-strategy: UPSERT
    business-key:
      - sku
      - warehouse_id
    chunk-size: 1000
    rollback-strategy: DELETE_BY_EXECUTION

  validation-config:
    mandatory-columns:
      - sku
      - warehouse_id
      - quantity_on_hand
      - last_updated
    column-types:
      quantity_on_hand: INTEGER
      quantity_reserved: INTEGER
      unit_cost: DECIMAL
      last_updated: DATETIME
    error-threshold-percent: 2.0
    allow-partial-load: false

  transformation-config:
    source-timezone: "America/Bogota"
    date-format: "yyyy-MM-dd HH:mm:ss"
    sku-normalization:
      uppercase: true
      trim-spaces: true
      replace-spaces-with-hyphens: true
      remove-leading-zeros: true
    duplicate-handling: CONSOLIDATE

  retry-policy:
    max-retries: 2
    retry-delay-ms: 600000
    retry-on-errors:
      - TECHNICAL
      - EXTERNAL_INTEGRATION

  schedule-config:
    cron: "0 0 0/6 * * *"
    timezone: "UTC"
```
