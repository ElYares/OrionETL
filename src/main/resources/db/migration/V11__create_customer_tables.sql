CREATE TABLE customers (
    crm_customer_id       VARCHAR(100),
    document_type         VARCHAR(20) NOT NULL,
    document_number       VARCHAR(100) NOT NULL,
    first_name            VARCHAR(200) NOT NULL,
    last_name             VARCHAR(200) NOT NULL,
    email                 VARCHAR(320) NOT NULL,
    phone                 VARCHAR(40),
    country_code          VARCHAR(2) NOT NULL,
    registration_date     TIMESTAMPTZ,
    status                VARCHAR(50) NOT NULL,
    preferred_language    VARCHAR(35),
    birth_date            TIMESTAMPTZ,
    gender                VARCHAR(20),
    customer_type         VARCHAR(50),
    etl_execution_id      UUID NOT NULL,
    etl_pipeline_id       UUID NOT NULL,
    etl_source_file       VARCHAR(1000),
    etl_load_timestamp    TIMESTAMPTZ NOT NULL,
    etl_pipeline_version  VARCHAR(50),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (document_type, document_number)
);

CREATE TABLE customers_staging (
    crm_customer_id       VARCHAR(100),
    document_type         VARCHAR(20) NOT NULL,
    document_number       VARCHAR(100) NOT NULL,
    first_name            VARCHAR(200) NOT NULL,
    last_name             VARCHAR(200) NOT NULL,
    email                 VARCHAR(320) NOT NULL,
    phone                 VARCHAR(40),
    country_code          VARCHAR(2) NOT NULL,
    registration_date     TIMESTAMPTZ,
    status                VARCHAR(50) NOT NULL,
    preferred_language    VARCHAR(35),
    birth_date            TIMESTAMPTZ,
    gender                VARCHAR(20),
    customer_type         VARCHAR(50),
    etl_execution_id      UUID NOT NULL,
    etl_pipeline_id       UUID NOT NULL,
    etl_source_file       VARCHAR(1000),
    etl_load_timestamp    TIMESTAMPTZ NOT NULL,
    etl_pipeline_version  VARCHAR(50)
);

CREATE INDEX idx_customers_exec_id
    ON customers (etl_execution_id);

CREATE INDEX idx_customers_staging_exec_id
    ON customers_staging (etl_execution_id);
