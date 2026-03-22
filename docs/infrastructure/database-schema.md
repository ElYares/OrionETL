# Database Schema

OrionETL uses PostgreSQL 15+ as its metadata and audit store. All tables are managed by Flyway migrations. The schema is designed for observability: every execution, step, error, rejected record, and audit event is stored and queryable.

---

## Schema Overview

```
etl_pipelines                         ← Pipeline definitions
etl_pipeline_executions               ← One row per pipeline run
etl_execution_steps                   ← One row per step per run
etl_execution_errors                  ← All classified errors per run
etl_rejected_records                  ← All rejected records per run (JSONB)
etl_audit_records                     ← Immutable audit log
etl_execution_metrics                 ← Numeric metrics per run
```

All tables use UUID primary keys. All timestamps are `TIMESTAMPTZ` (UTC). All JSONB columns use GIN indexes for efficient querying.

---

## Table: `etl_pipelines`

Stores the registered pipeline definitions managed by OrionETL. Each row represents one active (or historical) pipeline configuration version.

```sql
CREATE TABLE etl_pipelines (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255)    NOT NULL UNIQUE,
    version         VARCHAR(50)     NOT NULL,
    description     TEXT,
    status          VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
                                    -- ACTIVE, INACTIVE, DEPRECATED
    source_type     VARCHAR(50)     NOT NULL,
                                    -- CSV, EXCEL, JSON, API, DATABASE
    target_type     VARCHAR(50)     NOT NULL,
                                    -- DATABASE, CSV, WAREHOUSE
    config_json     JSONB           NOT NULL,
                                    -- Full pipeline configuration (source, target, validation,
                                    -- transformation, retry, schedule configs serialized to JSON)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_pipelines_name    ON etl_pipelines (name);
CREATE INDEX idx_etl_pipelines_status  ON etl_pipelines (status);
CREATE INDEX idx_etl_pipelines_config  ON etl_pipelines USING GIN (config_json);
```

**Notes:**
- `config_json` stores the complete serialized `Pipeline` domain model. This allows the engine to reconstruct the full configuration at any point in history.
- `name` is globally unique. Attempting to create two pipelines with the same name fails at the database level.
- `status` is enforced at the application level (domain rule), not as a FK constraint.

---

## Table: `etl_pipeline_executions`

One row per pipeline execution attempt. Updated throughout the lifecycle from `PENDING` to a terminal state.

```sql
CREATE TABLE etl_pipeline_executions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID            NOT NULL REFERENCES etl_pipelines(id),
    execution_ref   UUID            NOT NULL UNIQUE,
                                    -- Public-facing execution identifier (correlation ID for logs/API)
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
                                    -- PENDING, RUNNING, SUCCESS, FAILED, PARTIAL, SKIPPED, RETRYING
    trigger_type    VARCHAR(50)     NOT NULL,
                                    -- MANUAL, SCHEDULED, RETRY, CLI
    triggered_by    VARCHAR(500),
                                    -- Actor: 'scheduler', 'api:user@example.com', 'cli:jenkins'
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    total_read      BIGINT          NOT NULL DEFAULT 0,
    total_transformed BIGINT        NOT NULL DEFAULT 0,
    total_rejected  BIGINT          NOT NULL DEFAULT 0,
    total_loaded    BIGINT          NOT NULL DEFAULT 0,
    error_summary   TEXT,
                                    -- Short description of failure cause (null on success)
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    parent_execution_id UUID        REFERENCES etl_pipeline_executions(id),
                                    -- For retry executions: reference to the original failed execution
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_exec_pipeline_id  ON etl_pipeline_executions (pipeline_id);
CREATE INDEX idx_etl_exec_status       ON etl_pipeline_executions (status);
CREATE INDEX idx_etl_exec_ref          ON etl_pipeline_executions (execution_ref);
CREATE INDEX idx_etl_exec_started_at   ON etl_pipeline_executions (started_at DESC);

-- Partial index for active execution lookup (enforces no-duplicate-execution rule)
CREATE UNIQUE INDEX idx_etl_exec_active_per_pipeline
    ON etl_pipeline_executions (pipeline_id)
    WHERE status IN ('RUNNING', 'RETRYING');
```

**Notes:**
- `execution_ref` is the UUID exposed in the REST API and logged in all MDC context. `id` is the internal PK.
- The partial unique index on `(pipeline_id) WHERE status IN ('RUNNING', 'RETRYING')` enforces the no-duplicate-execution rule at the database level (in addition to the domain rule check).
- `parent_execution_id` links retry executions to their original failed run for traceability.

---

## Table: `etl_execution_steps`

One row per step per execution. Tracks the progress and outcome of each of the 8 ETL steps.

