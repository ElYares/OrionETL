# ADR-001: Hexagonal Architecture (Ports and Adapters)

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-03-21 |
| **Deciders** | Architecture Team |
| **Context** | OrionETL system design — initial architecture decision |

---

## Context

OrionETL is an enterprise ETL engine that must support:

- **Multiple source types:** CSV files, Excel workbooks, JSON files, REST APIs, and relational databases.
- **Multiple destination types:** PostgreSQL tables, CSV exports, data warehouses.
- **Multiple pipelines:** Sales, Inventory, Customer, Crypto — each with different schemas, transformation rules, and business logic.
- **Evolvability:** New source types, new pipelines, and new destination strategies must be addable with minimal impact on existing functionality.
- **Testability:** Business rules (validation thresholds, transformation formulas, rejection logic) must be unit-testable in isolation, without requiring a database, a file system, or an HTTP server.
- **Auditability:** The core audit logic and execution lifecycle must be stable and independent of the underlying infrastructure.

The central challenge: if business logic is intertwined with Spring annotations, JPA entities, or CSV parsing code, every test requires a Spring context, every change risks breaking unrelated code, and adding a new source type means modifying business logic classes.

---

## Decision

OrionETL adopts **Hexagonal Architecture** (Ports and Adapters), also known as "Clean Architecture" in its layered form.

The architecture establishes a strict dependency rule:

```
Outer layers depend on inner layers. Inner layers never depend on outer layers.

[interfaces] → [application] → [domain] ← [infrastructure]
```

### The Domain is the Center

The `domain` package contains the core business model and logic:

- All domain entities (`Pipeline`, `PipelineExecution`, `RawRecord`, etc.)
- All domain enumerations (`ExecutionStatus`, `ErrorType`, etc.)
- All domain services (`PipelineOrchestrationService`, `DataQualityService`, etc.)
- All domain rules (`NoDuplicateExecutionRule`, `ErrorThresholdRule`, etc.)
- All **port interfaces** (`DataExtractor`, `DataTransformer`, `DataLoader`, `DataValidator`, repository interfaces)

**The domain has zero framework dependencies.** No `@Component`, no `@Service`, no `@Autowired`, no JPA annotations, no Spring imports. It is plain Java 21.

### Ports Define What the Domain Needs

The domain defines interfaces (ports) that express what the business logic requires without specifying how:

```java
// The domain says "I need to extract data" — it does not say HOW.
public interface DataExtractor {
    ExtractionResult extract(SourceConfig config, PipelineExecution execution);
}

// The domain says "I need to persist rejected records" — it does not say WHERE.
public interface RejectedRecordRepository {
    void save(List<RejectedRecord> records);
}
```

### Adapters Implement the Ports

The `infrastructure` package provides concrete adapters:

```java
// Infrastructure says: "I know HOW to extract from CSV using OpenCSV."
@Component
public class CsvExtractor implements DataExtractor {
    @Override
    public ExtractionResult extract(SourceConfig config, PipelineExecution execution) {
        // CSV-specific code here
    }
}

// Infrastructure says: "I know HOW to persist rejected records using JPA."
@Component
public class RejectedRecordRepositoryAdapter implements RejectedRecordRepository {
    private final JpaRejectedRecordRepository jpaRepository;
    // ...
}
```

### Configuration Wires It Together

Spring's dependency injection (configured in `config/beans/`) wires the correct adapter into each application service at startup. The domain never knows which adapter is in use.

---

## Rationale

This decision was chosen over alternatives for the following reasons:

### Why Not Traditional Layered Architecture (Controller → Service → Repository)?

In a traditional layered architecture, the `Service` layer typically imports JPA repositories directly. This means:

1. Every unit test for a `Service` class requires either a running Spring context or extensive Mockito mocking of JPA internals.
2. The `Service` layer becomes tightly coupled to the database technology. Switching from JPA to JDBC, or adding a second persistence backend, requires modifying business logic classes.
3. Business rules and persistence logic are often mixed in `Service` classes, making them hard to understand and test independently.

