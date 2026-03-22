# ADR-003: Staging Load Strategy

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-03-21 |
| **Deciders** | Architecture Team, Data Engineering Team |
| **Context** | ETL data loading — safety and consistency strategy |

---

## Context

ETL loading to production tables carries inherent risks:

1. **Partial failure:** If a load fails after 50,000 of 100,000 records have been written, the production table contains half the dataset — an inconsistent state.
2. **Bad data discovery:** Data quality issues may only become apparent after partially loading the dataset, when constraint violations, referential integrity failures, or semantic errors surface.
3. **Rollback complexity:** Rolling back a partially executed `INSERT` or `UPDATE` across thousands of records in a live production table is complex, risky, and slow.
4. **Concurrent reads:** During a long-running load, downstream consumers (reports, APIs, applications) that read the same production table see an evolving, inconsistent snapshot.
5. **Debugging opacity:** When a load fails directly to the production table, the "bad data" is often already mixed with "good data" in the same table, making forensic analysis difficult.

The question was: how should OrionETL write data to destination tables in a way that guarantees consistency, enables forensic inspection, and supports clean rollback?

---

## Decision

**All OrionETL pipelines load data to a staging table first, validate the staging table, then promote the contents to the final destination table.**

This is the mandatory standard for all pipelines. No pipeline may write directly to a final production table during the automated ETL execution flow.

### The Three-Phase Load Model

Every `LOAD` step executes three phases:

**Phase 1: Load to Staging**

The `StagingLoader` writes all `ProcessedRecord`s (records that passed schema and business validation) to a staging table:

```
Table: {target_table_name}_staging
Example: sales_transactions_staging
```

The staging table is always cleared at the start of a new execution for the same pipeline, then populated fresh. Each staging record is tagged with the `execution_id` for traceability.

Loading to staging uses chunked transactions (configurable chunk size, default 500 records). If a chunk fails, it is logged and either the load aborts or continues (per `failFastOnChunkError` config).

**Phase 2: Validate Staging**

The `StagingValidator` runs a series of integrity checks on the staging table:

- Row count matches the expected `processedRecords.size()`.
- No null values in mandatory target columns.
- Business key uniqueness (for UPSERT pipelines).
- Optional: FK reference checks against parent tables.

If any check fails, Phase 3 (promotion) is **not executed**. The execution is marked `FAILED`, and the staging table is preserved for inspection.

**Phase 3: Promote to Final Table**

The `FinalLoader` executes the promotion in a single database transaction:

```sql
-- INSERT strategy:
INSERT INTO sales_transactions
SELECT * FROM sales_transactions_staging
WHERE etl_execution_id = '{current_execution_id}';

-- UPSERT strategy:
INSERT INTO sales_transactions (transaction_id, customer_id, amount, ...)
SELECT transaction_id, customer_id, amount, ...
FROM sales_transactions_staging
WHERE etl_execution_id = '{current_execution_id}'
ON CONFLICT (transaction_id)
DO UPDATE SET customer_id = EXCLUDED.customer_id, amount = EXCLUDED.amount, ...;

-- REPLACE strategy (scoped replace, not full table truncate):
DELETE FROM sales_transactions WHERE sale_date = '{batch_date}';
INSERT INTO sales_transactions
SELECT * FROM sales_transactions_staging
WHERE etl_execution_id = '{current_execution_id}';
```

The promotion is an atomic single transaction. Either all records are promoted or none are.

---

## Staging Table Design

Staging tables are created by Flyway migrations, mirroring the final table schema with one addition: the `etl_execution_id` column for traceability.

Example:

```sql
-- V7__create_staging_tables.sql

CREATE TABLE sales_transactions_staging (
    LIKE sales_transactions INCLUDING DEFAULTS,   -- Copy column definitions
    etl_execution_id UUID NOT NULL,
    etl_loaded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sales_staging_execution_id
    ON sales_transactions_staging (etl_execution_id);
```

The staging table does **not** include foreign key constraints (to avoid validation overhead during staging load — FK validation happens in staging validation phase). FK constraints exist on the final table.

---

## Rationale

### Why Staging?

The staging-first pattern is chosen over several alternatives:

**Alternative 1: Direct insert with transaction rollback**

Load all records in a single large transaction. If any failure occurs, roll back the entire transaction.

- **Problem 1:** A single transaction holding 100,000 row locks is a severe contention risk in a shared database.
- **Problem 2:** If the database crashes mid-load, the transaction log required for rollback may be enormous.
- **Problem 3:** No ability to inspect "what was about to be loaded" for debugging.
- **Problem 4:** The entire load must be retried from scratch even if only the last 10% failed.

