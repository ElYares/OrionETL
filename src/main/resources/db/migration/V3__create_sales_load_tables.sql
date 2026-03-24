-- Phase 6 support tables for staging/final load integration tests.

CREATE TABLE sales_transactions (
    transaction_id        VARCHAR(100) PRIMARY KEY,
    customer_id           VARCHAR(100) NOT NULL,
    amount                NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    sale_date             DATE,
    status                VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    etl_execution_id      UUID NOT NULL,
    etl_pipeline_id       UUID NOT NULL,
    etl_source_file       VARCHAR(1000),
    etl_load_timestamp    TIMESTAMPTZ NOT NULL,
    etl_pipeline_version  VARCHAR(50),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sales_transactions_staging (
    transaction_id        VARCHAR(100) NOT NULL,
    customer_id           VARCHAR(100) NOT NULL,
    amount                NUMERIC(18, 2) NOT NULL CHECK (amount >= 0),
    sale_date             DATE,
    status                VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    etl_execution_id      UUID NOT NULL,
    etl_pipeline_id       UUID NOT NULL,
    etl_source_file       VARCHAR(1000),
    etl_load_timestamp    TIMESTAMPTZ NOT NULL,
    etl_pipeline_version  VARCHAR(50)
);

CREATE INDEX idx_sales_transactions_exec_id
    ON sales_transactions (etl_execution_id);

CREATE INDEX idx_sales_transactions_staging_exec_id
    ON sales_transactions_staging (etl_execution_id);
