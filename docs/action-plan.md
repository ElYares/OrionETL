# OrionETL — Implementation Action Plan

This document is the authoritative implementation roadmap for OrionETL. It defines the 10 phases of development, the deliverables of each phase, the test coverage expectations, and the acceptance criteria that must be met before moving to the next phase.

**Total estimated duration:** 12 weeks.

---

## Overview

| Phase | Weeks | Focus |
|---|---|---|
| Phase 1 | 1–2 | Project foundation: Maven, Docker, Flyway, shared layer, domain model |
| Phase 2 | 3–4 | Domain logic and application core: use cases, orchestrator, business rules |
| Phase 3 | 5 | Infrastructure - Persistence: JPA entities, repositories, adapters |
| Phase 4 | 6 | Infrastructure - Extractors: CSV and API extractors |
| Phase 5 | 7 | Infrastructure - Transformers and Validators |
| Phase 6 | 8 | Infrastructure - Loaders: staging, validation, promotion, rejected records |
| Phase 7 | 9 | First pipeline end-to-end: Sales pipeline |
| Phase 8 | 10 | REST interface and monitoring |
| Phase 9 | 11 | Second and third pipelines: Inventory and Customer |
| Phase 10 | 12 | Hardening, V2 preparation, Docker, documentation |

---

## Phase 1: Foundation (Weeks 1–2)

### Objective

Establish a working, buildable project with all infrastructure plumbing in place, so subsequent phases can focus exclusively on business logic.

### Deliverables

#### 1.1 Maven Project Setup (`pom.xml`)

Configure `pom.xml` with:

- **Parent:** `spring-boot-starter-parent 3.x` (latest 3.x release)
- **Java version:** 21 (`<java.version>21</java.version>`)
- **Core dependencies:**
  - `spring-boot-starter-web` — REST controllers
  - `spring-boot-starter-data-jpa` — JPA repositories
  - `spring-boot-starter-validation` — Bean Validation
  - `spring-boot-starter-actuator` — health and metrics endpoints
  - `spring-boot-starter-webflux` — WebClient for API extractor
  - `postgresql` — JDBC driver
  - `flyway-core` — schema migrations
  - `mapstruct` — compile-time mapping
  - `lombok` — boilerplate reduction
  - `jackson-databind` — JSON serialization
  - `opencsv` — CSV parsing
- **Test dependencies:**
  - `spring-boot-starter-test` — JUnit 5, Mockito, AssertJ
  - `testcontainers:postgresql` — real PostgreSQL in tests
  - `testcontainers:junit-jupiter` — Testcontainers JUnit 5 integration
- **Build plugins:**
  - `maven-compiler-plugin` (Java 21, annotation processing for MapStruct + Lombok)
  - `maven-surefire-plugin` (unit tests)
  - `maven-failsafe-plugin` (integration and e2e tests)

#### 1.2 Docker Compose (`docker-compose.yml`)

Configure a Docker Compose file with:

- **PostgreSQL 15** service:
  - Container name: `orionetl-db`
  - Environment: reads `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` from `.env`
  - Port: `5432:5432`
  - Volume: named volume `postgres_data` for persistence
  - Health check: `pg_isready -U ${POSTGRES_USER}`
- **App service** (commented out by default — used in CI):
  - Depends on `db` health check

#### 1.3 Application Configuration Profiles

Create four YAML files:

**`application.yml`** (base):
```yaml
spring:
  application:
    name: OrionETL
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration
server:
  port: 8080
```

**`application-local.yml`**: override with local DB credentials, `DEBUG` logging for `com.elyares.etl`.

**`application-dev.yml`**: development environment connection strings.

**`application-prod.yml`**: production-grade settings (connection pool tuning, `WARN`-level logging, no stack traces in API responses).

#### 1.4 Flyway Migration: V1 Schema

Create `V1__create_etl_schema.sql` with all seven ETL metadata tables:
- `etl_pipelines`
- `etl_pipeline_executions`
- `etl_execution_steps`
- `etl_execution_errors`
- `etl_rejected_records`
- `etl_audit_records`
- `etl_execution_metrics`

#### 1.5 Logback Configuration (`logback-spring.xml`)

- Console appender (for local/dev): colored output with `[exec=%X{executionId}] [pipeline=%X{pipelineId}] [step=%X{stepName}]` pattern.
- File appender (for prod): JSON-formatted structured logging to `/var/log/orionetl/application.log`.
- Profile-based appender activation.

#### 1.6 Shared Layer

Create all classes in `shared/`:

