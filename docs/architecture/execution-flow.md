# ETL Execution Flow

This document describes the complete, detailed execution flow for every pipeline run in OrionETL. Every execution follows the same 8-step sequence, driven by `ETLOrchestrator`. Understanding this flow is essential for debugging, extending, and operating the system.

---

## Flow Overview

```
Trigger (API / CLI / Scheduler)
         │
         ▼
  [1] INIT
  Load config, validate params, create PipelineExecution (RUNNING)
         │
         ▼
  [2] EXTRACT
  Call DataExtractor → List<RawRecord>
         │
    failure? ──YES──► ABORT → CLOSE → AUDIT
         │ NO
         ▼
  [3] VALIDATE_SCHEMA
  SchemaValidator → ValidationResult
         │
  critical errors > 0? ──YES──► ABORT → CLOSE → AUDIT
         │ NO
         ▼
  [4] TRANSFORM
  DataTransformer chain → List<ProcessedRecord> + List<RejectedRecord>
         │
         ▼
  [5] VALIDATE_BUSINESS
  BusinessValidator + QualityValidator → ValidationResult + DataQualityReport
         │
  errorRate > threshold? ──YES──► ABORT → CLOSE → AUDIT
         │ NO
         ▼
  [6] LOAD
  StagingLoader → StagingValidator → FinalLoader
         │
  staging invalid? ──YES──► ABORT → CLOSE → AUDIT
         │ NO
         ▼
  [7] CLOSE
  Record metrics, determine final status (SUCCESS / PARTIAL / FAILED)
         │
         ▼
  [8] AUDIT
  Persist AuditRecord (ALWAYS runs, even on failure)
```

---

## Step 1: INIT

**Purpose:** Resolve the pipeline definition, validate the execution context, and initialize the execution record.

**Input:**
- `pipelineId` or `pipelineName` (from request/trigger)
- Optional `parameters` map (e.g., override source file path, date range)
- `triggeredBy` (actor identifier)
- `triggerType` (`MANUAL`, `SCHEDULED`, `RETRY`, `CLI`)

**Processing:**

1. Look up the `Pipeline` definition via `PipelineRepository.findById(pipelineId)`.
2. Assert that the pipeline exists and has status `ACTIVE`. If not, throw `PipelineNotFoundException`.
3. Evaluate `NoDuplicateExecutionRule`: if another execution of this pipeline is currently `RUNNING` or `RETRYING`, throw `ExecutionConflictException`.
4. Evaluate `AllowedExecutionWindowRule`: if the pipeline has configured time windows and the current time is outside them, create a `SKIPPED` execution record and exit.
5. Create a new `PipelineExecution` with status `PENDING` via `ExecutionLifecycleService.createExecution()`.
6. Transition status to `RUNNING`.
7. Create the `PipelineExecutionStep` record for `INIT` with status `RUNNING`.
8. Set MDC context: `executionId`, `pipelineId`, `stepName=INIT`.

**Output:**
- Active `PipelineExecution` with status `RUNNING`
- `INIT` step record with status `SUCCESS`

**Error Handling:**
- `PipelineNotFoundException` → log WARN, return 404 to caller, no execution created.
- `ExecutionConflictException` → log WARN, return 409 to caller, no execution created.
- Any other exception during INIT → set execution to `FAILED`, record error, proceed to CLOSE/AUDIT.

---

## Step 2: EXTRACT

**Purpose:** Read all records from the configured source into memory as `RawRecord` objects.

**Input:**
- `Pipeline.sourceConfig` (type, connection details, format, auth)
- Active `PipelineExecution`

**Processing:**

1. Resolve the correct `DataExtractor` implementation based on `SourceConfig.type` using the extractor registry.
2. Call `extractor.extract(sourceConfig, execution)` → returns `ExtractionResult`.
3. Record `totalRead = ExtractionResult.totalRecords` on the `PipelineExecution`.
4. If configured, create a source snapshot (store a hash or copy of the source file for traceability).
5. Record the `EXTRACT` step as `SUCCESS` with `recordsProcessed = totalRead`.

**Output:**
- `List<RawRecord>` — one entry per source row/object.
- Updated `PipelineExecution.totalRead`.

**Error Handling:**
- **IO failure** (file not found, read error): classify as `ErrorType.TECHNICAL`. Set EXTRACT step to `FAILED`. Mark execution as `FAILED`. Skip to CLOSE.
- **Auth failure** (API 401/403): classify as `ErrorType.EXTERNAL_INTEGRATION`. Same abort flow.
- **Empty source** (zero records): this is a valid scenario. Log INFO, set totalRead=0, continue to CLOSE as `SUCCESS` with no records processed.
- **Partial read** (e.g., corrupted mid-file): attempt to process available records, classify remaining rows as `DATA_QUALITY` errors.

