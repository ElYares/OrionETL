# Loading Rules

Loading rules govern how validated, transformed records are persisted to the target destination. These rules ensure data consistency, traceability, and the ability to recover from failures. All loading logic is implemented in the `infrastructure/loader/` package.

---

## Rule 1: Always Load to Staging Before Promoting to Final Tables

**Rule ID:** `LOAD-001`
**Enforced by:** `ETLOrchestrator`, `StagingLoader`, `FinalLoader`

Every pipeline must write all processed records to a staging table (`{tableName}_staging`) before any record reaches the final destination table. Direct insertion into final tables is **never permitted** during an automated ETL execution.

**Rationale:**
- If the load process fails midway, the staging table contains the partial dataset while the final table remains intact and uncontaminated.
- Staging allows a full validation pass on the persisted data (row count check, mandatory column check, business key uniqueness) before committing to production.
- Staging tables serve as a forensic artifact: they can be inspected after a failure to understand what was about to be loaded.

**Staging table convention:**
- Staging table name: `{target_table_name}_staging` (e.g., `sales_transactions_staging`).
- Staging table schema mirrors the final table schema.
- Staging tables are truncated at the start of each pipeline execution for this pipeline.
- Staging tables are retained until the next successful execution of the same pipeline (to allow post-mortem inspection after failure).

---

## Rule 2: Do Not Insert Duplicates If Business Key Already Exists (INSERT Strategy)

**Rule ID:** `LOAD-002`
**Enforced by:** `FinalLoader` (INSERT strategy)

When `targetConfig.loadStrategy = INSERT`, records whose business key already exists in the final table must be rejected — not silently ignored and not overwritten.

**Rationale:** The `INSERT` strategy is used when each batch is expected to contain exclusively new records. If a duplicate is encountered, it means either the source sent duplicate data (a data quality issue) or the pipeline is being re-run without properly handling idempotency (an operational issue). Both cases must be surfaced, not silently swallowed.

**Behavior:**
- Before promotion, check for business key conflicts between staging and final table.
- Any staging record whose business key exists in the final table is moved to the `etl_rejected_records` table with reason `DUPLICATE_KEY_INSERT`.
- Only non-conflicting records are promoted to the final table.
- The execution counts reflect: `totalLoaded` = records successfully inserted; `totalRejected` += duplicate records.

**Exception:** If `allowDuplicateInsert = true` is set (rare, explicit opt-in), duplicates are silently skipped with a WARNING log and not rejected.

---

## Rule 3: Use Upsert Per Natural Identifier Per Configuration

**Rule ID:** `LOAD-003`
**Enforced by:** `FinalLoader` (UPSERT strategy)

When `targetConfig.loadStrategy = UPSERT`, records are inserted if their business key does not exist, or their columns are updated if the business key already exists. The upsert key is defined by `targetConfig.businessKey`.

**Implementation:**

PostgreSQL `INSERT ... ON CONFLICT DO UPDATE`:

```sql
INSERT INTO sales_transactions (transaction_id, customer_id, amount, ...)
SELECT transaction_id, customer_id, amount, ...
FROM sales_transactions_staging
ON CONFLICT (transaction_id)
DO UPDATE SET
    customer_id = EXCLUDED.customer_id,
    amount = EXCLUDED.amount,
    updated_at = NOW()
WHERE sales_transactions.closed = FALSE;   -- see Rule 4
```

**Fields excluded from upsert update:**
- The business key itself.
- `created_at` (set only on insert).
- Any field marked as `immutable: true` in `targetConfig.columnConfig`.

**Behavior:** Every record in the staging table is processed. No record causes an error; the operation is fully idempotent.

---

## Rule 4: Never Overwrite Closed or Historical Records

**Rule ID:** `LOAD-004`
**Enforced by:** `FinalLoader`

Records in the final table that are marked as `closed`, `historical`, `archived`, or `finalized` must not be modified by an upsert operation, even if the pipeline receives updated data for the same business key.

**Rationale:** Closed records represent finalized business events (e.g., a settled transaction, a closed billing period, a historical inventory snapshot). Overwriting them corrupts the historical record.

**Implementation:**
- The upsert `DO UPDATE` clause includes a `WHERE` condition that excludes closed records (see example in Rule 3).
- If a staging record attempts to update a closed final record, the update is silently skipped and logged as INFO.
- The skipped record is **not** counted as a rejection — it is a valid no-op.

**Configuration:**

```yaml
target-config:
  closed-record-guard:
    enabled: true
    closed-flag-column: is_closed
    closed-flag-value: true
```

---

## Rule 5: All Rejections Must Be Persisted in `etl_rejected_records` With Full Detail

**Rule ID:** `LOAD-005`
**Enforced by:** `RejectedRecordPersister`

This rule reinforces Data Quality Rule DQ-011 at the loading layer. All records rejected at any point — schema validation, transformation, business validation, or during the staging load itself — must be written to `etl_rejected_records` before the main load begins.

**Timing:** `RejectedRecordPersister.persist(rejectedRecords, execution)` is the **first** operation in the `LOAD` step, before `StagingLoader` is called. This ensures rejected records are safe even if the main load fails catastrophically.

**What is persisted per rejected record:**

| Column | Content |
|---|---|
| `execution_id` | Reference to the active execution. |
| `pipeline_id` | Reference to the pipeline. |
| `step_name` | Step where the record was rejected (VALIDATE_SCHEMA, TRANSFORM, VALIDATE_BUSINESS). |
| `raw_data` | Complete JSONB snapshot of the original `RawRecord.data`. |
| `rejection_reason` | Human-readable summary (e.g., `"NULL mandatory field: customer_id"`). |
| `validation_errors` | Full JSONB array of `ValidationError` objects. |
| `rejected_at` | Timestamp of rejection. |
| `source_row_number` | Original row number from the source file. |