```sql
CREATE TABLE etl_execution_steps (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    step_name           VARCHAR(100) NOT NULL,
                                    -- INIT, EXTRACT, VALIDATE_SCHEMA, TRANSFORM,
                                    -- VALIDATE_BUSINESS, LOAD, CLOSE, AUDIT
    step_order          INTEGER     NOT NULL,
                                    -- 1-8, determines display order
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                                    -- PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    records_processed   BIGINT      DEFAULT 0,
    error_detail        TEXT,
                                    -- Short error description if step failed
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_execution_step UNIQUE (execution_id, step_name)
);

CREATE INDEX idx_etl_steps_execution_id ON etl_execution_steps (execution_id);
CREATE INDEX idx_etl_steps_status       ON etl_execution_steps (status);
```

**Notes:**
- The unique constraint on `(execution_id, step_name)` ensures each step appears exactly once per execution.
- `step_order` is stored for reliable ordering when displaying step progress in the UI or API response.

---

## Table: `etl_execution_errors`

All classified errors captured during an execution. One row per distinct error event. Multiple errors per execution are normal.

```sql
CREATE TABLE etl_execution_errors (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    step_name           VARCHAR(100) NOT NULL,
    error_type          VARCHAR(100) NOT NULL,
                                    -- TECHNICAL, FUNCTIONAL, DATA_QUALITY, EXTERNAL_INTEGRATION
    error_code          VARCHAR(100) NOT NULL,
                                    -- Machine-readable code (e.g. 'EXTRACTION_IO_FAILURE')
    message             TEXT        NOT NULL,
    stack_trace         TEXT,
                                    -- Full Java stack trace (populated for TECHNICAL errors only)
    record_reference    VARCHAR(500),
                                    -- Row number or business key (for DATA_QUALITY errors)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_errors_execution_id ON etl_execution_errors (execution_id);
CREATE INDEX idx_etl_errors_type         ON etl_execution_errors (error_type);
CREATE INDEX idx_etl_errors_step         ON etl_execution_errors (step_name);
```

**Notes:**
- `stack_trace` is only populated for `TECHNICAL` errors. For `DATA_QUALITY` and `FUNCTIONAL` errors, `record_reference` and `message` provide the relevant context.
- `error_code` enables programmatic error handling and alerting rules.

---

## Table: `etl_rejected_records`

Every record rejected at any step is persisted here in full. This table can grow large and should be partitioned by `rejected_at` (range partitioning by month) in production deployments.

```sql
CREATE TABLE etl_rejected_records (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    pipeline_id         UUID        NOT NULL REFERENCES etl_pipelines(id),
    step_name           VARCHAR(100) NOT NULL,
                                    -- VALIDATE_SCHEMA, TRANSFORM, VALIDATE_BUSINESS, LOAD
    raw_data            JSONB       NOT NULL,
                                    -- Complete original record as extracted from source
    rejection_reason    TEXT        NOT NULL,
                                    -- Human-readable summary of why the record was rejected
    validation_errors   JSONB,
                                    -- JSON array of ValidationError objects:
                                    -- [{field, value, rule, message, severity, rowNumber}]
    source_row_number   BIGINT,
                                    -- Original row number in source file (for traceability)
    rejected_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_rejected_execution_id  ON etl_rejected_records (execution_id);
CREATE INDEX idx_etl_rejected_pipeline_id   ON etl_rejected_records (pipeline_id);
CREATE INDEX idx_etl_rejected_step          ON etl_rejected_records (step_name);
CREATE INDEX idx_etl_rejected_at            ON etl_rejected_records (rejected_at DESC);
CREATE INDEX idx_etl_rejected_raw_data      ON etl_rejected_records USING GIN (raw_data);
CREATE INDEX idx_etl_rejected_errors        ON etl_rejected_records USING GIN (validation_errors);
```

**Notes:**
- `raw_data` JSONB stores the complete original record for forensic purposes.
- GIN indexes on both `raw_data` and `validation_errors` allow efficient querying (e.g., "find all rejections where the `amount` field had a value > 0").
- In production, consider partitioning this table: `PARTITION BY RANGE (rejected_at)`.

---

## Table: `etl_audit_records`

An immutable audit log. One row is created per execution, always, regardless of outcome. No rows are ever deleted or updated.

```sql
CREATE TABLE etl_audit_records (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID        NOT NULL REFERENCES etl_pipeline_executions(id),
    pipeline_id     UUID        NOT NULL REFERENCES etl_pipelines(id),
    action          VARCHAR(200) NOT NULL,
                                -- 'PIPELINE_EXECUTED', 'PIPELINE_FAILED', 'PIPELINE_SKIPPED',
                                -- 'PIPELINE_RETRIED', 'EXECUTION_ABORTED'
    actor_type      VARCHAR(100) NOT NULL,
                                -- 'SCHEDULER', 'API', 'CLI', 'SYSTEM'
    details         JSONB       NOT NULL,
                                -- Full audit detail:
                                -- { status, totalRead, totalTransformed, totalRejected,
                                --   totalLoaded, durationMs, triggeredBy, stepSummary,
                                --   errorSummary, pipelineVersion, sourceFile }
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_audit_execution_id ON etl_audit_records (execution_id);
CREATE INDEX idx_etl_audit_pipeline_id  ON etl_audit_records (pipeline_id);
CREATE INDEX idx_etl_audit_timestamp    ON etl_audit_records (timestamp DESC);
CREATE INDEX idx_etl_audit_action       ON etl_audit_records (action);
CREATE INDEX idx_etl_audit_details      ON etl_audit_records USING GIN (details);

-- No DELETE or UPDATE should ever be performed on this table.
-- In production, consider adding a row-level security policy to enforce this.
```