---

## Step 3: VALIDATE_SCHEMA

**Purpose:** Verify that the extracted records conform to the expected schema: correct columns, correct types, no missing mandatory fields.

**Input:**
- `List<RawRecord>` from EXTRACT step
- `Pipeline.validationConfig` (mandatory columns, column types, error threshold)
- Active `PipelineExecution`

**Processing:**

1. Call `SchemaValidator.validate(rawRecords, validationConfig, execution)`.
2. For each record, check:
   - All `mandatoryColumns` are present and non-null.
   - Each column's value is convertible to the expected type (STRING, INTEGER, DECIMAL, DATE, BOOLEAN).
   - No unexpected column names if strict mode is enabled.
3. Collect all `ValidationError`s per record and per field.
4. Records with `CRITICAL` schema errors are moved to `rejectedRecords` list.
5. Calculate schema error rate: `criticalSchemaErrors / totalRead`.
6. Check `NoDuplicateExecutionRule` at batch level: if duplicate row numbers are detected, flag as DATA_QUALITY errors.

**Output:**
- `ValidationResult` — isValid, errorList, warningList, errorRate.
- Records split into: `validRecords` (passed schema) and `schemaRejectedRecords`.

**Abort Condition:**
If critical schema errors exist AND the error rate exceeds the `errorThresholdPercent`, or if no records pass schema validation, abort the execution.

**Error Handling:**
- Schema errors are classified as `ErrorType.DATA_QUALITY`.
- Each rejected record is added to the `rejectedRecords` accumulator (to be persisted in LOAD step or CLOSE).
- If abort condition is met: set VALIDATE_SCHEMA step to `FAILED`, mark execution `FAILED`, skip to CLOSE.
- Warnings (non-critical schema issues) do not block execution.

---

## Step 4: TRANSFORM

**Purpose:** Apply all configured transformations to produce clean, normalized `ProcessedRecord`s ready for business validation and loading.

**Input:**
- `List<RawRecord>` that passed schema validation
- `Pipeline.transformationConfig`
- Active `PipelineExecution`

**Processing:**

1. Resolve the transformer chain for this pipeline. The chain always includes `CommonTransformer` first, followed by pipeline-specific transformers (e.g., `SalesTransformer`).
2. For each transformer in the chain:
   - Call `transformer.transform(records, transformationConfig, execution)`.
   - The output `List<ProcessedRecord>` of each transformer is the input to the next.
   - Records that fail transformation (e.g., unparseable date, unmappable code) are extracted from the list and added to `rejectedRecords` with the transformation error.
3. Common transformations include:
   - Trim all string fields.
   - Normalize column names to `snake_case`.
   - Parse and convert dates to UTC `Instant`.
   - Convert monetary amounts to base currency (configured).
   - Map external status codes to internal catalog values.
   - Split composite fields per schema definition.
   - Calculate derived columns (formulas defined in `transformationConfig`).
4. Update `execution.totalTransformed` with the count of successfully transformed records.
5. Record `TRANSFORM` step as `SUCCESS`.

**Output:**
- `List<ProcessedRecord>` — transformed, normalized records.
- Updated `rejectedRecords` accumulator with transformation failures.

**Error Handling:**
- A record that cannot be transformed is NOT treated as a fatal error. It is rejected, added to `rejectedRecords`, and processing continues with the remaining records.
- Transformation failures are classified as `ErrorType.FUNCTIONAL` (business mapping failure) or `ErrorType.DATA_QUALITY` (type conversion failure).
- If the entire transform step throws an unexpected exception (e.g., OutOfMemoryError, NullPointerException in transformer), classify as `ErrorType.TECHNICAL`, mark step `FAILED`, abort.

---

## Step 5: VALIDATE_BUSINESS

**Purpose:** Apply business-level validation rules to transformed records to ensure they are semantically correct and consistent with the business domain.

**Input:**
- `List<ProcessedRecord>` from TRANSFORM step
- `Pipeline.validationConfig.businessRules`
- Active `PipelineExecution`

**Processing:**

1. Call `BusinessValidator.validate(processedRecords, validationConfig, execution)`.
2. Business rules are applied in configured order. Examples:
   - `CATALOG_LOOKUP`: verify that `product_id` exists in the `products` catalog table.
   - `UNIQUENESS_CHECK`: verify no duplicate `transaction_id` values within the batch.
   - `RANGE_CHECK`: verify `amount >= 0` (or as configured).
   - `CROSS_FIELD_CHECK`: verify `sale_date <= today` (or configured date boundary).
   - `ACTIVE_REFERENCE_CHECK`: verify `salesperson_id` is an active salesperson.