---

## Rule 6: Source File Traceability Must Be Saved Per Record

**Rule ID:** `LOAD-006`
**Enforced by:** `StagingLoader`, `FinalLoader`

Every record loaded to the final destination table must carry traceability metadata that allows the record to be traced back to the specific execution and source file that produced it.

**Traceability columns added to every target table:**

| Column | Value |
|---|---|
| `etl_execution_id` | UUID of the `PipelineExecution` that loaded this record. |
| `etl_pipeline_id` | UUID of the pipeline. |
| `etl_source_file` | Source file path, API URL, or database query reference. |
| `etl_load_timestamp` | Timestamp when the record was written to the final table. |
| `etl_pipeline_version` | Version of the pipeline config that produced this record. |

**Note:** These columns are added to the final table DDL in Flyway migrations. They are transparent to downstream consumers but invaluable for debugging and lineage tracing.

---

## Rule 7: Use Transactions Per Chunk

**Rule ID:** `LOAD-007`
**Enforced by:** `StagingLoader`

Records are loaded to the staging table in configurable chunks. Each chunk is wrapped in its own database transaction. This limits the size of any single transaction and reduces lock contention.

**Configuration:**

```yaml
target-config:
  chunk-size: 500   # records per transaction
```

**Transaction behavior:**
- Transaction begins: before inserting the first record of the chunk.
- Transaction commits: after successfully inserting the last record of the chunk.
- Transaction rolls back: if any record insertion in the chunk fails.

**Chunk failure handling:**
- On chunk failure: roll back the chunk transaction, record the failed chunk (start row, end row, error detail) in the `etl_execution_errors` table.
- `failFastOnChunkError: true` (default): abort the entire staging load, mark LOAD step FAILED.
- `failFastOnChunkError: false`: log the failed chunk, continue with remaining chunks, mark execution as PARTIAL.

**Promotion transaction:** The final promotion from staging to final table is a single transaction (not chunked) to ensure atomicity of the complete dataset switch.

---

## Rule 8: Failed Chunks Must Be Registered With Full Detail

**Rule ID:** `LOAD-008`
**Enforced by:** `StagingLoader`

When a chunk fails (see Rule 7), the failure must be fully documented in the execution error log.

**What is recorded per failed chunk:**

| Field | Content |
|---|---|
| `error_type` | `TECHNICAL` (DB error) or `DATA_QUALITY` (constraint violation). |
| `error_code` | Specific error code (e.g., `CHUNK_LOAD_FAILURE`). |
| `message` | Human-readable description including chunk number, start/end row. |
| `stack_trace` | Full JDBC/JPA exception stack trace. |
| `record_reference` | `"chunk:{chunkNumber}, rows:{startRow}-{endRow}"` |

**Why this level of detail:**
- Operators can identify exactly which rows caused the chunk failure.
- The failed rows can be extracted from `etl_rejected_records` or the staging table for manual review.
- Pattern analysis of multiple chunk failures can identify a systematic data issue.

---

## Rule 9: Staging Validation Must Pass Before Promotion to Final Table

**Rule ID:** `LOAD-009`
**Enforced by:** `ETLOrchestrator`, `StagingValidator`

This rule reinforces Execution Rule EXEC-010. The promotion from staging to final must be gated by a passing `StagingValidator` result.

**What `StagingValidator` checks:**

1. **Row count:** `COUNT(*) FROM {table}_staging WHERE execution_id = {current_execution_id}` must equal the expected `processedRecords.size()`.
2. **No null mandatory columns:** Checks that no row in staging has a null value in any column marked mandatory in the target table schema.
3. **Business key uniqueness in staging:** For UPSERT strategy, confirms no duplicate business keys within the staging dataset (duplicates within staging would produce non-deterministic upsert behavior).
4. **No orphaned FK references (optional):** If configured, checks that all FK columns in staging reference existing records in their parent tables.

**Behavior on validation failure:**
- Mark LOAD step `FAILED`.
- Do not call `FinalLoader.promote()`.
- Preserve the staging table contents for manual inspection.
- Set execution status to `FAILED`.
- Record the staging validation failure as an `ExecutionError` with type `DATA_QUALITY`.

---

## Rule 10: Rollback Strategy Per Pipeline on Critical Failure

**Rule ID:** `LOAD-010`
**Enforced by:** `FinalLoader`, `ETLOrchestrator`

Each pipeline defines a rollback strategy that governs what happens to already-loaded data when a critical failure occurs during or after the promotion step.

**Available rollback strategies:**

| Strategy | Behavior |
|---|---|
| `NONE` | No rollback. Already loaded records remain in the final table. Used for append-only pipelines where partial loads are acceptable. |
| `DELETE_BY_EXECUTION` | DELETE all records from the final table WHERE `etl_execution_id = {current_execution_id}`. Reverses the promotion exactly. |
| `RESTORE_FROM_STAGING` | (Advanced) Restore the pre-execution state from a pre-load snapshot. Used for high-criticality pipelines. |
| `MANUAL` | No automatic rollback. Alert is emitted to operators for manual intervention. |

**Configuration:**

```yaml
target-config:
  rollback-strategy: DELETE_BY_EXECUTION
```

**When rollback is triggered:**
- A CRITICAL exception occurs during or immediately after the promotion transaction.
- The `CLOSE` step determines the execution as `FAILED` after promotion was at least partially attempted.
- Rollback is executed within the `CLOSE` step, before final status is recorded.

**Note:** The `DELETE_BY_EXECUTION` strategy relies on the `etl_execution_id` traceability column (Rule 6) being present on the final table.