**Alternative 2: Chunked direct inserts with compensating deletes**

Load chunks directly to the final table. If the process fails mid-way, run a compensating DELETE for already-loaded chunks.

- **Problem 1:** The window between the last committed chunk and the compensating DELETE is observable to downstream consumers (inconsistent data visible).
- **Problem 2:** The compensating DELETE must execute correctly under failure conditions — a second point of failure.
- **Problem 3:** Complex to implement correctly, especially with UPSERT semantics.

**Alternative 3: Shadow table + rename (blue/green tables)**

Maintain two copies of the target table. Load into the inactive copy. Swap by renaming.

- **Problem 1:** PostgreSQL's `ALTER TABLE RENAME` is a DDL operation that acquires an `ACCESS EXCLUSIVE` lock, briefly blocking all reads and writes.
- **Problem 2:** Managing two complete copies of large tables requires double the storage.
- **Problem 3:** Not compatible with FK relationships pointing to the table (FK targets cannot be renamed).

**Staging-first chosen because:**

1. Staging is isolated from production reads (staging tables are not queried by applications).
2. Promotion is a single atomic transaction — either fully committed or not.
3. Staging tables are inspectable for debugging without contaminating production.
4. No lock contention during the staging write phase.
5. Staging validation provides a final safety check before promotion.
6. Clean rollback: if promotion fails, delete from final table WHERE `etl_execution_id = {current}`.

---

## Consequences

### Positive Consequences

1. **Data consistency guaranteed:** The final table never sees a partial batch. Promotion is atomic.

2. **Forensic inspection after failure:** When a staging validation fails, the staging table is preserved. Operators can query `sales_transactions_staging WHERE etl_execution_id = '{failed_exec_id}'` to see exactly what was about to be loaded and why it failed.

3. **Clean rollback:** If a critical failure occurs after promotion, `DELETE FROM {table} WHERE etl_execution_id = '{execution_id}'` precisely undoes exactly what this execution loaded — no more, no less.

4. **Staging as a test harness:** During development, new pipelines can be validated by running them with a `dry-run` mode that loads to staging but skips promotion. Developers can inspect the staging table to verify transformation correctness before allowing production loads.

5. **Concurrent reads unaffected:** Downstream applications reading the final table see a consistent snapshot during the entire staging phase. The final table only changes instantaneously during the promotion transaction.

### Negative Consequences

1. **Additional storage:** Each pipeline requires a staging table that can hold up to one full batch of data. For large pipelines, this doubles the storage requirement for that dataset during the load window.

2. **Additional DDL:** Each target table requires a corresponding staging table created in Flyway migrations.

3. **Additional complexity:** Three phases instead of one. More code to implement and test (`StagingLoader`, `StagingValidator`, `FinalLoader` vs. a single `DirectLoader`).

4. **Staging cleanup:** Staging tables accumulate data from multiple executions if not cleaned. The engine clears staging at the start of each new execution, but this must be implemented correctly.

5. **Not applicable for streaming:** The staging-first model is a batch model. It is not suitable for real-time streaming ETL (which is out of scope for OrionETL V1 and V2).

---

## Staging Lifecycle

```
Execution starts
    │
    ▼
TRUNCATE {table}_staging WHERE pipeline_id = {current_pipeline}
(or DELETE WHERE etl_execution_id IS NULL — for first run)
    │
    ▼
Load to staging (chunked, with StagingLoader)
    │
    ▼
Run StagingValidator
    │
  passes? ─NO─► Preserve staging, mark execution FAILED, skip promotion
    │ YES
    ▼
Promote: INSERT/UPSERT/REPLACE into final table (single transaction)
    │
    ▼
Mark execution SUCCESS/PARTIAL
    │
    ▼
Staging table remains populated (for forensic reference)
    │
Next execution of same pipeline starts
    │
    ▼
Staging is cleared at start of new execution
```

---

## Related Decisions

- [ADR-001: Hexagonal Architecture](./ADR-001-hexagonal-architecture.md) — The `StagingLoader`, `StagingValidator`, and `FinalLoader` are infrastructure adapters implementing domain contracts. The domain never references staging tables directly.
- [ADR-002: Spring Batch](./ADR-002-spring-batch.md) — In V2, the staging load and promotion become Spring Batch `ItemWriter` steps within a `Chunk` model, but the staging strategy itself is unchanged.
- [Loading Rules](../business-rules/loading-rules.md) — Rules LOAD-001 through LOAD-010 formalize the loading behavior enforced by this architecture decision.
