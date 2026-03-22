-- OrionETL ETL Metadata Schema
-- V1: Initial schema creation
-- All timestamps stored in UTC (TIMESTAMPTZ)
-- All primary keys are UUIDs

-- ============================================================
-- TABLE: etl_pipelines
-- Stores registered pipeline definitions
-- ============================================================
CREATE TABLE etl_pipelines (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255)    NOT NULL UNIQUE,
    version         VARCHAR(50)     NOT NULL,
    description     TEXT,
    status          VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    source_type     VARCHAR(50)     NOT NULL,
    target_type     VARCHAR(50)     NOT NULL,
    config_json     JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_pipelines_name   ON etl_pipelines (name);
CREATE INDEX idx_etl_pipelines_status ON etl_pipelines (status);
CREATE INDEX idx_etl_pipelines_config ON etl_pipelines USING GIN (config_json);

-- ============================================================
-- TABLE: etl_pipeline_executions
-- One row per pipeline execution attempt
-- ============================================================
CREATE TABLE etl_pipeline_executions (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id          UUID        NOT NULL REFERENCES etl_pipelines(id),
    execution_ref        UUID        NOT NULL UNIQUE,
    status               VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    trigger_type         VARCHAR(50) NOT NULL,
    triggered_by         VARCHAR(500),
    started_at           TIMESTAMPTZ,
    finished_at          TIMESTAMPTZ,
    total_read           BIGINT      NOT NULL DEFAULT 0,
    total_transformed    BIGINT      NOT NULL DEFAULT 0,
    total_rejected       BIGINT      NOT NULL DEFAULT 0,
    total_loaded         BIGINT      NOT NULL DEFAULT 0,
    error_summary        TEXT,
    retry_count          INTEGER     NOT NULL DEFAULT 0,
    parent_execution_id  UUID        REFERENCES etl_pipeline_executions(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_exec_pipeline_id ON etl_pipeline_executions (pipeline_id);
CREATE INDEX idx_etl_exec_status      ON etl_pipeline_executions (status);
CREATE INDEX idx_etl_exec_ref         ON etl_pipeline_executions (execution_ref);
CREATE INDEX idx_etl_exec_started_at  ON etl_pipeline_executions (started_at DESC);

-- Partial unique index: enforce no-duplicate-active-execution rule at DB level
CREATE UNIQUE INDEX idx_etl_exec_active_per_pipeline
    ON etl_pipeline_executions (pipeline_id)
    WHERE status IN ('RUNNING', 'RETRYING');

-- ============================================================
-- TABLE: etl_execution_steps
-- One row per step per execution (8 steps: INIT to AUDIT)
-- ============================================================
CREATE TABLE etl_execution_steps (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id      UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    step_name         VARCHAR(100) NOT NULL,
    step_order        INTEGER     NOT NULL,
    status            VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    records_processed BIGINT      NOT NULL DEFAULT 0,
    error_detail      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_steps_execution_id ON etl_execution_steps (execution_id);
CREATE INDEX idx_etl_steps_step_name    ON etl_execution_steps (step_name);
CREATE INDEX idx_etl_steps_status       ON etl_execution_steps (status);

-- ============================================================
-- TABLE: etl_execution_errors
-- All classified errors from each execution
-- ============================================================
CREATE TABLE etl_execution_errors (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id     UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    step_name        VARCHAR(100),
    error_type       VARCHAR(50) NOT NULL,
    error_code       VARCHAR(100),
    message          TEXT        NOT NULL,
    stack_trace      TEXT,
    record_reference VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_errors_execution_id ON etl_execution_errors (execution_id);
CREATE INDEX idx_etl_errors_error_type   ON etl_execution_errors (error_type);
CREATE INDEX idx_etl_errors_step_name    ON etl_execution_errors (step_name);

-- ============================================================
-- TABLE: etl_rejected_records
-- Every record rejected at any step (never lost)
-- ============================================================
CREATE TABLE etl_rejected_records (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id      UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    pipeline_id       UUID        NOT NULL REFERENCES etl_pipelines(id),
    step_name         VARCHAR(100) NOT NULL,
    raw_data          JSONB       NOT NULL,
    rejection_reason  TEXT        NOT NULL,
    validation_errors JSONB,
    rejected_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_rejected_execution_id ON etl_rejected_records (execution_id);
CREATE INDEX idx_etl_rejected_pipeline_id  ON etl_rejected_records (pipeline_id);
CREATE INDEX idx_etl_rejected_step_name    ON etl_rejected_records (step_name);
CREATE INDEX idx_etl_rejected_raw_data     ON etl_rejected_records USING GIN (raw_data);

-- ============================================================
-- TABLE: etl_audit_records
-- Immutable audit log for all ETL actions
-- ============================================================
CREATE TABLE etl_audit_records (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID        REFERENCES etl_pipeline_executions(id),
    pipeline_id  UUID        REFERENCES etl_pipelines(id),
    action       VARCHAR(200) NOT NULL,
    actor_type   VARCHAR(100),
    details      JSONB,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_audit_execution_id ON etl_audit_records (execution_id);
CREATE INDEX idx_etl_audit_pipeline_id  ON etl_audit_records (pipeline_id);
CREATE INDEX idx_etl_audit_action       ON etl_audit_records (action);
CREATE INDEX idx_etl_audit_recorded_at  ON etl_audit_records (recorded_at DESC);
CREATE INDEX idx_etl_audit_details      ON etl_audit_records USING GIN (details);

-- ============================================================
-- TABLE: etl_execution_metrics
-- Numeric metrics per execution (timing, counts, rates)
-- ============================================================
CREATE TABLE etl_execution_metrics (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id UUID        NOT NULL REFERENCES etl_pipeline_executions(id) ON DELETE CASCADE,
    metric_name  VARCHAR(200) NOT NULL,
    metric_value NUMERIC(20, 6) NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_etl_metrics_execution_id ON etl_execution_metrics (execution_id);
CREATE INDEX idx_etl_metrics_metric_name  ON etl_execution_metrics (metric_name);