### Why Not Microservices / Event-Driven Architecture?

OrionETL V1 is a single-process ETL engine. While future evolution toward event-driven or distributed processing is possible, premature distribution would add operational complexity (message brokers, distributed transactions, network failures) without corresponding business benefit at this stage.

### Why Hexagonal Specifically?

Hexagonal architecture provides:

1. **Clear boundaries:** The domain-infrastructure boundary is explicit and enforced.
2. **Pluggable adapters:** Each extractor, transformer, loader, and validator is a plug-in. Adding a new source type (e.g., Parquet files) means writing one new `DataExtractor` implementation — nothing else changes.
3. **Framework independence:** The domain can be tested and understood without Spring. A developer reading `PipelineOrchestrationService` sees only business logic.
4. **Evolutionary path:** V2 introduces Spring Batch. With hexagonal architecture, the Spring Batch integration lives in `infrastructure/batch/` and `config/batch/`. The domain and application layers are unaffected.

---

## Consequences

### Positive Consequences

1. **Domain purity:** All 10 domain rules are unit-testable with plain JUnit 5, no Spring context needed. Test execution time for domain tests is measured in milliseconds.

2. **Pluggable extractors:** Adding an `FtpExtractor` for SFTP-sourced files requires only:
   - A new class in `infrastructure/extractor/ftp/`
   - A new `SourceType.FTP` enum value
   - No changes to application or domain code

3. **Testable application layer:** Use cases can be tested by providing mock adapter implementations (which implement the domain interfaces), without any database or framework.

4. **Spring Batch migration path:** V2's Spring Batch integration does not require modifying the domain or application layers. The orchestrator's role is taken over by a Spring Batch `Job`, but the underlying `DataExtractor`, `DataTransformer`, `DataValidator`, and `DataLoader` implementations remain unchanged.

5. **Onboarding clarity:** New developers can understand the business logic by reading `domain/` classes without needing to understand Spring, JPA, or any infrastructure specifics.

### Negative Consequences

1. **More classes:** The adapter pattern requires additional adapter classes (e.g., `PipelineRepositoryAdapter` alongside the JPA repository interface). This increases the total number of classes compared to a traditional layered approach.

2. **More mapping:** Data must be mapped between domain models, JPA entities, and DTOs at layer boundaries. MapStruct mitigates the boilerplate cost.

3. **Initial complexity:** Developers unfamiliar with hexagonal architecture may find the indirection (interface → adapter → JPA repository) confusing at first. This is addressed with documentation (this ADR and the architecture overview).

4. **Strict discipline required:** The dependency rule (domain has no Spring imports) requires active enforcement via code review and potentially static analysis tools (ArchUnit tests).

---

## Enforcement

The following mechanisms enforce the architectural rules:

1. **Code review checklist:** Any new class in `domain/` must be reviewed for Spring imports.
2. **ArchUnit tests (V2):** Automated architecture tests in `test/unit/architecture/` will verify:
   - No classes in `domain/` import from `org.springframework.*`
   - No classes in `domain/` import from `jakarta.persistence.*`
   - No classes in `interfaces/` import directly from `domain/service/` (must go through `application/`)
3. **Build warnings:** Maven compiler plugin configured to warn on unexpected cross-layer dependencies.

---

## Alternatives Considered

| Alternative | Rejected Reason |
|---|---|
| Traditional Spring MVC layered architecture | Too much coupling between business logic and Spring/JPA. Testability suffers. |
| Pure Clean Architecture with separate Maven modules | Overkill for a single-process application at V1. Module overhead without proportional benefit. |
| Spring Batch as the primary architecture driver from V1 | Adds complexity to V1. Spring Batch is adopted in V2 when the job model is well-understood. See ADR-002. |
| No explicit architecture (organic growth) | Historically leads to unmaintainable "big ball of mud" — especially problematic for ETL engines that grow incrementally. |
