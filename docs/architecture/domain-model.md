# Domain Model

The domain model is the heart of OrionETL. It contains all entities, value objects, domain services, repository contracts, and enumerations that express the business concepts of an ETL engine. The domain layer has **no Spring Framework dependencies** — it is pure Java 21.

---

## Entities

### `Pipeline`

Represents a fully configured ETL pipeline definition. This is the root aggregate for pipeline configuration.

| Field | Type | Description |
|---|---|---|
| `id` | `PipelineId` | Unique identifier (UUID value object). |
| `name` | `String` | Human-readable pipeline name (e.g., `"sales-daily"`). |
| `version` | `String` | Semantic version string (e.g., `"1.3.0"`). |
| `description` | `String` | Free-text description of the pipeline's purpose. |
| `status` | `PipelineStatus` | Current lifecycle status (`ACTIVE`, `INACTIVE`, `DEPRECATED`). |
| `sourceConfig` | `SourceConfig` | Configuration for the data source (type, connection, format). |
| `targetConfig` | `TargetConfig` | Configuration for the data destination (table, strategy). |
| `transformationConfig` | `TransformationConfig` | List of transformation rules to apply. |
| `validationConfig` | `ValidationConfig` | Schema rules, business rules, error threshold. |
| `scheduleConfig` | `ScheduleConfig` | Cron expression, time windows, timezone. |
| `retryPolicy` | `RetryPolicy` | Max retries, delay, retryable error types. |

---

### `PipelineVersion`

Tracks the version history of a pipeline definition. Enables rollback and change auditing.

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Primary key. |
| `pipelineId` | `PipelineId` | Reference to the owning `Pipeline`. |
| `version` | `String` | Version string for this snapshot. |
| `changelog` | `String` | Description of what changed in this version. |
| `createdAt` | `Instant` | When this version was created. |
| `isActive` | `boolean` | Whether this is the currently deployed version. |

---

### `PipelineExecution`

Represents a single execution instance of a pipeline. Created at the start of each run and updated throughout.

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Internal database identifier. |
| `pipelineId` | `PipelineId` | Reference to the pipeline being executed. |
| `executionId` | `ExecutionId` | Public-facing UUID used in APIs and logs (correlation ID). |
| `status` | `ExecutionStatus` | Current execution status. |
| `startedAt` | `Instant` | When execution began. |
| `finishedAt` | `Instant` | When execution ended (null if still running). |
| `triggeredBy` | `String` | Who/what triggered this run: `"scheduler"`, `"api:user@example.com"`, `"cli"`. |
| `triggerType` | `TriggerType` | Enum: `MANUAL`, `SCHEDULED`, `RETRY`, `CLI`. |
| `totalRead` | `RecordCount` | Total records read from source. |
| `totalTransformed` | `RecordCount` | Total records successfully transformed. |
| `totalRejected` | `RecordCount` | Total records rejected at any step. |
| `totalLoaded` | `RecordCount` | Total records successfully loaded to destination. |
| `errorSummary` | `String` | Short human-readable summary of failure cause (null on success). |
| `steps` | `List<PipelineExecutionStep>` | Step-level execution records. |
| `errors` | `List<ExecutionError>` | All classified errors from this execution. |

