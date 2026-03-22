# Architecture Overview

## Architecture Philosophy

OrionETL is built on **Hexagonal Architecture** (also known as Ports and Adapters), complemented by principles from **Clean Architecture**. The guiding principle is that the core business logic — the domain model, the execution rules, the transformation contracts — must remain completely independent of frameworks, databases, file formats, and HTTP libraries.

This means:

- The **domain** defines what an ETL engine must do: extract, validate, transform, load, audit.
- The **infrastructure** provides concrete implementations of how those things are done: via CSV files, REST APIs, PostgreSQL tables, etc.
- The **application layer** coordinates the use cases that fulfill a business goal (e.g., "execute a pipeline") by calling domain services and infrastructure adapters.
- The **interfaces layer** is the entry point from the outside world: REST controllers, CLI commands, scheduled triggers.

This separation ensures that:

1. Business rules can be tested without a database, a file system, or an HTTP server.
2. A new data source (e.g., a Parquet file extractor) can be added without touching any domain or business logic code.
3. The system can evolve — e.g., migrate from manual orchestration to Spring Batch — without rewriting the core.

---

## Architectural Layers

### 1. `interfaces/` — Entry Points

The outermost layer. Handles all inbound triggers:

- **REST controllers** (`interfaces/rest/controller/`) — Receive HTTP requests, delegate to application use cases or facades, return HTTP responses.
- **CLI handlers** (`interfaces/cli/`) — Command-line triggers for manual or scripted execution.
- **Scheduler triggers** (`interfaces/scheduler/`) — Scheduled job triggers (V2: cron-driven).

Controllers and entry points are thin. They perform request deserialization, basic input validation (Bean Validation), and delegate immediately to the application layer. **No business logic belongs here.**

### 2. `application/` — Use Cases and Orchestration

The application layer coordinates work. It knows the "what" but delegates the "how" to the domain and infrastructure layers.

- **Use cases** (`application/usecase/`) — Each use case represents a single business operation: `ExecutePipelineUseCase`, `ValidateInputDataUseCase`, `TransformDataUseCase`, `LoadProcessedDataUseCase`, `RegisterAuditUseCase`.
- **Orchestrator** (`application/orchestrator/`) — `ETLOrchestrator` drives the full 8-step execution flow, calling use cases in sequence and managing the execution lifecycle.
- **Facade** (`application/facade/`) — Simplifies interaction for entry points that need to combine multiple use cases.
- **DTOs** (`application/dto/`) — Data Transfer Objects used at the application boundary (not domain models).
- **Mappers** (`application/mapper/`) — MapStruct mappers that translate between DTOs, domain models, and infrastructure entities.

### 3. `domain/` — Core Business Logic

The heart of the system. Has **zero Spring dependencies**. No `@Component`, no `@Service`, no `@Repository` annotations inside `domain/`.

- **Model** (`domain/model/`) — Rich domain entities: `Pipeline`, `PipelineExecution`, `RawRecord`, `ProcessedRecord`, `RejectedRecord`, `AuditRecord`, etc.
- **Enums** (`domain/enums/`) — All domain enumerations: `ExecutionStatus`, `ErrorType`, `SourceType`, etc.
- **Value Objects** (`domain/valueobject/`) — Immutable, self-validating values: `PipelineId`, `ExecutionId`, `RecordCount`, `ErrorThreshold`.
- **Domain Services** (`domain/service/`) — Pure business logic that doesn't naturally fit a single entity: `PipelineOrchestrationService`, `ExecutionLifecycleService`, `DataQualityService`.
- **Rules** (`domain/rules/`) — Encapsulated, named business rule objects: `NoDuplicateExecutionRule`, `RetryEligibilityRule`, `ErrorThresholdRule`.
- **Contracts** (`domain/contract/`) — Port interfaces (the "ports" in hexagonal): `DataExtractor`, `DataTransformer`, `DataLoader`, `DataValidator`, `AuditRepository`, `ExecutionRepository`, `PipelineRepository`.
- **Repository interfaces** (`domain/repository/`) — Domain-level repository contracts (implemented by infrastructure adapters).

### 4. `infrastructure/` — Adapters

Concrete implementations of the domain contracts. Each adapter knows about a specific technology.