- **`shared/exception/`**: `EtlException`, `ExtractionException`, `TransformationException`, `LoadingException`, `ValidationException`, `PipelineNotFoundException`, `ExecutionConflictException`, `RetryExhaustedException`.
- **`shared/constants/`**: `StepNames`, `ErrorCodes`, `MetricKeys`.
- **`shared/util/`**: `DateUtils`, `StringUtils`, `JsonUtils`.
- **`shared/logging/`**: `ExecutionMdcContext`, `MdcCleaner`.
- **`shared/response/`**: `ApiResponse<T>`, `ErrorResponse`, `PagedResponse<T>`.

#### 1.7 Domain Model

Create all domain model classes in `domain/model/`:

- `pipeline/`: `Pipeline`, `PipelineVersion`, `ScheduleConfig`, `RetryPolicy`
- `execution/`: `PipelineExecution`, `PipelineExecutionStep`, `ExecutionError`, `ExecutionMetric`
- `source/`: `SourceConfig`, `RawRecord`, `ExtractionResult`
- `target/`: `TargetConfig`, `ProcessedRecord`, `LoadResult`
- `validation/`: `ValidationConfig`, `ValidationResult`, `ValidationError`, `RejectedRecord`, `DataQualityReport`
- `audit/`: `AuditRecord`

Create all enums in `domain/enums/`:

`PipelineStatus`, `ExecutionStatus`, `StepStatus`, `ErrorType`, `ErrorSeverity`, `SourceType`, `TargetType`, `LoadStrategy`, `TriggerType`

Create value objects in `domain/valueobject/`:

`PipelineId`, `ExecutionId`, `RecordCount`, `ErrorThreshold`, `BusinessKey`

Create domain contracts in `domain/contract/`:

`DataExtractor`, `DataTransformer`, `DataLoader`, `DataValidator`, `AuditRepository`, `ExecutionRepository`, `PipelineRepository`, `RejectedRecordRepository`

#### 1.8 Unit Test Structure

Create the base test package structure under `src/test/java/com/elyares/etl/`:
- `unit/` — marker package
- `integration/` — marker package
- `e2e/` — marker package
- `fixtures/SampleDataFactory.java` — test record builders

### Test Coverage Expected

- Unit tests for all value objects (constructor validation, equality, toString).
- Unit tests for all custom exception types (correct message propagation, error codes).
- Unit tests for `DateUtils`, `StringUtils`, `JsonUtils`.

### Acceptance Criteria

