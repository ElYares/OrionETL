# Execution Rules

These rules govern **when** a pipeline can run, **how** it behaves under failure conditions, and **what conditions** must be met before certain steps can proceed. All rules are implemented as named `Rule` classes in `domain/rules/` and enforced by `PipelineOrchestrationService` and `ETLOrchestrator`.

---

## Rule 1: No Duplicate Active Execution

**Rule ID:** `EXEC-001`
**Class:** `NoDuplicateExecutionRule`

A pipeline cannot be triggered if there is already an execution of the same pipeline with status `RUNNING` or `RETRYING`.

**Rationale:** Running two instances of the same pipeline simultaneously against the same source and target can cause data corruption, duplicate loads, race conditions in staging tables, and unreliable audit counts.

**Enforcement:**
- Checked at the start of `INIT` step, before creating the new execution record.
- The check queries `ExecutionRepository.findActiveByPipelineId(pipelineId)`.
- If an active execution is found, throws `ExecutionConflictException`.

**Behavior on violation:**
- HTTP `409 Conflict` returned to caller with the ID and start time of the active execution.
- No new execution record is created.
- The existing execution is not affected.

**Exception:**
- If the existing execution has been in `RUNNING` state for longer than `pipeline.maxRunDurationMinutes` (configured per pipeline), it may be considered stuck. An operator can force-expire it via the admin API. This is a manual intervention, not automatic.

---

## Rule 2: Retry Limit Per Pipeline

**Rule ID:** `EXEC-002`
**Class:** `RetryEligibilityRule`

A failed execution can be automatically retried a maximum of `RetryPolicy.maxRetries` times. Once the retry limit is reached, no further automatic retries are attempted.

**Rationale:** Unlimited retries can cause infinite loops and resource exhaustion, especially when the failure is due to permanent data quality issues rather than transient infrastructure failures.

**Enforcement:**
- Checked before scheduling a retry attempt.
- `retryCount` is tracked on the execution metadata.
- If `retryCount >= maxRetries`, throws `RetryExhaustedException`.

**Configuration (per pipeline):**

```yaml
retry-policy:
  max-retries: 3
  retry-delay-ms: 300000   # 5 minutes
  retry-on-errors:
    - EXTERNAL_INTEGRATION
    - TECHNICAL
```

**Behavior:**
- Only error types listed in `retryOnErrors` trigger automatic retry.
- `FUNCTIONAL` and `DATA_QUALITY` errors **do not** trigger automatic retry (the data must be corrected first).
- Each retry creates a new `PipelineExecution` record with `triggerType = RETRY` and an incremented retry counter.
- If all retries are exhausted, the execution is marked `FAILED` permanently and a critical alert is emitted.

---

## Rule 3: Execution Time Window Enforcement

**Rule ID:** `EXEC-003`
**Class:** `AllowedExecutionWindowRule`

A pipeline may only be executed within its configured time windows. If a trigger arrives outside the allowed window, the execution is skipped.

**Rationale:** Some pipelines must only run during specific business hours or off-peak windows to avoid impacting transactional systems or to comply with data availability guarantees.

**Configuration:**

```yaml
schedule-config:
  timezone: "UTC"
  allowed-windows:
    - start: "01:00"
      end: "04:00"
      days: ["MON", "TUE", "WED", "THU", "FRI"]
```

**Enforcement:**
- Evaluated during `INIT` step after loading pipeline config.
- Uses the pipeline's configured timezone for window evaluation.
- If the current time is outside all allowed windows, a `PipelineExecution` is created with status `SKIPPED` and no further steps are executed.

**Behavior on skip:**
- HTTP `200 OK` returned with execution ID and status `SKIPPED`.
- A `SKIPPED` execution record is persisted for audit trail.
- No alert is emitted for a normal skip (only if the skip is unexpected, based on configuration).

---

## Rule 4: Mandatory Parameter Validation

**Rule ID:** `EXEC-004`

Certain pipelines require mandatory runtime parameters to be provided before execution can begin. These parameters cannot have default values because they are context-specific (e.g., a specific batch date, a source file path override, a target schema name).

**Configuration:**

```yaml
required-parameters:
  - name: "batch_date"
    type: "DATE"
    description: "The business date for which to process data."
  - name: "source_file_path"
    type: "STRING"
    description: "Override path for the source CSV file."
```

**Enforcement:**
- Checked during `INIT` step after loading pipeline config.
- If any required parameter is missing from the execution request, the execution is rejected before a `PipelineExecution` record is created.
- HTTP `400 Bad Request` returned with the list of missing parameters.

---

## Rule 5: SUCCESS Requires No CRITICAL Errors

**Rule ID:** `EXEC-005`
**Class:** `CriticalErrorBlocksSuccessRule`

An execution **cannot** be marked `SUCCESS` if any `ExecutionError` with `ErrorSeverity.CRITICAL` was recorded during any step of that execution.

**Rationale:** A SUCCESS status implies the pipeline ran completely and correctly. CRITICAL errors indicate that some data was not processed correctly or that a business invariant was violated, which makes a SUCCESS designation misleading.

**Enforcement:**
- Evaluated during the `CLOSE` step when determining the final `ExecutionStatus`.
- If CRITICAL errors exist: the execution is marked `FAILED` (or `PARTIAL` if `allowPartialLoad = true` and records were loaded).
- Non-critical errors (WARNING, INFO) do not block SUCCESS.

