# Project Structure

This document describes the full Maven project layout for OrionETL, with explanations for each directory's purpose, the classes it contains, and the responsibilities it carries.

---

## Full Directory Tree

```
OrionETL/
├── src/
│   ├── main/
│   │   ├── java/com/elyares/etl/
│   │   │   ├── EtlApplication.java
│   │   │   ├── shared/
│   │   │   │   ├── exception/
│   │   │   │   ├── constants/
│   │   │   │   ├── util/
│   │   │   │   ├── logging/
│   │   │   │   └── response/
│   │   │   ├── config/
│   │   │   │   ├── database/
│   │   │   │   ├── batch/
│   │   │   │   ├── scheduler/
│   │   │   │   ├── properties/
│   │   │   │   └── beans/
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── pipeline/
│   │   │   │   │   ├── execution/
│   │   │   │   │   ├── source/
│   │   │   │   │   ├── target/
│   │   │   │   │   ├── validation/
│   │   │   │   │   └── audit/
│   │   │   │   ├── enums/
│   │   │   │   ├── valueobject/
│   │   │   │   ├── service/
│   │   │   │   ├── rules/
│   │   │   │   ├── repository/
│   │   │   │   └── contract/
│   │   │   ├── application/
│   │   │   │   ├── usecase/
│   │   │   │   │   ├── pipeline/
│   │   │   │   │   ├── execution/
│   │   │   │   │   ├── validation/
│   │   │   │   │   ├── extraction/
│   │   │   │   │   ├── transformation/
│   │   │   │   │   └── loading/
│   │   │   │   ├── dto/
│   │   │   │   ├── mapper/
│   │   │   │   ├── facade/
│   │   │   │   └── orchestrator/
│   │   │   ├── infrastructure/
│   │   │   │   ├── persistence/
│   │   │   │   │   ├── entity/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── adapter/
│   │   │   │   ├── extractor/
│   │   │   │   │   ├── csv/
│   │   │   │   │   ├── excel/
│   │   │   │   │   ├── json/
│   │   │   │   │   ├── api/
│   │   │   │   │   └── database/
│   │   │   │   ├── transformer/
│   │   │   │   │   ├── common/
│   │   │   │   │   ├── sales/
│   │   │   │   │   ├── inventory/
│   │   │   │   │   └── customer/
│   │   │   │   ├── loader/
│   │   │   │   │   ├── database/
│   │   │   │   │   ├── csv/
│   │   │   │   │   └── warehouse/
│   │   │   │   ├── validator/
│   │   │   │   │   ├── schema/
│   │   │   │   │   ├── business/
│   │   │   │   │   └── quality/
│   │   │   │   ├── scheduler/
│   │   │   │   ├── batch/
│   │   │   │   ├── notification/
│   │   │   │   └── monitoring/
│   │   │   ├── interfaces/
│   │   │   │   ├── rest/
│   │   │   │   │   ├── controller/
│   │   │   │   │   └── request/
│   │   │   │   ├── cli/
│   │   │   │   └── scheduler/
│   │   │   └── pipelines/
│   │   │       ├── sales/
│   │   │       ├── inventory/
│   │   │       ├── crypto/
│   │   │       └── customer/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── db/
│   │       │   └── migration/
│   │       │       ├── V1__create_etl_schema.sql
│   │       │       ├── V2__add_indexes.sql
│   │       │       └── V3__add_audit_table.sql
│   │       ├── pipelines/
│   │       │   ├── sales.yml
│   │       │   ├── inventory.yml
│   │       │   └── crypto.yml
│   │       └── logback-spring.xml
│   └── test/
│       └── java/com/elyares/etl/
│           ├── unit/
│           ├── integration/
│           ├── e2e/
│           └── fixtures/
├── docker/
│   ├── app/
│   └── db/
├── scripts/
├── docs/
├── .env.example
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

---

## Directory Explanations

### Root Files

| File                 | Purpose                                                                                                                               |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `pom.xml`            | Maven build descriptor. Declares Java 21, Spring Boot 3.x parent, all dependencies, and plugin config (compiler, Surefire, Failsafe). |
| `Dockerfile`         | Multi-stage Docker build: compile with Maven → package into a minimal JRE 21 image.                                                   |
| `docker-compose.yml` | Local development compose file: starts PostgreSQL 15, sets env vars, mounts init scripts.                                             |
| `.env.example`       | Template for local environment variables (DB credentials, Spring profile, etc.). Copy to `.env` before running.                       |
| `README.md`          | Root-level project README with quick start, build instructions, and links to documentation.                                           |

---

### `src/main/java/com/elyares/etl/`

#### `EtlApplication.java`

The Spring Boot application entry point. Annotated with `@SpringBootApplication`. Contains the `main()` method. No logic beyond bootstrapping.

---

### `shared/`

Cross-cutting concerns used across all layers. Has no dependencies on other packages in this project.

#### `shared/exception/`

Custom exception hierarchy for the entire application.

| Class | Purpose |
|---|---|
| `EtlException` | Base unchecked exception for all ETL errors. Carries `errorCode` and `stepName`. |
| `ExtractionException` | Thrown when a `DataExtractor` fails to read data from its source. |
| `TransformationException` | Thrown when a transformation rule cannot be applied. |
| `LoadingException` | Thrown when a `DataLoader` fails to write to destination. |
| `ValidationException` | Thrown when validation finds an unrecoverable structural error. |
| `PipelineNotFoundException` | Thrown when a requested pipeline ID does not exist or is not active. |
| `ExecutionConflictException` | Thrown when a pipeline is already running and a new execution is requested. |
| `RetryExhaustedException` | Thrown when a pipeline has exceeded its configured `maxRetries`. |

#### `shared/constants/`

Application-wide string constants to avoid magic literals.

| Class | Contains |
|---|---|
| `StepNames` | `"INIT"`, `"EXTRACT"`, `"VALIDATE_SCHEMA"`, `"TRANSFORM"`, `"VALIDATE_BUSINESS"`, `"LOAD"`, `"CLOSE"`, `"AUDIT"` |
| `ErrorCodes` | Standard error code strings (e.g., `ETL_001`, `EXTRACTION_FAILED`) |
| `MetricKeys` | Metric name constants (e.g., `"records.read"`, `"records.rejected"`, `"execution.duration.ms"`) |
| `ConfigKeys` | YAML config key constants |

#### `shared/util/`

Pure static utility classes. No state, no Spring injection.

| Class | Purpose |
|---|---|
| `DateUtils` | Parse, format, and convert dates and timestamps across formats/timezones. |
| `StringUtils` | Trim, normalize, title-case, snake-case conversion utilities. |
| `JsonUtils` | Serialize/deserialize objects to/from JSON using a shared ObjectMapper. |
| `FileUtils` | Read files from classpath or filesystem, detect encoding. |
| `CurrencyUtils` | Currency conversion helpers (rate lookup delegation). |

#### `shared/logging/`

MDC (Mapped Diagnostic Context) utilities for structured logging.

| Class | Purpose |
|---|---|
| `ExecutionMdcContext` | Sets `executionId`, `pipelineId`, `stepName` into SLF4J MDC at the start of each step. |
| `MdcCleaner` | Clears MDC keys after step completion (used in `finally` blocks). |

#### `shared/response/`

Standard HTTP response wrappers used by REST controllers.

| Class | Purpose |
|---|---|
| `ApiResponse<T>` | Generic wrapper: `{ "status": "ok", "data": T, "timestamp": ... }` |
| `ErrorResponse` | Error wrapper: `{ "status": "error", "errorCode": "...", "message": "...", "details": [...] }` |
| `PagedResponse<T>` | Paginated list wrapper: data, page, size, total. |

---

### `config/`

All Spring `@Configuration` classes. This package is the only place where Spring wiring of infrastructure adapters into domain contracts occurs.

#### `config/database/`

| Class | Purpose |
|---|---|
| `DataSourceConfig` | Configures primary DataSource from `application.yml` properties. |
| `JpaConfig` | Sets JPA dialect, naming strategy, DDL-auto (none — Flyway manages DDL). |
| `TransactionConfig` | Configures `PlatformTransactionManager`. |
| `FlywayConfig` | Flyway migration configuration: locations, baseline, out-of-order flag. |

#### `config/batch/`

(V2) Spring Batch `JobRepository`, `JobLauncher`, and `JobBuilderFactory` configuration.

#### `config/scheduler/`

| Class | Purpose |
|---|---|
| `SchedulerConfig` | Configures `ThreadPoolTaskScheduler` with pool size and thread name prefix. |

#### `config/properties/`

`@ConfigurationProperties` classes bound to `application.yml` sections.

| Class | Binds to |
|---|---|
| `EtlProperties` | `etl.*` — general engine settings |
| `PipelineProperties` | `etl.pipelines.*` — pipeline registration |
| `RetryProperties` | `etl.retry.*` — global retry defaults |
| `NotificationProperties` | `etl.notifications.*` — alert configuration |

#### `config/beans/`

Manual `@Bean` factory methods for shared infrastructure objects: `ObjectMapper`, `WebClient.Builder`, `ClockBean` (for testable time injection).

---

### `domain/`

The core business model. **No Spring framework imports.** Plain Java 21 with Lombok and Jakarta Bean Validation only.

#### `domain/model/pipeline/`

| Class | Purpose |
|---|---|
| `Pipeline` | Root domain entity. Holds full pipeline definition: id, name, version, source/target/transformation/validation/schedule/retry configs. |
| `PipelineVersion` | Tracks version history: changelog, created date, active flag. |
| `ScheduleConfig` | Cron expression, allowed time windows, timezone. |
| `RetryPolicy` | `maxRetries`, `retryDelayMs`, list of retryable error types. |

#### `domain/model/execution/`

| Class | Purpose |
|---|---|
| `PipelineExecution` | Execution instance: UUID, status, timestamps, record counts (read/transformed/rejected/loaded), triggeredBy, errorSummary. |
| `PipelineExecutionStep` | Step-level tracking: stepName, stepOrder, status, startedAt, finishedAt, recordsProcessed, errorDetail. |
| `ExecutionError` | Classified error: errorType, errorCode, message, stackTrace, recordReference. |
| `ExecutionMetric` | Named metric value recorded at a point in time. |

#### `domain/model/source/`

| Class | Purpose |
|---|---|
| `SourceConfig` | Source type, connection details, file format, delimiter, encoding, headers, authentication config. |
| `RawRecord` | A single unprocessed record: rowNumber, `Map<String, Object> data`, sourceFile, extractedAt. |
| `ExtractionResult` | Outcome of an extraction: list of `RawRecord`s, total count, source metadata. |

#### `domain/model/target/`

| Class | Purpose |
|---|---|
| `TargetConfig` | Target type, connection details, table name, schema, load strategy (INSERT/UPSERT/REPLACE), chunk size. |
| `ProcessedRecord` | A transformed record ready for loading: reference to raw record, transformed data map, pipeline version, transformedAt. |
| `LoadResult` | Outcome of a load operation: loaded count, rejected count, staging status, staging table name. |

#### `domain/model/validation/`

| Class | Purpose |
|---|---|
| `ValidationConfig` | Mandatory columns, expected types, business rules list, error threshold percentage. |
| `ValidationResult` | Aggregate result: isValid, `List<ValidationError>`, warning count, error rate. |
| `ValidationError` | Per-record error: field, value, violated rule, message, severity. |
| `RejectedRecord` | A record that failed validation: reference to `RawRecord`, rejection reason, `List<ValidationError>`, rejectedAt, step name. |
| `DataQualityReport` | Summary report: total checked, valid count, invalid count, error rate, top error categories. |

#### `domain/model/audit/`

| Class | Purpose |
|---|---|
| `AuditRecord` | Full execution audit: executionId, pipelineId, action, actorType, details map, timestamp. |

#### `domain/enums/`

All domain enumerations. See [Domain Model documentation](./domain-model.md) for full enum definitions.

#### `domain/valueobject/`

| Class | Purpose |
|---|---|
| `PipelineId` | Wraps a UUID, validates non-null. Immutable record. |
| `ExecutionId` | Wraps a UUID. Immutable record. |
| `RecordCount` | Non-negative long value. Guards against negative counts. |
| `ErrorThreshold` | 0.0–1.0 double representing the maximum allowed error rate. |
| `BusinessKey` | Composite identifier for a domain record (used for uniqueness checks). |

#### `domain/service/`

| Class | Purpose |
|---|---|
| `PipelineOrchestrationService` | Coordinates the pipeline execution lifecycle: checks preconditions (no duplicate execution, valid time window), delegates to step services. |
| `ExecutionLifecycleService` | Creates executions, transitions statuses, closes executions, manages retry eligibility. |
| `DataQualityService` | Evaluates whether error rate exceeds threshold, produces `DataQualityReport`, makes abort/proceed decisions. |

#### `domain/rules/`

Named, encapsulated business rules. Each rule has a single `evaluate(context)` method.

| Class | Rule |
|---|---|
| `NoDuplicateExecutionRule` | Fails if a RUNNING or RETRYING execution exists for the same pipeline. |
| `RetryEligibilityRule` | Checks if a FAILED execution has remaining retries per `RetryPolicy`. |
| `ErrorThresholdRule` | Compares current error rate against configured threshold. |
| `AllowedExecutionWindowRule` | Checks if current time is within the pipeline's configured execution window. |
| `CriticalErrorBlocksSuccessRule` | Ensures SUCCESS status is not set if any CRITICAL errors exist. |

#### `domain/repository/` and `domain/contract/`

Port interfaces that the domain depends on (implemented by infrastructure adapters):

| Interface | Purpose |
|---|---|
| `PipelineRepository` | CRUD for `Pipeline` definitions. |
| `ExecutionRepository` | Create, update, find executions and steps. |
| `AuditRepository` | Persist and retrieve `AuditRecord`s. |
| `RejectedRecordRepository` | Persist `RejectedRecord`s. |
| `DataExtractor` | Extract data from a configured source. |
| `DataTransformer` | Transform a list of `RawRecord`s. |
| `DataLoader` | Load `ProcessedRecord`s to a destination. |
| `DataValidator` | Validate records, return `ValidationResult`. |

---

### `application/`

#### `application/usecase/`

Each sub-package contains use cases for a specific concern:

| Sub-package | Use Cases |
|---|---|
| `usecase/pipeline/` | `GetPipelineUseCase`, `ListPipelinesUseCase`, `ResolvePipelineConfigUseCase` |
| `usecase/execution/` | `ExecutePipelineUseCase`, `GetExecutionStatusUseCase`, `ListExecutionsUseCase`, `RetryExecutionUseCase` |
| `usecase/validation/` | `ValidateInputDataUseCase`, `ValidateBusinessDataUseCase` |
| `usecase/extraction/` | `ExtractDataUseCase` |
| `usecase/transformation/` | `TransformDataUseCase` |
| `usecase/loading/` | `LoadProcessedDataUseCase`, `RegisterAuditUseCase`, `PersistRejectedRecordsUseCase` |

#### `application/orchestrator/`

| Class | Purpose |
|---|---|
| `ETLOrchestrator` | Main class that drives the 8-step execution flow. Calls use cases in sequence. Handles step-level exceptions. Ensures `AUDIT` step always runs. |

#### `application/facade/`

| Class | Purpose |
|---|---|
| `PipelineExecutionFacade` | Combines `ExecutePipelineUseCase` + status polling for the REST controller. |
| `ExecutionMonitoringFacade` | Aggregates execution status, step details, metrics, and rejected records for the monitoring endpoint. |

#### `application/dto/`

Data Transfer Objects (not domain models) exchanged across layer boundaries:

- `PipelineDto`, `PipelineExecutionDto`, `ExecutionStepDto`
- `ExecutionRequestDto` (input to trigger execution)
- `ExecutionStatusDto` (polling response)
- `RejectedRecordDto`, `AuditRecordDto`, `ExecutionMetricDto`

#### `application/mapper/`

MapStruct mapper interfaces:

| Interface | Maps |
|---|---|
| `PipelineMapper` | `Pipeline` ↔ `PipelineDto` ↔ `EtlPipelineEntity` |
| `ExecutionMapper` | `PipelineExecution` ↔ `PipelineExecutionDto` ↔ `EtlPipelineExecutionEntity` |
| `AuditMapper` | `AuditRecord` ↔ `AuditRecordDto` ↔ `EtlAuditRecordEntity` |

---

### `infrastructure/`

#### `infrastructure/persistence/entity/`

JPA `@Entity` classes that map to database tables. One entity per table:

`EtlPipelineEntity`, `EtlPipelineExecutionEntity`, `EtlExecutionStepEntity`, `EtlExecutionErrorEntity`, `EtlRejectedRecordEntity`, `EtlAuditRecordEntity`, `EtlExecutionMetricEntity`

#### `infrastructure/persistence/repository/`

Spring Data JPA `JpaRepository` interfaces for each entity. Custom `@Query` methods for non-trivial lookups (e.g., find active execution by pipeline ID, find executions in date range).

#### `infrastructure/persistence/adapter/`

Adapter classes that implement domain repository interfaces using JPA repositories:

| Class | Implements |
|---|---|
| `PipelineRepositoryAdapter` | `PipelineRepository` |
| `ExecutionRepositoryAdapter` | `ExecutionRepository` |
| `AuditRepositoryAdapter` | `AuditRepository` |
| `RejectedRecordRepositoryAdapter` | `RejectedRecordRepository` |

#### `infrastructure/extractor/`

Each sub-package contains one extractor implementing `DataExtractor`:

| Package | Class | Source |
|---|---|---|
| `extractor/csv/` | `CsvExtractor` | CSV files |
| `extractor/excel/` | `ExcelExtractor` | `.xlsx`/`.xls` files |
| `extractor/json/` | `JsonExtractor` | JSON files |
| `extractor/api/` | `ApiExtractor` | REST APIs |
| `extractor/database/` | `DatabaseExtractor` | JDBC data sources |

#### `infrastructure/transformer/`

| Package | Class | Scope |
|---|---|---|
| `transformer/common/` | `CommonTransformer` | Generic: trim, type convert, dates, currency, column rename |
| `transformer/sales/` | `SalesTransformer` | Sales-specific: subtotal, tax, channel mapping |
| `transformer/inventory/` | `InventoryTransformer` | SKU normalization, quantity consolidation |
| `transformer/customer/` | `CustomerTransformer` | Name, email, phone normalization |

#### `infrastructure/loader/`

| Package | Class | Purpose |
|---|---|---|
| `loader/database/` | `StagingLoader` | Write `ProcessedRecord`s to staging table |
| `loader/database/` | `StagingValidator` | Validate staging table contents before promotion |
| `loader/database/` | `FinalLoader` | Promote staging → final table |
| `loader/database/` | `RejectedRecordPersister` | Persist `RejectedRecord`s to `etl_rejected_records` |

#### `infrastructure/validator/`

| Package | Class | Purpose |
|---|---|---|
| `validator/schema/` | `SchemaValidator` | Column presence, types, nullability |
| `validator/business/` | `BusinessValidator` | Business rules, catalog lookups, uniqueness |
| `validator/quality/` | `QualityValidator` | Error rate, `DataQualityReport`, threshold enforcement |

#### `infrastructure/scheduler/`

`@Scheduled` beans that trigger pipelines on cron expressions (V2).

#### `infrastructure/batch/`

(V2) Spring Batch `Job`, `Step`, `ItemReader`, `ItemProcessor`, `ItemWriter` definitions.

#### `infrastructure/notification/`

| Class | Purpose |
|---|---|
| `LogNotificationService` | Emits structured log alerts on execution failure/success (V1). |
| `WebhookNotificationService` | Sends HTTP webhook notifications (V2). |

#### `infrastructure/monitoring/`

| Class | Purpose |
|---|---|
| `ExecutionMetricsCollector` | Records timing and count metrics during execution. |
| `ActuatorHealthContributor` | Custom Spring Actuator `HealthIndicator` for ETL engine state. |

---

### `interfaces/`

#### `interfaces/rest/controller/`

| Class | Endpoints |
|---|---|
| `PipelineController` | `GET /api/v1/pipelines`, `POST /api/v1/pipelines/{id}/execute` |
| `ExecutionController` | `GET /api/v1/executions/{id}`, `GET /api/v1/executions/{id}/steps`, `GET /api/v1/executions/{id}/metrics`, `GET /api/v1/executions/{id}/rejected` |

#### `interfaces/rest/request/`

Request body POJOs validated with Bean Validation: `ExecutePipelineRequest`, `RetryExecutionRequest`.

#### `interfaces/cli/`

`PipelineCliRunner` — implements `CommandLineRunner`. Parses `--pipeline` and `--env` args, calls `PipelineExecutionFacade`.

#### `interfaces/scheduler/`

`ScheduledPipelineTrigger` — reads pipeline schedule configs, registers cron triggers at startup (V2).

---

### `pipelines/`

Each sub-package contains the pipeline-specific wiring:

| Package | Contents |
|---|---|
| `pipelines/sales/` | `SalesPipelineConfig` — loads `sales.yml`, assembles extractor/transformer/validator/loader chain |
| `pipelines/inventory/` | `InventoryPipelineConfig` |
| `pipelines/crypto/` | `CryptoPipelineConfig` |
| `pipelines/customer/` | `CustomerPipelineConfig` |

---

### `src/main/resources/`

| File/Directory | Purpose |
|---|---|
| `application.yml` | Base configuration: server port, Spring datasource, JPA, Flyway, logging. |
| `application-local.yml` | Local dev overrides: in-memory datasource or local Docker DB. |
| `application-dev.yml` | Dev environment overrides. |
| `application-prod.yml` | Production overrides: connection pools, log levels, alert config. |
| `db/migration/` | Flyway SQL migration scripts. Named `V{n}__{description}.sql`. |
| `pipelines/sales.yml` | Sales pipeline YAML configuration. |
| `pipelines/inventory.yml` | Inventory pipeline YAML configuration. |
| `pipelines/crypto.yml` | Crypto pipeline YAML configuration. |
| `logback-spring.xml` | Logback configuration with profile-based appenders: console (local), file (prod). |

---

### `src/test/`

#### `test/unit/`

Pure unit tests. No Spring context. Test domain services, rules, transformers, validators in isolation using Mockito.

- `domain/service/` tests
- `domain/rules/` tests
- `infrastructure/transformer/` tests
- `infrastructure/validator/` tests

#### `test/integration/`

Spring integration tests using `@SpringBootTest` with Testcontainers (PostgreSQL). Test persistence adapters, full use cases with real DB.

- `infrastructure/persistence/adapter/` tests
- `application/usecase/` tests (with DB)

#### `test/e2e/`

End-to-end tests that exercise the full 8-step pipeline flow with a real database container and sample input files.

- `SalesPipelineE2ETest` — CSV → transform → validate → load → audit
- `CustomerPipelineE2ETest` — API mock → transform → validate → load → audit

#### `test/fixtures/`

Shared test data and utilities:

- `SampleDataFactory` — programmatic test record builders
- Sample CSV, JSON, and Excel files used in tests
- `PipelineConfigFixture` — pre-built `Pipeline` objects for testing

---

### `docker/`

| Directory | Contents |
|---|---|
| `docker/app/` | Application Dockerfile and related assets (e.g., JVM options, entrypoint script). |
| `docker/db/` | PostgreSQL init scripts, custom `postgresql.conf` overrides for local dev. |

### `scripts/`

Shell scripts for developer convenience:

| Script | Purpose |
|---|---|
| `run-pipeline.sh` | Trigger a pipeline from CLI: `./scripts/run-pipeline.sh --pipeline sales --env local` |
| `migrate.sh` | Run Flyway migrations manually. |
| `reset-db.sh` | Drop and recreate local database (dev only). |
| `build.sh` | Full Maven build with tests. |