- [ ] `mvn clean compile` succeeds with zero errors and zero warnings.
- [ ] `mvn test` passes all unit tests.
- [ ] `docker-compose up -d` starts PostgreSQL successfully.
- [ ] Application starts with `mvn spring-boot:run -Dspring-boot.run.profiles=local`.
- [ ] `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.
- [ ] Flyway migrations apply on startup with no errors.
- [ ] All domain model classes, enums, and value objects exist in the correct packages.
- [ ] No class in `domain/` imports from `org.springframework.*`.

---

## Phase 2: Domain & Application Core (Weeks 3–4)

### Objective

Implement the core business logic: domain services, business rules, all use cases, and the main ETL orchestrator. Everything implemented in this phase should be testable without a database or Spring context.

### Deliverables

#### 2.1 Domain Services

**`ExecutionLifecycleService`:**
- `createExecution(PipelineId, ExecutionRequest)` → `PipelineExecution`
- `markRunning(ExecutionId)` → void
- `markSuccess(ExecutionId, FinalCounts)` → void
- `markFailed(ExecutionId, errorSummary)` → void
- `markPartial(ExecutionId, FinalCounts, errorSummary)` → void
- `closeExecution(ExecutionId)` → void

**`PipelineOrchestrationService`:**
- `validatePreconditions(Pipeline, ExecutionRequest)` → evaluates `NoDuplicateExecutionRule`, `AllowedExecutionWindowRule`, `RetryEligibilityRule`
- `determineExecutionStatus(ExecutionContext)` → `ExecutionStatus`

**`DataQualityService`:**
- `evaluateQuality(long totalRead, long totalRejected, ErrorThreshold)` → `DataQualityReport`
- `isAbortRequired(DataQualityReport, ValidationConfig)` → `boolean`

#### 2.2 Domain Rules

Implement all rule classes in `domain/rules/`:

- `NoDuplicateExecutionRule` — calls `ExecutionRepository.findActiveByPipelineId()`
- `RetryEligibilityRule` — checks `retryCount < maxRetries` AND `errorType in retryOnErrors`
- `ErrorThresholdRule` — `errorRate > threshold → abort`
- `AllowedExecutionWindowRule` — checks current time against `ScheduleConfig.allowedWindows`
- `CriticalErrorBlocksSuccessRule` — checks for any `CRITICAL` errors in execution

#### 2.3 Use Cases

**`application/usecase/pipeline/`:**
- `GetPipelineUseCase` — find pipeline by ID or name
- `ListPipelinesUseCase` — list all active pipelines
- `ResolvePipelineConfigUseCase` — load and validate pipeline config from YAML

**`application/usecase/execution/`:**
- `ExecutePipelineUseCase` — entry point: validates, creates execution, delegates to orchestrator
- `GetExecutionStatusUseCase` — retrieve execution + steps + metrics
- `ListExecutionsUseCase` — list executions by pipeline, date range, status
- `RetryExecutionUseCase` — check eligibility, create retry execution

**`application/usecase/validation/`:**
- `ValidateInputDataUseCase` — orchestrates schema validation step
- `ValidateBusinessDataUseCase` — orchestrates business + quality validation step

**`application/usecase/extraction/`:**
- `ExtractDataUseCase` — resolves extractor, calls extract, records step result

**`application/usecase/transformation/`:**
- `TransformDataUseCase` — invokes transformer chain, collects results

**`application/usecase/loading/`:**
- `LoadProcessedDataUseCase` — stages, validates staging, promotes
- `RegisterAuditUseCase` — builds and persists `AuditRecord`
- `PersistRejectedRecordsUseCase` — persists all accumulated rejected records

#### 2.4 ETLOrchestrator

Implement `ETLOrchestrator` in `application/orchestrator/`:

- Full 8-step execution flow calling use cases in sequence.
- Short-circuit on extraction failure (skip to CLOSE).
- Abort on schema validation threshold exceeded.
- Abort on business validation threshold exceeded.
- `finally` block guarantees AUDIT step always runs.
- MDC context set/cleared per step.
- `PipelineExecutionStep` records created and updated per step.

#### 2.5 DTOs and Mappers

Create all `application/dto/` classes:
- `PipelineDto`, `PipelineExecutionDto`, `ExecutionStepDto`
- `ExecutionRequestDto`, `ExecutionStatusDto`
- `RejectedRecordDto`, `AuditRecordDto`, `ExecutionMetricDto`

Create MapStruct mapper interfaces in `application/mapper/`.

### Test Coverage Expected

- Unit tests for all 5 domain rule classes (success and failure cases).
- Unit tests for all 3 domain services (mock repository interfaces).
- Unit tests for `ETLOrchestrator` using mock use cases (verify step sequencing, abort on failure, audit always runs).
- Unit tests for each use case class (mock dependencies).
- Test coverage target: **80%+ on domain and application packages**.

### Acceptance Criteria

- [ ] All use cases have unit tests with mocked dependencies.
- [ ] `ETLOrchestrator` unit test verifies: audit runs even when step 3 fails.
- [ ] `NoDuplicateExecutionRule` unit test verifies: exception thrown when active execution exists.
- [ ] `ErrorThresholdRule` unit test verifies: abort at exactly `threshold + 0.01%` and allow at exactly `threshold`.
- [ ] No `@Autowired` or Spring annotations in `domain/` classes.
- [ ] All value objects throw `IllegalArgumentException` on invalid construction.

---

## Phase 3: Infrastructure — Persistence (Week 5)

### Objective

Implement the persistence layer: JPA entities, Spring Data JPA repositories, and the adapter classes that bridge the domain repository contracts to JPA.

### Deliverables

#### 3.1 JPA Entities (`infrastructure/persistence/entity/`)

One `@Entity` class per database table, with Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`:

- `EtlPipelineEntity` — maps to `etl_pipelines`
- `EtlPipelineExecutionEntity` — maps to `etl_pipeline_executions`
- `EtlExecutionStepEntity` — maps to `etl_execution_steps`
- `EtlExecutionErrorEntity` — maps to `etl_execution_errors`
- `EtlRejectedRecordEntity` — maps to `etl_rejected_records` (with `@Type(JsonType.class)` for JSONB columns)
- `EtlAuditRecordEntity` — maps to `etl_audit_records`
- `EtlExecutionMetricEntity` — maps to `etl_execution_metrics`

Use `@Column(columnDefinition = "jsonb")` + Hypersistence Utils for JSONB mapping.

#### 3.2 Spring Data JPA Repositories (`infrastructure/persistence/repository/`)

One `JpaRepository<Entity, UUID>` interface per entity. Custom query methods:

- `JpaEtlPipelineExecutionRepository`: `findByPipelineIdAndStatusIn(UUID, List<String>)` for active execution check.
- `JpaEtlRejectedRecordRepository`: `findByExecutionId(UUID)`, `countByExecutionId(UUID)`.
- Custom `@Query` for complex lookups (execution history by date range, etc.).

#### 3.3 Adapter Classes (`infrastructure/persistence/adapter/`)

Four adapter classes implementing domain contracts:

- `PipelineRepositoryAdapter implements PipelineRepository`
- `ExecutionRepositoryAdapter implements ExecutionRepository`
- `AuditRepositoryAdapter implements AuditRepository`
- `RejectedRecordRepositoryAdapter implements RejectedRecordRepository`

