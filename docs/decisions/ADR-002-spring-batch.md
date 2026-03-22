# ADR-002: Spring Batch Adoption Strategy

| Field | Value |
|---|---|
| **Status** | Accepted for V2; Manual Orchestration for V1 |
| **Date** | 2026-03-21 |
| **Deciders** | Architecture Team |
| **Context** | ETL execution engine — batch processing framework decision |

---

## Context

ETL jobs have specific operational requirements that distinguish them from standard application request/response processing:

- **Restartability:** If a job fails halfway through processing 1 million records, it should be restartable from where it left off, not from the beginning.
- **Chunk processing:** Records should be processed and committed in configurable batches (chunks) to balance memory usage, transaction size, and failure granularity.
- **State management:** The job's progress (which step completed, how many records were processed) should survive application restarts.
- **Retry mechanisms:** Individual steps should be retryable with configurable back-off, without rerunning the entire job.
- **Formal job model:** A formal `Job → Step → (ItemReader, ItemProcessor, ItemWriter)` model with a persistent `JobRepository` makes it easy to query job history, monitor execution, and integrate with scheduling systems.

**Spring Batch** is the de-facto standard for these requirements in the Spring ecosystem. It provides all of the above out of the box.

However, Spring Batch also introduces significant complexity:
- `JobRepository` schema (6+ tables in the database).
- `JobLauncher`, `JobBuilderFactory`, `StepBuilderFactory` configuration.
- `ItemReader` / `ItemProcessor` / `ItemWriter` interfaces.
- Step execution context and `ExecutionContext` serialization.
- Formal `JobParameters` handling.

For a team learning the system and the three initial pipelines, this complexity can obscure the business logic during V1 development.

---

## Decision

### V1: Manual Orchestration

V1 uses a custom `ETLOrchestrator` that manually drives the 8-step execution flow. This is a straightforward, understandable implementation:

```java
@Service
public class ETLOrchestrator {

    public ExecutionResult execute(PipelineId pipelineId, ExecutionRequest request) {
        PipelineExecution execution = initStep(pipelineId, request);
        try {
            ExtractionResult extracted = extractStep(execution);
            ValidationResult schemaResult = validateSchemaStep(execution, extracted.records());
            if (schemaResult.isAbort()) return closeAndAudit(execution, FAILED);

            TransformationResult transformed = transformStep(execution, schemaResult.validRecords());
            ValidationResult businessResult = validateBusinessStep(execution, transformed.processedRecords());
            if (businessResult.isAbort()) return closeAndAudit(execution, FAILED);

            LoadResult loaded = loadStep(execution, businessResult.validRecords());
            return closeAndAudit(execution, determineStatus(loaded, businessResult));

        } catch (ExtractionException e) {
            recordError(execution, e, ErrorType.TECHNICAL);
            return closeAndAudit(execution, FAILED);
        } catch (Exception e) {
            recordError(execution, e, ErrorType.TECHNICAL);
            return closeAndAudit(execution, FAILED);
        } finally {
            auditStep(execution); // ALWAYS runs
        }
    }
}
```

**V1 characteristics:**
- Simple, linear, easy to debug.
- No Spring Batch dependency or schema required.
- State is persisted manually in `etl_pipeline_executions` and `etl_execution_steps` tables (which OrionETL already maintains).
- Retry is implemented as a new execution with `triggerType = RETRY`.
- No restart-from-checkpoint (if a failure occurs at step 5 of 8, the job restarts from step 1 on retry).

### V2: Spring Batch Migration

V2 replaces `ETLOrchestrator` with a formal Spring Batch `Job` definition. Each ETL step becomes a Spring Batch `Step`.

The `DataExtractor`, `DataTransformer`, `DataValidator`, and `DataLoader` implementations remain **unchanged** (hexagonal architecture ensures this — see ADR-001). Spring Batch only changes the orchestration layer.

```
V1: ETLOrchestrator manually calls extract() → validateSchema() → transform() → ...
V2: Spring Batch Job drives:
    Step 1 (INIT) → Step 2 (EXTRACT) → Step 3 (VALIDATE_SCHEMA) →
    Step 4 (TRANSFORM/chunk) → Step 5 (VALIDATE_BUSINESS) →
    Step 6 (LOAD/chunk) → Step 7 (CLOSE) → Step 8 (AUDIT)
```

**V2 Spring Batch mapping:**

| ETL Step | Spring Batch Component |
|---|---|
| INIT | `Tasklet` step |
| EXTRACT | `Tasklet` step (returns `ExtractionResult` stored in `ExecutionContext`) |
| VALIDATE_SCHEMA | `Tasklet` step |
| TRANSFORM | `Chunk<RawRecord, ProcessedRecord>` step (ItemReader/ItemProcessor/ItemWriter) |
| VALIDATE_BUSINESS | `Tasklet` step |
| LOAD | `Chunk<ProcessedRecord, Void>` step (ItemReader reads from staging, ItemWriter promotes) |
| CLOSE | `Tasklet` step |
| AUDIT | `Tasklet` step (always runs via `StepExecution.FAILED` → `AUDIT` step flow) |

