# OrionETL — Enterprise ETL Engine Documentation

## Project Overview

**OrionETL** is an enterprise-grade Extract, Transform, Load (ETL) engine built with **Java 21** and **Spring Boot 3.x**. It is designed to serve as the data integration backbone for organizations that need reliable, auditable, and configurable pipelines to move, transform, and validate data across heterogeneous systems.

OrionETL treats each ETL job as a first-class, observable process — every execution is tracked, every record is traceable, every failure is classified and stored. The engine supports multiple source types (CSV, Excel, JSON, REST API, Database) and multiple target destinations, all orchestrated through a declarative pipeline configuration model.

---

## What OrionETL Does

When a pipeline is triggered (via REST API, CLI, or scheduler), OrionETL executes the following sequence:

| Step | Name | Description |
|------|------|-------------|
| 1 | **Receive / Select Pipeline** | Receives a pipeline identifier and optional parameters. Resolves the active pipeline configuration. |
| 2 | **Load Configuration** | Loads the full pipeline config: source, target, transformation rules, validation rules, retry policy, schedule config. |
| 3 | **Extract Data** | Calls the appropriate DataExtractor (CSV, Excel, JSON, API, Database) and reads raw records into memory/cursor. |
| 4 | **Validate Structure (Schema)** | Validates that extracted records have the expected columns, types, and mandatory fields. Rejects structurally invalid records. |
| 5 | **Transform** | Applies all configured transformations: normalization, type conversion, enrichment, column mapping, derived field calculation. |
| 6 | **Validate Quality (Business)** | Applies business rules to transformed records: catalog lookups, uniqueness checks, range validations. Rejects records that fail. |
| 7 | **Load to Destination** | Writes validated records to staging, validates staging, then promotes to final destination table using the configured load strategy. |
| 8 | **Register Full Audit** | Persists a complete AuditRecord with counts (read, transformed, rejected, loaded), duration, actor, and step-level detail. |
| 9 | **Record Final Job State** | Marks the execution as SUCCESS, FAILED, or PARTIAL. Emits alerts if configured. Updates execution record in the database. |

---

## Technology Stack

| Category | Technology | Purpose |
|---|---|---|
| Language | Java 21 | Virtual threads, records, sealed classes, pattern matching |
| Framework | Spring Boot 3.x | Application container, auto-configuration, profiles |
| Batch Processing | Spring Batch (V2) | Job/step model, chunk processing, restart-from-checkpoint |
| Persistence | Spring Data JPA | Repository abstraction, entity management |
| Database | PostgreSQL 15+ | Primary datastore for metadata, audit, rejected records |
| Migrations | Flyway | Version-controlled schema migrations |
| Mapping | MapStruct | Compile-time entity ↔ DTO ↔ domain object mapping |
| Boilerplate Reduction | Lombok | `@Builder`, `@Data`, `@Slf4j` annotations |
| Validation | Bean Validation (Jakarta) | `@NotNull`, `@NotBlank`, `@Valid` on DTOs and requests |
| JSON | Jackson | Serialization/deserialization, JSONB column mapping |
| Logging | Logback / SLF4J | Structured logging, profile-based appenders |
| Testing | JUnit 5 + Testcontainers | Unit and integration tests with real DB containers |
| Containerization | Docker / Docker Compose | Local development and deployment |
| HTTP Client | Spring WebClient | Reactive HTTP client for API extractors |
| CSV | OpenCSV / Apache Commons CSV | CSV file parsing |
| Excel | Apache POI | `.xlsx` / `.xls` file parsing |

---

## Documentation Index

### Architecture
- [Architecture Overview](./architecture/overview.md) — Philosophy, layers, dependency rules, execution flow
- [Current Architecture Context](./architecture/current-architecture-context.md) — Guía rápida del estado real, diagramas y mapa de clases para editar rápido
- [Project Structure](./architecture/project-structure.md) — Full Maven directory tree with explanations
- [Domain Model](./architecture/domain-model.md) — Entities, value objects, domain services, contracts, enums
- [Execution Flow](./architecture/execution-flow.md) — Detailed step-by-step ETL execution with error handling

### Business Rules
- [Execution Rules](./business-rules/execution-rules.md) — Rules governing when and how pipelines run
- [Data Quality Rules](./business-rules/data-quality-rules.md) — Rules for validating data integrity
- [Transformation Rules](./business-rules/transformation-rules.md) — Rules for transforming raw data
- [Loading Rules](./business-rules/loading-rules.md) — Rules for persisting data safely

### Pipelines
- [Sales Pipeline](./pipelines/sales-pipeline.md) — Sales transaction processing specification
- [Inventory Pipeline](./pipelines/inventory-pipeline.md) — Warehouse inventory sync specification
- [Customer Pipeline](./pipelines/customer-pipeline.md) — CRM customer data sync specification