Each adapter:
- Injects the JPA repository.
- Converts between domain model and JPA entity using MapStruct mappers.
- Implements all interface methods.
- Handles `Optional.empty()` correctly for not-found scenarios.

#### 3.4 Flyway V2 Migration

Create `V2__add_indexes.sql` with performance indexes for all common query patterns (see [Database Schema documentation](./infrastructure/database-schema.md) for the full index list).

#### 3.5 Spring Configuration

In `config/database/`:
- `DataSourceConfig` — datasource bean from properties.
- `JpaConfig` — entity scan, naming strategy.
- `FlywayConfig` — Flyway bean configuration.

In `config/beans/`:
- Wire `PipelineRepositoryAdapter` as the `PipelineRepository` bean.
- Wire all other adapters.

### Test Coverage Expected

- Integration tests for each adapter class using `@SpringBootTest` + Testcontainers PostgreSQL.
- Test: `ExecutionRepositoryAdapter.findActiveByPipelineId()` returns correct result for RUNNING and RETRYING statuses.
- Test: `RejectedRecordRepositoryAdapter.save()` correctly persists JSONB `raw_data` and `validation_errors`.
- Test: `AuditRepositoryAdapter.save()` persists JSONB `details` and retrieves them correctly.
- Test coverage target: **90%+ on persistence adapters**.

### Acceptance Criteria

- [ ] Integration tests pass with a real Testcontainers PostgreSQL instance.
- [ ] JSONB columns (`raw_data`, `validation_errors`, `config_json`, `details`) are correctly serialized and deserialized.
- [ ] The partial unique index on `etl_pipeline_executions(pipeline_id) WHERE status IN ('RUNNING','RETRYING')` correctly rejects a second active execution insert.
- [ ] All domain contracts are implemented by adapters.
- [ ] No JPA/Hibernate annotations appear in `domain/` classes.

---

## Phase 4: Infrastructure — Extractors (Week 6)

### Objective

Implement the two highest-priority extractors: `CsvExtractor` (for Sales and Inventory pipelines) and `ApiExtractor` (for Customer pipeline). Also implement the `ExtractorRegistry`.

### Deliverables

#### 4.1 ExtractorRegistry

`ExtractorRegistry` bean that:
- Receives all `DataExtractor` beans via constructor injection.
- `resolve(SourceType)` method returns the matching extractor or throws.

#### 4.2 CsvExtractor

Full implementation in `infrastructure/extractor/csv/CsvExtractor.java`:
- OpenCSV-based file reading.
- Configurable delimiter, encoding, quote character.
- Header row handling and `headerMapping`.
- Null value normalization.
- Lazy row iteration (not loading entire file into memory).
- Row number tracking (1-based, accounting for header row).
- `ExtractionException` thrown on IO failures with meaningful messages.

#### 4.3 ApiExtractor

Full implementation in `infrastructure/extractor/api/ApiExtractor.java`:
- Spring WebClient-based HTTP calls (blocking `.block()` for V1 synchronous flow).
- Support for `BEARER`, `BASIC`, and `API_KEY` auth types.
- Cursor-based and offset-based pagination.
- Configurable `responseArrayPath` for extracting the data array from JSON responses.
- Retry on 5xx status codes using WebClient's `retryWhen` operator.
- Timeout per request.
- `ExtractionException` wrapping `WebClientResponseException` for HTTP errors.

#### 4.4 Sample Test Data Files

Create test fixture files in `src/test/resources/fixtures/`:
- `sales_sample.csv` — 100 rows with valid sales data.
- `sales_invalid.csv` — 20 rows with various data quality issues.
- `api_customers_response.json` — mock API response for 50 customer records.

### Test Coverage Expected

- Unit tests for `CsvExtractor`: test with in-memory CSV string (no file system dependency).
- Integration test for `CsvExtractor` with real sample CSV files.
- Unit tests for `ApiExtractor`: mock WebClient using `WireMock` or `MockWebServer`.
- Test: cursor pagination correctly fetches all pages.
- Test: `CsvExtractor` correctly handles `null` values, empty lines, and quoted fields with embedded commas.
- Test: `ApiExtractor` retries on 503 and succeeds on the retry.

### Acceptance Criteria

- [ ] `CsvExtractor` reads `sales_sample.csv` and produces 100 `RawRecord`s with correct field names and values.
- [ ] `CsvExtractor` correctly maps camelCase header names to snake_case via `headerMapping`.
- [ ] `ApiExtractor` paginates correctly: mocked API with 3 pages of 10 records each produces 30 `RawRecord`s.
- [ ] `ApiExtractor` throws `ExtractionException` wrapping `ExternalIntegrationException` on 401.
- [ ] All extractors implement `supports()` correctly.