- **Persistence** (`infrastructure/persistence/`) — JPA entities, Spring Data JPA repositories, adapter classes that implement domain repository interfaces.
- **Extractors** (`infrastructure/extractor/`) — `CsvExtractor`, `ExcelExtractor`, `JsonExtractor`, `ApiExtractor`, `DatabaseExtractor` — all implementing `DataExtractor`.
- **Transformers** (`infrastructure/transformer/`) — `CommonTransformer`, `SalesTransformer`, `InventoryTransformer`, `CustomerTransformer` — all implementing `DataTransformer`.
- **Loaders** (`infrastructure/loader/`) — `StagingLoader`, `FinalLoader`, `RejectedRecordPersister` — all implementing `DataLoader`.
- **Validators** (`infrastructure/validator/`) — `SchemaValidator`, `BusinessValidator`, `QualityValidator` — all implementing `DataValidator`.
- **Scheduler** (`infrastructure/scheduler/`) — Spring `@Scheduled` beans (V2 integration point).
- **Batch** (`infrastructure/batch/`) — Spring Batch job/step definitions (V2).
- **Notification** (`infrastructure/notification/`) — Alert hooks (log-based in V1, webhook in V2).
- **Monitoring** (`infrastructure/monitoring/`) — Metrics collection and Spring Actuator integration.

### 5. `shared/` — Cross-Cutting Concerns

Utilities and structures that are used across all layers but belong to none:

- **Exception** (`shared/exception/`) — Custom exception hierarchy: `EtlException`, `ExtractionException`, `TransformationException`, `LoadingException`, `ValidationException`, `PipelineNotFoundException`.
- **Constants** (`shared/constants/`) — Application-wide constants: step names, error codes, metric keys.
- **Util** (`shared/util/`) — Pure utility classes: `DateUtils`, `StringUtils`, `JsonUtils`.
- **Logging** (`shared/logging/`) — MDC context management for structured logging (execution ID, pipeline ID).
- **Response** (`shared/response/`) — Standard API response wrapper: `ApiResponse<T>`, `ErrorResponse`, `PagedResponse`.

### 6. `config/` — Spring Configuration

All Spring `@Configuration` classes, isolated from business logic:

- **Database** (`config/database/`) — DataSource, JPA, transaction manager configuration.
- **Batch** (`config/batch/`) — Spring Batch configuration (V2).
- **Scheduler** (`config/scheduler/`) — Thread pool and scheduler configuration.
- **Properties** (`config/properties/`) — `@ConfigurationProperties` classes that bind YAML config.
- **Beans** (`config/beans/`) — Bean factory methods for shared infrastructure objects.

### 7. `pipelines/` — Pipeline Definitions

Pipeline-specific orchestration classes that wire together the correct extractor, transformer, validator, and loader for a given pipeline. Each sub-package (e.g., `pipelines/sales/`) contains the pipeline's specific configuration loader and any pipeline-specific overrides.

---

## Layer Dependency Rules

These rules are **strictly enforced** and must not be violated:

```
interfaces → application → domain ← infrastructure
```

| Rule | Explanation |
|---|---|
| `domain` has NO Spring dependencies | Domain classes are plain Java. No `@Component`, `@Service`, `@Autowired`, `@Transactional`. |
| `domain` does not depend on `infrastructure` | Domain defines contracts (interfaces). Infrastructure implements them. |
| `application` depends on `domain`, not `infrastructure` | Use cases call domain contracts, not concrete adapters. |
| `interfaces` depends on `application`, not `domain` directly | Controllers call use cases or facades, not domain services directly. |
| `infrastructure` implements `domain` contracts | Adapters are wired via dependency injection in `config/`. |
| `shared` has no dependencies on other layers | Shared utilities are used by everyone but depend on nothing. |

---

## ETL Execution Flow

