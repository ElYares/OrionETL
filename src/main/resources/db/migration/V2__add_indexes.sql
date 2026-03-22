-- OrionETL V2: Additional indexes and constraints for Phase 3 persistence performance

CREATE UNIQUE INDEX IF NOT EXISTS uq_etl_steps_execution_step_name
    ON etl_execution_steps (execution_id, step_name);

CREATE INDEX IF NOT EXISTS idx_etl_steps_order
    ON etl_execution_steps (execution_id, step_order);

CREATE INDEX IF NOT EXISTS idx_etl_errors_code
    ON etl_execution_errors (error_code);

CREATE INDEX IF NOT EXISTS idx_etl_rejected_errors
    ON etl_rejected_records USING GIN (validation_errors);

CREATE INDEX IF NOT EXISTS idx_etl_rejected_at
    ON etl_rejected_records (rejected_at DESC);

CREATE INDEX IF NOT EXISTS idx_etl_metrics_recorded_at
    ON etl_execution_metrics (recorded_at DESC);