---

## Phase 5: Infrastructure — Transformers & Validators (Week 7)

### Objective

Implement the transformation and validation infrastructure: `CommonTransformer`, `SchemaValidator`, `BusinessValidator`, and `QualityValidator`.

### Deliverables

#### 5.1 CommonTransformer

Full implementation of all generic transformation rules (TR-001 through TR-010):
- String trimming.
- Column name snake_casing.
- Date parsing and UTC conversion.
- Currency conversion to base currency (`BigDecimal` arithmetic only).
- Null value normalization.
- Numeric type conversion with strict parsing.
- Code/status mapping from config.
- Derived column calculation (arithmetic formulas).
- Monetary rounding per configured policy.

#### 5.2 TransformerChain

`TransformerChain` bean that orders and applies transformers.

#### 5.3 SchemaValidator

Full implementation:
- Mandatory column presence check (file-level and record-level).
- Type compatibility validation for all configured column types.
- String pattern validation (regex).
- Produces `ValidationResult` with per-record `ValidationError` list.

#### 5.4 BusinessValidator

Full implementation:
- Batch bulk-fetch for catalog lookups (single query per field, not per record).
- Business key uniqueness check within batch.
- Range checks.
- Cross-field constraint validation.
- Future date validation.
- Active reference checks.

#### 5.5 QualityValidator

Full implementation:
- Error rate calculation.
- Threshold comparison.
- `DataQualityReport` generation with error breakdown by rule, field, and step.
- Abort decision.

#### 5.6 ValidationChainExecutor

Application-layer coordinator that calls schema validation, then business + quality validation, collecting all rejections.

### Test Coverage Expected

- Unit tests for `CommonTransformer`:
  - Test each transformation rule in isolation.
  - Test that BigDecimal is used for monetary arithmetic (no double arithmetic).
  - Test currency conversion with known rates.
  - Test derived column calculation (subtotal, tax, total).
- Unit tests for `SchemaValidator`:
  - Test missing mandatory column (file-level) → abort.
  - Test null mandatory field → record rejection.
  - Test invalid date format → record rejection.
  - Test invalid numeric → record rejection.
- Unit tests for `BusinessValidator`:
  - Test catalog lookup miss → record rejection.
  - Test duplicate business key → rejection (first or all, per policy).
  - Test range violation → record rejection.
- Unit tests for `QualityValidator`:
  - Test: 5% threshold, 6% error rate → abort = true.
  - Test: 5% threshold, 4.9% error rate → abort = false.
- Test coverage target: **90%+ on transformer and validator classes**.

### Acceptance Criteria

- [ ] `CommonTransformer` converts `"EUR"` amount to `"USD"` correctly using `BigDecimal` with no floating-point errors.
- [ ] `SchemaValidator` rejects 100% of records when a mandatory column is absent from the CSV header.
- [ ] `BusinessValidator` performs catalog lookups in bulk (one DB query for all `product_id` values, not one per record).
- [ ] `QualityValidator` returns `abort=true` when error rate is 1 record above the threshold.
- [ ] All validators return `ValidationResult` with correct `isValid`, `invalidCount`, and `errorRate` values.

---

## Phase 6: Infrastructure — Loaders (Week 8)

### Objective

Implement the three-phase loading system: staging load, staging validation, and final promotion. Also implement `RejectedRecordPersister`.

### Deliverables

#### 6.1 RejectedRecordPersister

`RejectedRecordPersister` in `infrastructure/loader/database/`:
- Converts `List<RejectedRecord>` to `List<EtlRejectedRecordEntity>`.
- Saves in a single batch insert (not one by one).
- Called first in the LOAD step, before staging load begins.

#### 6.2 StagingLoader

Full implementation:
- Clears staging table for current pipeline at start of each execution.
- Loads `ProcessedRecord`s to staging in configurable chunks.
- Each chunk is a separate database transaction.
- Failed chunks are recorded in `etl_execution_errors` with full detail.
- `failFastOnChunkError` behavior respected.
- Injects `etl_execution_id`, `etl_load_timestamp`, `etl_source_file` traceability columns.

#### 6.3 StagingValidator

Full implementation:
- Row count verification.
- Mandatory column null check on staging.
- Business key uniqueness in staging (for UPSERT).
- Returns `StagingValidationResult` with pass/fail and detail.

#### 6.4 FinalLoader

Full implementation supporting all three load strategies:
- `INSERT`: `INSERT INTO final SELECT * FROM staging WHERE execution_id = ?`
- `UPSERT`: `INSERT ... ON CONFLICT ({businessKey}) DO UPDATE SET ...`
- `REPLACE`: scoped DELETE + INSERT
- All in a single transaction.
- Closed record guard implementation (skips update for `status = 'CLOSED'` records).
- Rollback strategy execution on critical failure: `DELETE FROM final WHERE etl_execution_id = ?`