The `ETLOrchestrator` drives the following 8-step execution flow for every pipeline run:

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ETL EXECUTION FLOW                           │
│                                                                     │
│  [1] INIT ──► [2] EXTRACT ──► [3] VALIDATE_SCHEMA                  │
│                                         │                           │
│                                    valid? ──NO──► ABORT (FAILED)    │
│                                         │ YES                       │
│                                    [4] TRANSFORM                    │
│                                         │                           │
│                                    [5] VALIDATE_BUSINESS            │
│                                         │                           │
│                              error% > threshold? ──YES──► ABORT     │
│                                         │ NO                        │
│                                    [6] LOAD (staging → final)       │
│                                         │                           │
│                                    [7] CLOSE                        │
│                                         │                           │
│                                    [8] AUDIT                        │
└─────────────────────────────────────────────────────────────────────┘
```

| Step | Name | What Happens |
|---|---|---|
| 1 | **INIT** | Load pipeline config, validate parameters, create `PipelineExecution` record, set status to `RUNNING`. |
| 2 | **EXTRACT** | Call the appropriate `DataExtractor`, read raw records, record `totalRead`, store `ExtractionResult`. |
| 3 | **VALIDATE_SCHEMA** | Check mandatory columns, data types, basic format. Register `ValidationError`s. If critical schema errors exceed threshold, abort. |
| 4 | **TRANSFORM** | Apply all configured `DataTransformer`s in chain order. Produce `ProcessedRecord`s. Structurally invalid records become `RejectedRecord`s. |
| 5 | **VALIDATE_BUSINESS** | Apply business rules: catalog lookups, uniqueness, range checks. If `errorRate > threshold`, abort entire execution. |
| 6 | **LOAD** | Write to staging first. Validate staging. If staging validation passes, promote to final destination using the configured `LoadStrategy`. |
| 7 | **CLOSE** | Record execution metrics. Update `PipelineExecution` status to `SUCCESS`, `FAILED`, or `PARTIAL`. Emit alert notifications if needed. |
| 8 | **AUDIT** | Persist full `AuditRecord`: total read, transformed, rejected, loaded, duration, triggeredBy, step-level detail. |

---

## Architectural Rules

The following ten rules govern the design of OrionETL and must be respected when adding or modifying code:

**Rule 1 — Domain Purity**
The `domain` package must not import any class from Spring Framework, Spring Data, Hibernate, Jackson, or any other framework. The domain is pure Java 21. If a domain service needs persistence, it calls a repository *interface* defined in `domain/contract/` or `domain/repository/`.

**Rule 2 — No Business Logic in Controllers**
REST controllers (`interfaces/rest/controller/`) perform exactly three things: deserialize the request, invoke a use case or facade, and serialize the response. No branching logic, no data manipulation, no direct database calls.

**Rule 3 — No Business Logic in Repositories**
JPA repositories (`infrastructure/persistence/repository/`) contain only query methods. No business logic, no transformation, no validation. Complex queries use `@Query` or `Specification` patterns. Anything beyond a query goes in a domain service or use case.

**Rule 4 — Use Cases Are Single-Responsibility**
Each use case class has a single public method representing a single business operation. Use cases are named with a verb: `ExecutePipelineUseCase`, `RegisterAuditUseCase`. They do not call other use cases directly; orchestration is handled by `ETLOrchestrator` or a facade.

**Rule 5 — Rejected Records Are Never Discarded**
Any record that fails schema validation, transformation, or business validation must be persisted as a `RejectedRecord` with: the raw data, the step where it failed, all `ValidationError`s, and the batch reference. The application must never silently drop records.

**Rule 6 — Staging Before Final Load**
No pipeline may write directly to a final destination table without first loading to a staging area, validating staging, and explicitly promoting. The `StagingValidator` must pass before `FinalLoader` is invoked.

**Rule 7 — Execution Is Atomic at the Step Level**
Each step's success or failure is independently recorded via `PipelineExecutionStep`. If the process crashes mid-execution, the partial state is recoverable from the step records. In V2, Spring Batch will provide formal checkpoint/restart.

**Rule 8 — Audit Is Always Last and Always Persists**
The `AUDIT` step runs even if earlier steps failed. The audit record captures the final state of the execution — including failures, partial loads, and rejection counts. An execution without an audit record is considered incomplete.

**Rule 9 — Configuration Drives Behavior**
Pipeline behavior (source type, schema, transformations, business rules, error thresholds, retry policy, load strategy) is driven by YAML configuration files in `resources/pipelines/`. Adding a new pipeline should not require writing new Java code for standard operations.

**Rule 10 — Testability Is a First-Class Concern**
Domain logic must be unit-testable without any Spring context. Infrastructure adapters must be integration-testable with Testcontainers. End-to-end tests must verify the complete 8-step flow with real (containerized) dependencies.

---

## Package Structure (Top Level)

```
com.elyares.etl/
├── EtlApplication.java          # Spring Boot entry point
├── shared/                      # Cross-cutting utilities, exceptions, response wrappers
├── config/                      # All Spring @Configuration classes
├── domain/                      # Pure Java domain model (no Spring)
├── application/                 # Use cases, orchestrator, DTOs, mappers, facades
├── infrastructure/              # Adapters: persistence, extractors, transformers, loaders, validators
├── interfaces/                  # Entry points: REST, CLI, scheduler
└── pipelines/                   # Pipeline-specific wiring and config loading
```

Each top-level package represents a distinct architectural layer with clear responsibilities and enforced dependency directions.