---

## Table: `etl_execution_metrics`

Named numeric metrics recorded during each execution. Used for performance dashboards and trend analysis.

```sql
CREATE TABLE etl_execution_metrics (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id    UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    metric_name     VARCHAR(200) NOT NULL,
                                -- 'extract.duration.ms', 'transform.records.per.second',
                                -- 'records.rejected.rate', 'execution.total.duration.ms'
    metric_value    NUMERIC(20, 6) NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_metrics_execution_id ON etl_execution_metrics (execution_id);
CREATE INDEX idx_etl_metrics_name         ON etl_execution_metrics (metric_name);
CREATE INDEX idx_etl_metrics_recorded_at  ON etl_execution_metrics (recorded_at DESC);
```

---

## Flyway Migration Strategy

All schema changes are managed by Flyway. Migration scripts are located in `src/main/resources/db/migration/`.

### Naming Convention

```
V{version}__{description}.sql

Examples:
V1__create_etl_schema.sql
V2__add_indexes.sql
V3__add_audit_table.sql
V4__add_metrics_table.sql
V5__add_execution_retry_columns.sql
```

### Migration History

| Migration | Description | Tables Affected |
|---|---|---|
| `V1__create_etl_schema.sql` | Creates all core ETL metadata tables with primary keys and basic constraints. | `etl_pipelines`, `etl_pipeline_executions`, `etl_execution_steps`, `etl_execution_errors`, `etl_rejected_records` |
| `V2__add_indexes.sql` | Adds performance indexes for common query patterns (by `pipeline_id`, `status`, `started_at`). | All V1 tables |
| `V3__add_audit_table.sql` | Creates `etl_audit_records` table with GIN index on `details` JSONB. | `etl_audit_records` |
| `V4__add_metrics_table.sql` | Creates `etl_execution_metrics` table. | `etl_execution_metrics` |
| `V5__add_execution_retry_columns.sql` | Adds `retry_count` and `parent_execution_id` columns to `etl_pipeline_executions`. | `etl_pipeline_executions` |
| `V6__add_partial_index_active_execution.sql` | Adds the partial unique index on `etl_pipeline_executions(pipeline_id) WHERE status IN ('RUNNING','RETRYING')`. | `etl_pipeline_executions` |

### Flyway Configuration

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    out-of-order: false
    validate-on-migrate: true
```

**Principles:**
- Migrations are **never modified** after being applied (immutable history).
- New changes always create a new migration file.
- Destructive operations (DROP, TRUNCATE) require explicit review and must never be in automatic migrations for production tables.
- Each migration script is idempotent where possible (using `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`).

---

## Useful Queries

### Find the most recent execution for each pipeline

```sql
SELECT DISTINCT ON (pipeline_id)
    pipeline_id,
    execution_ref,
    status,
    started_at,
    finished_at,
    total_read,
    total_loaded,
    total_rejected
FROM etl_pipeline_executions
ORDER BY pipeline_id, started_at DESC;
```

### Find all rejected records for a specific execution

```sql
SELECT
    source_row_number,
    step_name,
    rejection_reason,
    raw_data,
    validation_errors,
    rejected_at
FROM etl_rejected_records
WHERE execution_id = (
    SELECT id FROM etl_pipeline_executions WHERE execution_ref = '{your-execution-uuid}'
)
ORDER BY source_row_number;
```

### Find all FAILED executions in the last 7 days

```sql
SELECT
    p.name AS pipeline_name,
    e.execution_ref,
    e.status,
    e.started_at,
    e.finished_at,
    e.error_summary,
    e.total_read,
    e.total_rejected
FROM etl_pipeline_executions e
JOIN etl_pipelines p ON p.id = e.pipeline_id
WHERE e.status = 'FAILED'
  AND e.started_at >= NOW() - INTERVAL '7 days'
ORDER BY e.started_at DESC;
```

### Error rate trend for a pipeline

```sql
SELECT
    DATE(started_at) AS execution_date,
    AVG(CASE WHEN total_read > 0 THEN total_rejected::FLOAT / total_read * 100 ELSE 0 END) AS avg_error_rate_pct,
    COUNT(*) AS execution_count
FROM etl_pipeline_executions
WHERE pipeline_id = '{pipeline-uuid}'
  AND status IN ('SUCCESS', 'PARTIAL')
  AND started_at >= NOW() - INTERVAL '30 days'
GROUP BY DATE(started_at)
ORDER BY execution_date;
```