3. Records that fail one or more business rules are added to `rejectedRecords` with the rule name and context.
4. After business validation, call `QualityValidator` to calculate the cumulative error rate.
5. `QualityValidator` produces a `DataQualityReport` with:
   - Total checked, valid count, invalid count.
   - Error rate (`totalRejected / totalRead`).
   - Top 5 error categories with counts.
6. `DataQualityService` evaluates: if `errorRate > ErrorThreshold` → abort decision.

**Output:**
- `ValidationResult` with business validation results.
- `DataQualityReport`.
- Updated `rejectedRecords` accumulator.

**Abort Condition:**
If cumulative `errorRate > validationConfig.errorThresholdPercent / 100`, the execution is aborted. The existing `rejectedRecords` are still persisted.

**Error Handling:**
- Business rule failures are `ErrorType.FUNCTIONAL`.
- Catalog lookup failures (catalog unavailable) are `ErrorType.EXTERNAL_INTEGRATION`.
- If abort: set VALIDATE_BUSINESS step to `FAILED`, persist all rejected records collected so far, mark execution `FAILED`, skip to CLOSE.

---

## Step 6: LOAD

**Purpose:** Persist all valid `ProcessedRecord`s to the target destination safely, using a staging-first strategy.

**Input:**
- `List<ProcessedRecord>` that passed all validations
- `Pipeline.targetConfig` (table, schema, load strategy, chunk size)
- Active `PipelineExecution`
- `rejectedRecords` accumulator (from all prior steps)

**Processing:**

### Phase A: Persist Rejected Records

1. Call `RejectedRecordPersister.persist(rejectedRecords, execution)` to write all rejected records to `etl_rejected_records` table. This happens **before** the main load so rejected records are never lost even if the load fails.

### Phase B: Load to Staging

2. Call `StagingLoader.load(processedRecords, targetConfig, execution)`.
3. The staging loader writes in chunks (configurable `chunkSize`, default 500 records per transaction).
4. Each chunk is wrapped in a database transaction:
   - If a chunk commits successfully, move to the next chunk.
   - If a chunk fails, record the failed chunk with detail (start row, end row, error), continue with remaining chunks (or abort per config).
5. All records from `processedRecords` are loaded to the staging table `{tableName}_staging`.
6. The staging table is cleared at the start of each execution (for this pipeline) to ensure clean state.

### Phase C: Validate Staging

7. Call `StagingValidator.validate(targetConfig, execution)`.
8. Staging validation checks:
   - Row count matches `processedRecords.size()`.
   - No null values in mandatory target columns.
   - Business key uniqueness in staging (if UPSERT strategy).
9. If staging validation fails: mark LOAD step `FAILED`, do NOT promote to final table, mark execution `FAILED`, skip to CLOSE.

### Phase D: Promote to Final Table

10. Call `FinalLoader.promote(targetConfig, execution)`.
11. Promotion applies the configured `LoadStrategy`:
    - `INSERT`: `INSERT INTO {tableName} SELECT * FROM {tableName}_staging`
    - `UPSERT`: `INSERT INTO {tableName} ... ON CONFLICT ({businessKey}) DO UPDATE SET ...`
    - `REPLACE`: `DELETE FROM {tableName} WHERE {scope_condition}; INSERT INTO {tableName} SELECT * FROM {tableName}_staging`
12. The promotion is wrapped in a single database transaction.
13. Update `execution.totalLoaded` with the final loaded count.
14. Record `LOAD` step as `SUCCESS`.

**Output:**
- `LoadResult` — loaded count, staging count, promotion status.
- Updated `PipelineExecution.totalLoaded`.

**Error Handling:**
- Chunk load failure: log with chunk reference, continue or abort per `targetConfig.failFastOnChunkError`.
- Staging validation failure: do not promote, abort execution.
- Promotion transaction failure: staging data is preserved for inspection, execution marked `FAILED`.
- All rejected records are persisted regardless of main load outcome.

---

## Step 7: CLOSE

**Purpose:** Finalize the execution record, determine the final status, record all metrics, and emit any configured notifications.

**Input:**
- Active `PipelineExecution` with all counts populated
- Step results from all prior steps
- `rejectedRecords` count

**Processing:**

1. Determine the final `ExecutionStatus`:
   - `SUCCESS` — all steps passed, 0 CRITICAL errors, error rate within threshold.
   - `PARTIAL` — load completed, but some records were rejected (error rate > 0 but <= threshold). Only if `allowPartialLoad = true`.
   - `FAILED` — any step aborted due to error rate exceeded, unrecoverable error, or staging validation failure.
2. Record all `ExecutionMetric`s:
   - `extract.duration.ms`, `transform.duration.ms`, `validate.duration.ms`, `load.duration.ms`
   - `records.read`, `records.transformed`, `records.rejected`, `records.loaded`
   - `records.rejected.rate`, `execution.total.duration.ms`