### Test Coverage Expected

- Integration tests using Testcontainers for all loaders (real PostgreSQL).
- Test: `StagingLoader` commits chunk 1, fails on chunk 2 → only chunk 2 records absent from staging.
- Test: `StagingValidator` fails on row count mismatch → `FinalLoader.promote()` is NOT called.
- Test: `FinalLoader` UPSERT correctly updates existing records and inserts new ones.
- Test: `FinalLoader` `DELETE_BY_EXECUTION` rollback removes exactly the records from this execution.
- Test: `RejectedRecordPersister` persists JSONB correctly for querying.

### Acceptance Criteria

- [ ] `RejectedRecordPersister` persists all rejected records before staging load begins.
- [ ] If `StagingValidator` fails, `FinalLoader.promote()` is never called (verified via test).
- [ ] UPSERT strategy: record with existing business key is updated, not duplicated.
- [ ] Closed record guard: records with `status = "CLOSED"` are skipped in UPSERT updates.
- [ ] Rollback: after a `DELETE_BY_EXECUTION` rollback, no records from this execution remain in the final table.
- [ ] Integration tests pass against a real Testcontainers PostgreSQL.

---

## Phase 7: First Pipeline — Sales (Week 9)

### Objective

Implement and validate the complete Sales pipeline end-to-end: CSV → transform → validate → load staging → promote to final table. This is the first fully functional pipeline demonstrating the complete engine.

### Deliverables

#### 7.1 SalesTransformer

Full implementation in `infrastructure/transformer/sales/SalesTransformer.java`:
- Quantity and discount rate defaulting.
- Channel code mapping.
- Subtotal, tax, and total calculation (all `BigDecimal`, rounded to 2 decimal places).
- Future date leniency for PARTNER channel.

#### 7.2 Sales-Specific Business Rules

Implement rule configurations for the sales pipeline in `BusinessValidator`:
- `amount >= 0` (CRITICAL)
- `product_id` in active products catalog
- `customer_id` in customers table
- `sale_date <= today` (with PARTNER exception)
- `salesperson_id` active in salespeople catalog
- `discount_rate` in `[0, 100]`

#### 7.3 `sales.yml` Pipeline Configuration

Create `src/main/resources/pipelines/sales.yml` with the full pipeline config (source, target, validation, transformation, retry, schedule — as defined in [Sales Pipeline documentation](./pipelines/sales-pipeline.md)).

#### 7.4 SalesPipelineConfig

`pipelines/sales/SalesPipelineConfig.java`:
- Loads and parses `sales.yml`.
- Registers the sales pipeline in `etl_pipelines` on application startup (if not already registered).
- Assembles the transformer chain for sales.

#### 7.5 Database Migrations for Sales Tables

Create staging and final tables:
- `V8__create_sales_transactions_table.sql` — `sales_transactions` final table with all columns.
- `V9__create_sales_staging_table.sql` — `sales_transactions_staging` table.

### Test Coverage Expected

- Unit tests for `SalesTransformer`:
  - Subtotal calculation with discount.
  - Tax calculation at 19%.
  - Total amount.
  - Channel mapping (all valid codes).
  - Rejection for unmapped channel code.
- End-to-end test `SalesPipelineE2ETest`:
  - Given: `sales_sample.csv` (100 valid records + 5 invalid records).
  - When: Execute `sales-daily` pipeline via `ETLOrchestrator`.
  - Then:
    - `etl_pipeline_executions.status = 'PARTIAL'` (or SUCCESS if 5 < 5% threshold).
    - `etl_pipeline_executions.total_read = 105`.
    - `etl_pipeline_executions.total_loaded` = count of valid records.
    - `etl_rejected_records` has 5 rows with the expected `rejection_reason`.
    - `sales_transactions` table has the correct number of rows.
    - `etl_audit_records` has 1 row for this execution.

### Acceptance Criteria

- [ ] Sales pipeline end-to-end test passes with Testcontainers PostgreSQL.
- [ ] `SalesTransformer` correctly calculates `subtotal`, `tax_amount`, `total_amount` to 2 decimal places.
- [ ] All 5 invalid test records end up in `etl_rejected_records` with correct `rejection_reason`.
- [ ] `etl_audit_records` row exists with correct `total_read`, `total_loaded`, `total_rejected`.
- [ ] No records in `sales_transactions` table from a failed execution after rollback.
- [ ] `SalesTransformer` unit tests pass with 100% coverage on calculation methods.

---

## Phase 8: REST Interface & Monitoring (Week 10)

### Objective

Expose the ETL engine via a REST API, add health monitoring, and provide execution status polling endpoints.