---

## Rule 6: Production Load Requires Prior Successful Validation

**Rule ID:** `EXEC-006`

The `FinalLoader` (promotion from staging to final tables) must **never** be invoked unless both the `VALIDATE_SCHEMA` step and the `VALIDATE_BUSINESS` step have recorded status `SUCCESS` for this execution.

**Rationale:** Loading unvalidated data to production tables breaks data integrity guarantees and can corrupt downstream reports, analytics, and transactional systems.

**Enforcement:**
- Checked by `ETLOrchestrator` before invoking `FinalLoader`.
- The orchestrator verifies that the `VALIDATE_SCHEMA` and `VALIDATE_BUSINESS` step records exist and have status `SUCCESS`.
- If either step is missing or has a non-SUCCESS status, the orchestrator throws an `IllegalStateException` and aborts the load.

---

## Rule 7: Extraction Failure Blocks All Downstream Steps

**Rule ID:** `EXEC-007`

If the `EXTRACT` step fails (throws an exception or returns zero records due to a source error), the `TRANSFORM`, `VALIDATE_BUSINESS`, and `LOAD` steps **must not execute**.

**Rationale:** Transformation, validation, and loading have no meaningful input if extraction failed. Executing downstream steps on an empty or partial dataset could result in incorrect totals being recorded, or a false `SUCCESS` state.

**Enforcement:**
- `ETLOrchestrator` uses a short-circuit pattern: each step's result is checked before invoking the next step.
- If EXTRACT step status is `FAILED`, the orchestrator transitions directly to `CLOSE` (which leads to `AUDIT`).
- Rejected records accumulated during partial extraction are still persisted.

**Zero-record edge case:**
- If extraction succeeds (no error) but returns 0 records (empty source), this is **not** considered an extraction failure.
- The execution proceeds through all steps, each operating on an empty list.
- The final execution is marked `SUCCESS` with all counts at zero, plus a INFO-level note that the source was empty.

---

## Rule 8: Business Validation Error Rate Threshold Triggers Abort

**Rule ID:** `EXEC-008`
**Class:** `ErrorThresholdRule`

If the cumulative error rate (rejected records / total read records) exceeds `validationConfig.errorThresholdPercent`, the execution **must** abort immediately. No records are loaded to the final destination.

**Rationale:** A high rejection rate suggests that the source data is fundamentally incorrect — possibly a wrong file, a mis-configured pipeline, or a systemic upstream data quality issue. Loading partial results in this condition would produce misleading data in the destination system.

**Formula:**
```
errorRate = totalRejectedRecords / totalReadRecords * 100
abortCondition = errorRate > errorThresholdPercent
```

**Enforcement:**
- Evaluated by `DataQualityService` after `VALIDATE_BUSINESS` step.
- If abort condition is true: set VALIDATE_BUSINESS step to `FAILED`, persist all accumulated rejected records, mark execution `FAILED`, skip LOAD step.
- The threshold is configured per pipeline and can be as strict as `0%` (zero tolerance) or as lenient as `50%`.

**Examples:**
- Sales pipeline: 5% threshold. If more than 5% of records fail, abort.
- Customer pipeline: 1% threshold. Very strict — any significant quality issue aborts the load.

---

## Rule 9: Rejected Records Must Always Be Persisted

**Rule ID:** `EXEC-009`

Every record that is rejected at any step — schema validation, transformation, or business validation — **must** be persisted to the `etl_rejected_records` table with its full context before the execution closes.

**Rationale:** Rejected records represent data that could not be processed. They must be traceable, inspectable, and potentially reprocessable. Silently discarding rejected records is not acceptable in an enterprise ETL system.

**What must be persisted per rejected record:**
- The full raw data (`Map<String, Object>` as JSONB).
- The step where rejection occurred.
- All `ValidationError`s that caused rejection (field, rule, message, severity).
- The batch reference (`executionId`, `pipelineId`).
- The source row number for traceability back to the original file.
- The timestamp of rejection.

**Enforcement:**
- `RejectedRecordPersister.persist()` is called in the `LOAD` step **before** the staging load begins.
- If the main load fails, rejected records are already persisted.
- If `RejectedRecordPersister` itself fails, this is classified as `ErrorType.TECHNICAL` and escalated — the execution cannot be marked SUCCESS.

---

## Rule 10: No Production Load Without Staging Validation

**Rule ID:** `EXEC-010`

The promotion from staging to final tables (`FinalLoader.promote()`) must **never** be called unless `StagingValidator.validate()` has returned a passing result for the current execution.

**Rationale:** Staging validation is the last defense before data enters the production destination. It verifies that the staging table contains what was expected (correct row count, no nulls in mandatory columns, correct business key uniqueness). Bypassing this check risks loading corrupted or incomplete data.

**Enforcement:**
- `ETLOrchestrator` checks the staging validation result before calling `FinalLoader.promote()`.
- If staging validation fails: mark `LOAD` step `FAILED`, do not call `FinalLoader`, preserve staging table for manual inspection, mark execution `FAILED`.
- Staging table contents are retained until the next successful execution of the same pipeline (at which point they are overwritten).