3. Set `execution.finishedAt = Instant.now()`.
4. Persist the final `PipelineExecution` state via `ExecutionLifecycleService`.
5. Mark `CLOSE` step as `SUCCESS`.
6. Emit notifications if configured:
   - On `FAILED`: log WARN alert with error summary. Invoke `NotificationService` if enabled.
   - On `PARTIAL`: log INFO with rejection count.
   - On `SUCCESS`: log INFO with counts.
7. Clear MDC context.

**Error Handling:**
- Errors in the CLOSE step itself are logged as WARN but do not change the execution's final status (status was already determined).
- CLOSE step always completes so that AUDIT can run.

---

## Step 8: AUDIT

**Purpose:** Persist a complete, immutable audit record capturing all aspects of the execution. This step **always runs**, regardless of whether the execution succeeded or failed.

**Input:**
- Finalized `PipelineExecution`
- All `PipelineExecutionStep` records
- All `ExecutionMetric`s
- All `ExecutionError`s

**Processing:**

1. Construct the `AuditRecord` with:
   - `executionId`, `pipelineId`
   - `action = "PIPELINE_EXECUTED"` (or `"PIPELINE_FAILED"` if status is FAILED)
   - `actorType` from `execution.triggerType`
   - `details` map containing:
     - `status`, `totalRead`, `totalTransformed`, `totalRejected`, `totalLoaded`
     - `durationMs` (from startedAt to finishedAt)
     - `triggeredBy`
     - `stepSummary` (map of stepName → stepStatus)
     - `errorSummary` (if failed)
     - `pipelineVersion`
2. Persist via `AuditRepository.save(auditRecord)`.
3. Mark `AUDIT` step as `SUCCESS`.
4. Log the final audit summary at INFO level.

**Guaranteed Execution:**
The AUDIT step is always called from a `finally` block in `ETLOrchestrator`. Even if all other steps fail, the audit record is written. If the audit step itself fails (e.g., database is down), the failure is logged at ERROR level but does not propagate to the caller.

---

## Error Classification

All errors captured during execution are classified into four types:

| Type | Examples | Retry Eligible |
|---|---|---|
| `TECHNICAL` | IO error, OOM, NullPointerException, DB connection failure | Sometimes (infra errors may be transient) |
| `FUNCTIONAL` | Invalid status code mapping, business rule violation, unmappable field | No (data must be corrected) |
| `DATA_QUALITY` | Null mandatory field, negative amount, invalid date format, duplicate key | No (data must be corrected) |
| `EXTERNAL_INTEGRATION` | API timeout, 503 response, auth failure, external catalog unavailable | Yes (transient external failures) |

---

## Retry Flow

When an execution fails with retry-eligible errors and the `RetryPolicy` allows it:

```
FAILED execution
      │
  retryEligible? (maxRetries not exceeded, error type in retryOnErrors list)
      │ YES
      ▼
  NEW execution created (status = RETRYING)
  retryCount incremented on pipeline execution metadata
      │
  Wait retryDelayMs
      │
      ▼
  Full 8-step execution begins again from INIT
      │
  On success: mark new execution SUCCESS, mark original as superseded
  On failure: increment retry count, evaluate eligibility again
      │
  maxRetries exceeded?
      │ YES
      ▼
  Throw RetryExhaustedException
  Mark final execution FAILED (no more retries)
  Emit critical alert
```

---

## Partial Execution Handling

A `PARTIAL` execution occurs when:

1. Some records were rejected (error rate > 0), but the error rate is below the configured threshold.
2. `validationConfig.allowPartialLoad = true`.
3. The load of valid records completed successfully.

In this case:
- Valid records are loaded to the final destination.
- Rejected records are persisted in `etl_rejected_records` with full context.
- The execution status is `PARTIAL`.
- Metrics show both loaded and rejected counts.
- The audit record notes the partial nature of the load.

A `PARTIAL` execution is **not** treated as a failure for retry purposes. It is a successful, intentional partial load. Operators can inspect the rejected records and decide whether to reprocess them.

---

## Concurrent Execution Safety

OrionETL enforces **single active execution per pipeline** at any given time:

1. Before creating a new execution, `NoDuplicateExecutionRule` queries `ExecutionRepository.findActiveByPipelineId()`.
2. If a `RUNNING` or `RETRYING` execution exists, the new request is rejected with `ExecutionConflictException`.
3. This check is performed at the domain level (not the controller level) to ensure it is always enforced regardless of entry point.

In a multi-node deployment, a database-level unique constraint or advisory lock can be added to enforce this at the persistence layer as well.