**ExecutionStatus values:** `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `PARTIAL`, `SKIPPED`, `RETRYING`

---

### `PipelineExecutionStep`

Tracks the execution of each individual step within a pipeline run.

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Primary key. |
| `executionId` | `ExecutionId` | Reference to the owning `PipelineExecution`. |
| `stepName` | `String` | Step identifier (e.g., `"EXTRACT"`, `"VALIDATE_SCHEMA"`). |
| `stepOrder` | `int` | Execution order (1–8). |
| `status` | `StepStatus` | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `SKIPPED`. |
| `startedAt` | `Instant` | When this step started. |
| `finishedAt` | `Instant` | When this step finished. |
| `errorDetail` | `String` | Error description if step failed. |
| `recordsProcessed` | `long` | Number of records processed in this step. |

---

### `ExecutionError`

A classified, structured error captured during execution. Each significant error is stored for debugging and audit.

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Primary key. |
| `executionId` | `ExecutionId` | Reference to the owning execution. |
| `stepName` | `String` | Step during which the error occurred. |
| `errorType` | `ErrorType` | Classification: `TECHNICAL`, `FUNCTIONAL`, `DATA_QUALITY`, `EXTERNAL_INTEGRATION`. |
| `errorCode` | `String` | Machine-readable error code (e.g., `"EXTRACTION_IO_FAILURE"`). |
| `message` | `String` | Human-readable error message. |
| `stackTrace` | `String` | Full Java stack trace (for TECHNICAL errors). |
| `recordReference` | `String` | Row number or business key identifying the affected record (for DATA_QUALITY errors). |

---

### `ExecutionMetric`

A named numeric metric captured at a specific point during execution. Used for dashboards and alerting.

| Field | Type | Description |
|---|---|---|
| `id` | `UUID` | Primary key. |
| `executionId` | `ExecutionId` | Reference to the owning execution. |
| `metricName` | `String` | Metric key (e.g., `"extract.duration.ms"`, `"transform.records.per.second"`). |
| `metricValue` | `double` | Numeric metric value. |
| `recordedAt` | `Instant` | When the metric was captured. |

---

### `RetryPolicy`

Embedded configuration object defining how a failed pipeline should be retried.

| Field | Type | Description |
|---|---|---|
| `maxRetries` | `int` | Maximum number of retry attempts (0 = no retries). |
| `retryDelayMs` | `long` | Delay in milliseconds between retry attempts. |
| `retryOnErrors` | `List<ErrorType>` | Which error types are eligible for retry (e.g., only `EXTERNAL_INTEGRATION`). |

---

### `SourceConfig`

Configuration for the data source that the pipeline extracts from.

| Field | Type | Description |
|---|---|---|
| `type` | `SourceType` | `CSV`, `EXCEL`, `JSON`, `API`, `DATABASE`. |
| `connectionDetails` | `Map<String, String>` | Source-specific connection parameters (file path, URL, JDBC URL, etc.). |
| `format` | `String` | File format hint (e.g., `"csv"`, `"xlsx"`). |
| `delimiter` | `Character` | CSV field delimiter (default: `,`). |
| `encoding` | `String` | File encoding (default: `"UTF-8"`). |
| `hasHeaders` | `boolean` | Whether the first row contains column headers. |
| `headerMapping` | `Map<String, String>` | Maps source column names to canonical field names. |
| `authConfig` | `AuthConfig` | Authentication: bearer token, API key, basic auth credentials. |

---

### `TargetConfig`

Configuration for the destination where processed data is loaded.

| Field | Type | Description |
|---|---|---|
| `type` | `TargetType` | `DATABASE`, `CSV`, `WAREHOUSE`. |
| `connectionDetails` | `Map<String, String>` | Destination-specific connection parameters. |
| `tableName` | `String` | Target table name. |
| `schema` | `String` | Database schema (e.g., `"public"`). |
| `stagingTableName` | `String` | Staging table name (derived from `tableName` if not set). |
| `loadStrategy` | `LoadStrategy` | `INSERT`, `UPSERT`, `REPLACE`. |
| `businessKey` | `List<String>` | Columns that form the natural key for upsert operations. |
| `chunkSize` | `int` | Number of records per database transaction (default: 500). |

---

### `ValidationConfig`

Configuration for all validation rules applied to this pipeline's data.

| Field | Type | Description |
|---|---|---|
| `mandatoryColumns` | `List<String>` | Column names that must be present and non-null. |
| `columnTypes` | `Map<String, String>` | Expected data type per column (e.g., `{"amount": "DECIMAL", "sale_date": "DATE"}`). |
| `businessRules` | `List<BusinessRuleConfig>` | Named business rules with parameters (catalog lookup, range, regex, etc.). |
| `errorThresholdPercent` | `double` | Maximum percentage of invalid records allowed before aborting (e.g., `5.0` = 5%). |
| `allowPartialLoad` | `boolean` | Whether to continue loading valid records when errors exist below threshold. |

---

### `RawRecord`

A single record as read from the source, before any transformation.

| Field | Type | Description |
|---|---|---|
| `rowNumber` | `long` | 1-based row number from the source file or query. |
| `data` | `Map<String, Object>` | Raw field values keyed by source column name. |
| `sourceFile` | `String` | Source file path or API endpoint URL. |
| `extractedAt` | `Instant` | When this record was extracted. |

---

### `ProcessedRecord`

A record that has been successfully transformed and is ready for loading.

| Field | Type | Description |
|---|---|---|
| `rawRecordRef` | `long` | Row number of the originating `RawRecord`. |
| `transformedData` | `Map<String, Object>` | Normalized field values keyed by canonical column names. |
| `transformedAt` | `Instant` | When transformation was applied. |
| `pipelineVersion` | `String` | Pipeline version that processed this record (for traceability). |

---

### `RejectedRecord`

A record that was rejected at any step — validation, transformation, or business rules.

| Field | Type | Description |
|---|---|---|
| `rawRecord` | `RawRecord` | The original unprocessed record. |
| `reason` | `String` | Short human-readable rejection reason. |
| `validationErrors` | `List<ValidationError>` | All validation errors that caused rejection. |
| `rejectedAt` | `Instant` | When the record was rejected. |
| `step` | `String` | Step name where rejection occurred (`"VALIDATE_SCHEMA"`, `"VALIDATE_BUSINESS"`, `"TRANSFORM"`). |

---

### `AuditRecord`

A complete, immutable audit entry created at the end of every execution.

| Field | Type | Description |
|---|---|---|
| `executionId` | `ExecutionId` | Reference to the execution being audited. |
| `pipelineId` | `PipelineId` | Reference to the pipeline. |
| `action` | `String` | Action description (e.g., `"PIPELINE_EXECUTED"`, `"PIPELINE_FAILED"`). |
| `actorType` | `String` | Who triggered the action: `"SCHEDULER"`, `"API"`, `"CLI"`. |
| `details` | `Map<String, Object>` | Full audit detail: counts, duration, step results, triggeredBy, error summary. |
| `timestamp` | `Instant` | When the audit record was created. |

---

### `ValidationResult`

The aggregate output of a validation step.

| Field | Type | Description |
|---|---|---|
| `isValid` | `boolean` | True if no CRITICAL errors were found. |
| `errors` | `List<ValidationError>` | All validation errors (any severity). |
| `warnings` | `List<ValidationError>` | Errors classified as WARNING (non-blocking). |
| `errorRate` | `double` | Percentage of records with at least one error. |
| `totalChecked` | `long` | Total number of records evaluated. |

---

### `ValidationError`

A single validation failure on a specific field of a specific record.

| Field | Type | Description |
|---|---|---|
| `field` | `String` | The field name that failed validation. |
| `value` | `Object` | The actual value that caused the failure. |
| `rule` | `String` | The rule that was violated (e.g., `"NOT_NULL"`, `"POSITIVE_AMOUNT"`, `"CATALOG_LOOKUP"`). |
| `message` | `String` | Human-readable explanation. |
| `severity` | `ErrorSeverity` | `CRITICAL`, `WARNING`, `INFO`. |
| `rowNumber` | `long` | Source row number for traceability. |

---

## Value Objects

Value objects are immutable, self-validating types that wrap primitive values and enforce domain constraints.

| Value Object | Wrapped Type | Constraint |
|---|---|---|
| `PipelineId` | `UUID` | Non-null UUID. |
| `ExecutionId` | `UUID` | Non-null UUID, generated with `UUID.randomUUID()`. |
| `RecordCount` | `long` | Must be >= 0. Throws `IllegalArgumentException` if negative. |
| `ErrorThreshold` | `double` | Must be between 0.0 and 1.0 inclusive. |
| `BusinessKey` | `Map<String, Object>` | Non-empty map. Provides `toString()` for logging. |

In Java 21, these are implemented as `record` types with compact constructors:

```java
public record ErrorThreshold(double value) {
    public ErrorThreshold {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Error threshold must be between 0.0 and 1.0, got: " + value);
        }
    }
}
```

---

## Domain Services

Domain services contain business logic that operates across multiple entities or does not naturally belong to any single entity.

### `PipelineOrchestrationService`

Coordinates the high-level orchestration decisions for pipeline execution:

- Checks `NoDuplicateExecutionRule` before allowing a new execution to start.
- Checks `AllowedExecutionWindowRule` if the pipeline has time window restrictions.
- Delegates execution coordination to `ETLOrchestrator` (in the application layer).
- Determines if a failed execution is eligible for retry using `RetryEligibilityRule`.

### `ExecutionLifecycleService`

Manages the lifecycle state transitions of a `PipelineExecution`:

- `createExecution(pipelineId, triggeredBy)` → creates and persists a new `PipelineExecution` in `PENDING` state.
- `markRunning(executionId)` → transitions to `RUNNING`, records `startedAt`.
- `markSuccess(executionId, counts)` → transitions to `SUCCESS`, records `finishedAt`, updates record counts.
- `markFailed(executionId, errorSummary)` → transitions to `FAILED`.
- `markPartial(executionId, counts, errorSummary)` → transitions to `PARTIAL` (records loaded but with rejections).
- `closeExecution(executionId, finalCounts)` → finalizes and persists all metrics.

### `DataQualityService`

Evaluates overall data quality for an execution batch:

- Aggregates `ValidationError`s from all steps.
- Calculates `errorRate = rejectedCount / totalReadCount`.
- Compares against `ErrorThreshold` from `ValidationConfig`.
- Returns a `DataQualityReport` with categories, counts, and the abort recommendation.
- Enforces: if `errorRate > threshold` → abort; if `errorRate > 0 && errorRate <= threshold` → allow with `PARTIAL` potential.

---

## Domain Contracts (Interfaces — Ports)

These are the "ports" in hexagonal architecture. The domain defines what it needs; infrastructure provides concrete implementations.

### `DataExtractor`

```java
public interface DataExtractor {
    boolean supports(SourceType sourceType);
    ExtractionResult extract(SourceConfig config, PipelineExecution execution);
}
```

Implemented by: `CsvExtractor`, `ExcelExtractor`, `JsonExtractor`, `ApiExtractor`, `DatabaseExtractor`

### `DataTransformer`

```java
public interface DataTransformer {
    boolean supports(String pipelineType);
    TransformationResult transform(List<RawRecord> records, TransformationConfig config, PipelineExecution execution);
}
```

Implemented by: `CommonTransformer`, `SalesTransformer`, `InventoryTransformer`, `CustomerTransformer`

### `DataLoader`

```java
public interface DataLoader {
    boolean supports(TargetType targetType);
    LoadResult load(List<ProcessedRecord> records, TargetConfig config, PipelineExecution execution);
}
```

Implemented by: `StagingLoader`, `FinalLoader`

### `DataValidator`

```java
public interface DataValidator {
    ValidationResult validate(List<RawRecord> records, ValidationConfig config, PipelineExecution execution);
}
```

Implemented by: `SchemaValidator`, `BusinessValidator`, `QualityValidator`

### `AuditRepository`

```java
public interface AuditRepository {
    void save(AuditRecord auditRecord);
    List<AuditRecord> findByExecutionId(ExecutionId executionId);
}
```

### `ExecutionRepository`

```java
public interface ExecutionRepository {
    PipelineExecution save(PipelineExecution execution);
    Optional<PipelineExecution> findById(ExecutionId executionId);
    Optional<PipelineExecution> findActiveByPipelineId(PipelineId pipelineId);
    List<PipelineExecution> findByPipelineId(PipelineId pipelineId);
}
```

### `PipelineRepository`

```java
public interface PipelineRepository {
    Optional<Pipeline> findById(PipelineId id);
    Optional<Pipeline> findByName(String name);
    List<Pipeline> findAllActive();
    Pipeline save(Pipeline pipeline);
}
```

---

## Enumerations

### `PipelineStatus`

| Value | Meaning |
|---|---|
| `ACTIVE` | Pipeline is enabled and can be executed. |
| `INACTIVE` | Pipeline exists but is disabled (will not run). |
| `DEPRECATED` | Pipeline is retired; kept for historical reference only. |

### `ExecutionStatus`

| Value | Meaning |
|---|---|
| `PENDING` | Execution created but not yet started. |
| `RUNNING` | Execution is actively in progress. |
| `SUCCESS` | All steps completed successfully, within error threshold. |
| `FAILED` | Execution aborted due to an unrecoverable error. |
| `PARTIAL` | Execution completed but with rejections below threshold. |
| `SKIPPED` | Execution was skipped (e.g., outside time window). |
| `RETRYING` | A failed execution is being retried. |

### `StepStatus`

`PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `SKIPPED`

### `ErrorType`

| Value | Meaning |
|---|---|
| `TECHNICAL` | Infrastructure failure (IO error, DB timeout, network issue). |
| `FUNCTIONAL` | Business rule violation (invalid amount, missing catalog reference). |
| `DATA_QUALITY` | Data content issue (null mandatory field, wrong type, out-of-range value). |
| `EXTERNAL_INTEGRATION` | External system failure (API timeout, auth error, unavailable service). |

### `ErrorSeverity`

| Value | Meaning |
|---|---|
| `CRITICAL` | Execution cannot continue. Record must be rejected and counted against threshold. |
| `WARNING` | Issue noted but does not block processing. |
| `INFO` | Informational note, no action required. |

### `SourceType`

`CSV`, `EXCEL`, `JSON`, `API`, `DATABASE`

### `TargetType`

`DATABASE`, `CSV`, `WAREHOUSE`

### `LoadStrategy`

| Value | Behavior |
|---|---|
| `INSERT` | Only inserts new records. Fails if business key already exists. |
| `UPSERT` | Inserts new records, updates existing ones by business key. |
| `REPLACE` | Deletes all existing records for this batch scope, then inserts. |

### `TriggerType`

`MANUAL`, `SCHEDULED`, `RETRY`, `CLI`