### Deliverables

#### 8.1 PipelineController

`interfaces/rest/controller/PipelineController.java`:
- `GET /api/v1/pipelines` — list all active pipelines.
- `GET /api/v1/pipelines/{pipelineId}` — get pipeline detail.
- `POST /api/v1/pipelines/{pipelineId}/execute` — trigger execution.
  - Returns `202 Accepted` with `executionId` and initial status.
  - Body: `ExecutionRequestDto` (triggeredBy, parameters).
  - Validates request with `@Valid` Bean Validation.

#### 8.2 ExecutionController

`interfaces/rest/controller/ExecutionController.java`:
- `GET /api/v1/executions/{executionId}` — full execution status with steps.
- `GET /api/v1/executions/{executionId}/metrics` — execution metrics.
- `GET /api/v1/executions/{executionId}/rejected` — rejected records (paginated).
- `GET /api/v1/pipelines/{pipelineId}/executions` — execution history for a pipeline.

#### 8.3 Global Exception Handler

`@RestControllerAdvice` class:
- Maps `PipelineNotFoundException` → 404 with `ErrorResponse`.
- Maps `ExecutionConflictException` → 409 with `ErrorResponse`.
- Maps `ValidationException` (Bean Validation) → 400 with field-level error details.
- Maps all other `EtlException` subtypes → 500 with `ErrorResponse` (no stack trace in response).

#### 8.4 Spring Actuator Configuration

In `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, loggers
  endpoint:
    health:
      show-details: when-authorized
```

Custom `ETLEngineHealthIndicator` that reports:
- Number of active executions.
- Number of registered pipelines.
- Last successful execution timestamp per pipeline.

### Test Coverage Expected

- `@WebMvcTest` unit tests for `PipelineController`:
  - Test `POST /execute` returns 202 with correct executionId.
  - Test `POST /execute` returns 409 when duplicate execution exists.
  - Test `POST /execute` returns 404 for unknown pipelineId.
- `@WebMvcTest` unit tests for `ExecutionController`:
  - Test `GET /executions/{id}` returns full status with steps.
  - Test `GET /executions/{id}/rejected` returns paginated results.

### Acceptance Criteria

- [ ] `POST /api/v1/pipelines/sales-daily/execute` returns `202 Accepted` with an `executionId`.
- [ ] Polling `GET /api/v1/executions/{executionId}` returns correct step statuses.
- [ ] `GET /api/v1/executions/{executionId}/rejected` returns paginated rejected records.
- [ ] `GET /actuator/health` returns `UP` with custom ETL engine component.
- [ ] `422` or `400` returned for missing required request fields (Bean Validation working).
- [ ] No Java stack traces leak in REST error responses.

---

## Phase 9: Second & Third Pipelines (Week 11)

### Objective

Implement the Inventory and Customer pipelines, adding `ExcelExtractor` and `CustomerTransformer` along the way.

### Deliverables

#### 9.1 ExcelExtractor

Full implementation in `infrastructure/extractor/excel/ExcelExtractor.java`:
- Apache POI for `.xlsx` (XSSF) support.
- Sheet selection by name or index.
- Header row detection.
- Cell type handling (numeric, string, boolean, date, formula evaluation).
- Excel date cell detection using `DateUtil.isCellDateFormatted()`.

#### 9.2 InventoryTransformer

Full implementation:
- SKU normalization (uppercase, trim, space-to-hyphen).
- Warehouse code translation via catalog.
- Quantity consolidation per `(sku, warehouse_id)`.

#### 9.3 CustomerTransformer

Full implementation:
- Name title-casing with apostrophe and hyphen handling.
- Email lowercase normalization.
- Phone E.164 normalization (using `libphonenumber`).
- Country code ISO normalization.
- Status code mapping.

#### 9.4 Pipeline Configurations

- `inventory.yml` pipeline config.
- `customer.yml` pipeline config (note: placed outside `resources/pipelines/` per project convention, or added there).
- `InventoryPipelineConfig.java` and `CustomerPipelineConfig.java` wiring classes.

#### 9.5 Database Migrations for Inventory and Customer Tables

- `V10__create_inventory_tables.sql` — `inventory_levels` + `inventory_levels_staging`.
- `V11__create_customer_tables.sql` — `customers` + `customers_staging`.

### Test Coverage Expected

- Unit tests for `ExcelExtractor` with sample `.xlsx` test files.
- Unit tests for `InventoryTransformer`: SKU normalization cases, consolidation logic.
- Unit tests for `CustomerTransformer`: name casing, E.164 phone normalization, country code resolution.
- E2E test for Inventory pipeline: Excel file → consolidation → upsert.
- E2E test for Customer pipeline: JSON (mocked API) → full normalization → upsert with closed-record guard.