### Infrastructure
- [Database Schema](./infrastructure/database-schema.md) — Table definitions, Flyway strategy
- [Extractors](./infrastructure/extractors.md) — CSV, Excel, JSON, API, Database extractor documentation
- [Transformers](./infrastructure/transformers.md) — Transformer chain, common and pipeline-specific transformers
- [Validators](./infrastructure/validators.md) — Schema, business, and quality validators

### Architecture Decisions (ADRs)
- [ADR-001: Hexagonal Architecture](./decisions/ADR-001-hexagonal-architecture.md)
- [ADR-002: Spring Batch](./decisions/ADR-002-spring-batch.md)
- [ADR-003: Staging Load Strategy](./decisions/ADR-003-staging-strategy.md)

### Runbooks
- [Running a Pipeline](./runbooks/running-a-pipeline.md) — How to trigger, monitor, and debug executions
- [Entender OrionETL](./runbooks/understanding-orionetl.md) — Explicación simple del flujo completo, estado actual, entradas y salidas
- [Using Phase 4 Extractors](./runbooks/using-phase4-extractors.md) — Cómo trabajar, validar y visualizar CSV/API extractors
- [Local Setup](./runbooks/local-setup.md) — Developer environment setup guide

### Planning
- [Action Plan](./action-plan.md) — Phased implementation roadmap (10 phases, 12 weeks)
- [Bitácora Fase 4](./bitacora-fase4.md) — Extractores CSV y API completados
- [Bitácora Fase 5](./bitacora-fase5.md) — Transformadores y validadores base implementados
- [Bitácora Fase 6](./bitacora-fase6.md) — Loaders JDBC, staging validation y promoción a final
- [Bitácora Fase 7](./bitacora-fase7.md) — Primer pipeline end-to-end real: Sales
- [Bitácora Fase 8](./bitacora-fase8.md) — REST API, monitoreo y health indicator
- [Bitácora Fase 9](./bitacora-fase9.md) — Pipelines Inventory y Customer + ExcelExtractor
- [Bitácora Fase 10](./bitacora-fase10.md) — Hardening final V1: retries automáticos, notifications, Docker y roadmap V2
- [Command Reference](./cmd.md) — Comandos operativos de Docker, tests unitarios e IT
- [V2 Roadmap](./architecture/v2-roadmap.md) — Diseño de la siguiente versión del motor

---

## Version Roadmap

### V1 — Core Engine (Current)

V1 focuses on building a stable, well-tested ETL engine with manual orchestration and support for the three primary pipelines.

| Feature | Status |
|---|---|
| Pipeline configuration model | Completed |
| Manual ETL orchestrator | Completed |
| CSV and API extractors | Completed |
| Excel extractor | Completed |
| Database extractor | Completed |
| Common transformer + pipeline-specific transformers | Completed |
| Schema and business validators | Completed |
| Staging → final load strategy | Completed |
| Full audit trail | Completed |
| REST API for execution and monitoring | Completed |
| Automatic retry mechanism | Completed |
| Log-based notifications | Completed |
| Sales pipeline end-to-end | Completed |
| Inventory and Customer pipelines | Completed |
| Testcontainers-based integration tests | Completed |
| Docker Compose local environment | Completed |
| Root README and operational docs | Completed |

### V2 — Advanced Capabilities (Roadmap)

Detalle completo:

- [V2 Roadmap](./architecture/v2-roadmap.md)

| Feature | Description |
|---|---|
| Spring Batch migration | Replace manual orchestrator with formal Job/Step/Chunk model. Enables restart-from-checkpoint. |
| Cron-based scheduler | Trigger pipelines on configured schedules using Spring Scheduler or Quartz |
| Monitoring dashboard | Web UI for execution status, metrics, rejected record inspection |
| ExcelExtractor (Apache POI) | Completed in V1 |
| DatabaseExtractor optimization | Advanced cursor tuning and vendor-specific extraction improvements |
| Webhook notifications | Alert on failure/success via configurable webhook endpoints |
| Pipeline versioning UI | Visual diff of pipeline config versions |
| Dead-letter queue | Route persistently failing records to a dedicated review queue |
| Multi-tenant support | Namespace-isolated pipeline execution for different business units |

---

## Project Conventions

- All domain classes are pure Java — **no Spring annotations** in `domain/`
- All business logic lives in `domain/` or `application/` — **never** in controllers or repositories
- Every pipeline execution produces a complete, immutable audit trail
- Rejected records are **never discarded** — always persisted with full context
- All database interactions go through the **adapter layer** in `infrastructure/persistence/adapter/`
- Configuration is **externalized** — pipeline behavior is driven by YAML config, not code changes

---

*Documentation maintained alongside source code. Last updated: 2026-03-24.*