---

## Rationale

### Why Not Spring Batch in V1?

1. **Learning curve:** Spring Batch requires understanding `JobRepository`, `JobParameters`, `ExecutionContext`, `StepContribution`, `ChunkContext`, and the `ItemReader/ItemProcessor/ItemWriter` contract before writing the first line of business logic. V1 should focus on getting the business logic right.

2. **Schema complexity:** Spring Batch adds 6 tables to the database schema (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_JOB_EXECUTION_PARAMS`, `BATCH_JOB_EXECUTION_CONTEXT`, `BATCH_STEP_EXECUTION`, `BATCH_STEP_EXECUTION_CONTEXT`). OrionETL already has its own execution tracking schema. Managing both doubles the operational surface area.

3. **Debuggability:** A simple method call chain in `ETLOrchestrator` is easier to set breakpoints in and step through than a Spring Batch job with its framework-managed transaction boundaries and proxy objects.

4. **Overkill for V1 scale:** The three initial pipelines (Sales, Inventory, Customer) process at most tens of thousands of records per run. The restart-from-checkpoint capability of Spring Batch is most valuable at millions of records.

### Why Spring Batch in V2?

1. **Restart from checkpoint:** At V2 scale (large datasets, multiple pipelines), the ability to restart a failed 8-step job from step 6 rather than step 1 saves significant processing time and reduces load on source systems.

2. **Formal chunk model:** Spring Batch's `Chunk` processing provides built-in transaction boundaries per chunk, retry on `ItemWriter` failures, and skip policies — replacing the manual chunk logic in `StagingLoader`.

3. **JobRepository as source of truth:** Spring Batch's `JobRepository` stores comprehensive execution history in a standardized schema, making it easy to integrate with monitoring tools (Spring Batch Admin, Grafana, custom dashboards).

4. **Scheduling integration:** Spring Batch's `JobLauncher` integrates naturally with Spring's `@Scheduled` and Quartz for trigger-based execution.

5. **Skip and retry policies:** Declarative `@StepScope` skip and retry policies replace the manual error classification and retry logic in `ETLOrchestrator`.

---

## Migration Path (V1 → V2)

The migration is designed to be low-risk due to the hexagonal architecture:

1. **Phase 1:** Add Spring Batch dependency to `pom.xml` and create the `JobRepository` schema (Flyway migration `V10__add_spring_batch_schema.sql`).

2. **Phase 2:** Create Spring Batch `Job` definitions in `infrastructure/batch/` that wrap the existing `DataExtractor`, `DataTransformer`, `DataValidator`, and `DataLoader` implementations. The implementations themselves are unchanged.

3. **Phase 3:** Add a feature flag `etl.use-spring-batch = false` (default). When `true`, routes execution through the Spring Batch `JobLauncher`. This allows gradual rollout pipeline by pipeline.

4. **Phase 4:** Migrate one pipeline at a time (starting with Sales) to Spring Batch execution. Validate that the job outcomes match the V1 orchestrator outcomes with identical test data.

5. **Phase 5:** Once all pipelines are validated on Spring Batch, remove `ETLOrchestrator` and the feature flag.

---

## Consequences

### V1 Consequences

| Consequence | Impact |
|---|---|
| No restart-from-checkpoint | Failed jobs re-run from the beginning. Acceptable for V1 data volumes. |
| Manual chunk management in `StagingLoader` | More code to maintain than Spring Batch's built-in chunk model. |
| Simple, readable orchestration code | Positive: new developers understand the flow immediately. |
| No Spring Batch schema in the database | Simpler schema, fewer tables to manage. |
| Faster initial implementation | Positive: V1 ships sooner. |

### V2 Consequences

| Consequence | Impact |
|---|---|
| Restart from checkpoint | Failed jobs resume from the last committed chunk. Significant operational benefit. |
| Spring Batch schema added | 6 additional tables in the database. Managed via Flyway. |
| Richer job history and monitoring | Spring Batch Admin or custom dashboard can visualize job history. |
| More complex configuration | `@JobScope`, `@StepScope`, `JobParameters`, `ExecutionContext` require understanding. |
| Declarative skip/retry | Business rules for what to skip or retry are expressed in Spring Batch annotations, not custom code. |

---

## Related Decisions

- [ADR-001: Hexagonal Architecture](./ADR-001-hexagonal-architecture.md) — The hexagonal architecture is what makes the V1 → V2 migration feasible without rewriting business logic.
- [ADR-003: Staging Load Strategy](./ADR-003-staging-strategy.md) — The staging strategy is compatible with both V1 manual orchestration and V2 Spring Batch chunk processing.