### Acceptance Criteria

- [ ] Inventory E2E test: duplicate `(sku, warehouse_id)` records are consolidated correctly.
- [ ] Customer E2E test: phone `"3001234567"` (Colombia context) normalized to `"+573001234567"`.
- [ ] Customer E2E test: `status = "CLOSED"` customer is NOT updated by the UPSERT.
- [ ] All three pipelines have passing E2E tests.
- [ ] `ExcelExtractor` correctly reads date cells as `LocalDate`, not as numbers.

---

## Phase 10: Hardening & V2 Preparation (Week 12)

### Objective

Finalize V1 with production-readiness improvements: retry mechanism, notifications, Docker build, and documentation for V2 roadmap.

### Deliverables

#### 10.1 Retry Mechanism

Implement automatic retry in `ExecutePipelineUseCase` and `RetryExecutionUseCase`:
- After a FAILED execution: check `RetryEligibilityRule`.
- If eligible: create a new execution with `triggerType = RETRY` and `parentExecutionId` pointing to the failed execution.
- Wait `retryDelayMs` before re-triggering (using `Thread.sleep()` in V1, or a `@Scheduled` check in a more sophisticated approach).
- Increment `retryCount` on the new execution.
- On `RetryExhaustedException`: mark final execution `FAILED` permanently, emit critical alert.

#### 10.2 Notification Hooks

Implement `LogNotificationService` in `infrastructure/notification/`:
- `notifyFailure(PipelineExecution, ExecutionError)` → structured ERROR log with full context.
- `notifySuccess(PipelineExecution)` → INFO log with final counts.
- `notifyPartial(PipelineExecution, long rejectedCount)` → WARN log.
- Injected into `ETLOrchestrator` and called in the CLOSE step.

Stub `WebhookNotificationService` for V2: interface defined but implementation returns immediately.

#### 10.3 DatabaseExtractor (Bonus if time permits)

Implement `DatabaseExtractor` using Spring JDBC:
- Custom DataSource from connection details in `SourceConfig`.
- `fetchSize` hint for cursor-based reading.
- Named parameter binding.

#### 10.4 Dockerfile

Multi-stage build:

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 10.5 Root README.md

Create the project root `README.md` with:
- Project description.
- Quick start (clone, `.env`, `docker-compose up`, `mvn spring-boot:run`).
- API overview.
- Link to `docs/README.md` for full documentation.
- Build and test instructions.
- Contributing guidelines.

#### 10.6 V2 Roadmap Documentation

Update `docs/README.md` V2 section with finalized scope based on V1 learnings.

Create `docs/architecture/v2-roadmap.md` with:
- Spring Batch migration plan (referencing ADR-002).
- Scheduler implementation design.
- Monitoring dashboard wireframes (described in text).
- ExcelExtractor and DatabaseExtractor completion notes.
- Dead-letter queue design.

### Test Coverage Expected

- Unit test for retry mechanism: verify `retryCount` increments on each retry.
- Unit test: `RetryEligibilityRule` returns false when `retryCount >= maxRetries`.
- Unit test: `RetryEligibilityRule` returns false for `DATA_QUALITY` errors.
- Integration test: failed execution → automatic retry → second execution created with `triggerType = RETRY`.
- Final E2E test pass: all three pipelines succeed with valid sample data.

### Acceptance Criteria

- [ ] `mvn clean verify` (all tests) passes with zero failures.
- [ ] `docker build -t orionetl:v1 .` succeeds.
- [ ] `docker-compose up` starts both the app and the database.
- [ ] `GET /actuator/health` returns `UP` in the Docker container.
- [ ] Retry mechanism: a FAILED pipeline with `maxRetries: 2` creates a second execution after failure.
- [ ] `RetryExhaustedException` is emitted and logged as ERROR after `maxRetries` is exceeded.
- [ ] All pipeline E2E tests pass in CI (GitHub Actions or equivalent).
- [ ] `docs/README.md` contains accurate V2 roadmap.
- [ ] Code coverage report shows **80%+ overall** for `domain/` and `application/` packages.

---

## Summary: Definition of Done for V1

V1 is considered **complete** when all of the following are true:

- [ ] All 10 phases have passed their acceptance criteria.
- [ ] `mvn clean verify` passes with zero test failures.
- [ ] Three pipelines (Sales, Inventory, Customer) have passing E2E tests.
- [ ] Docker image builds and runs successfully.
- [ ] All 22 documentation files in `docs/` are written and accurate.
- [ ] No class in `domain/` has Spring framework imports.
- [ ] `GET /actuator/health` returns `UP`.
- [ ] Rejected records for every test execution are persisted in `etl_rejected_records` with full detail.
- [ ] All ADRs reflect final architectural decisions.
- [ ] V2 roadmap is documented.
