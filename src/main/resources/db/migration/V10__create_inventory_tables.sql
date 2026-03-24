CREATE TABLE inventory_levels (
    sku                   VARCHAR(100) NOT NULL,
    warehouse_id          VARCHAR(100) NOT NULL,
    quantity_on_hand      BIGINT NOT NULL CHECK (quantity_on_hand >= 0),
    quantity_reserved     BIGINT NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    unit_cost             NUMERIC(18, 2),
    unit_cost_original    NUMERIC(18, 2),
    currency_original     VARCHAR(10),
    cost_currency         VARCHAR(10),
    last_updated          TIMESTAMPTZ,
    etl_execution_id      UUID NOT NULL,
    etl_pipeline_id       UUID NOT NULL,
    etl_source_file       VARCHAR(1000),
    etl_load_timestamp    TIMESTAMPTZ NOT NULL,
    etl_pipeline_version  VARCHAR(50),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sku, warehouse_id)
);

CREATE TABLE inventory_levels_staging (
    sku                   VARCHAR(100) NOT NULL,
    warehouse_id          VARCHAR(100) NOT NULL,
    quantity_on_hand      BIGINT NOT NULL CHECK (quantity_on_hand >= 0),
    quantity_reserved     BIGINT NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    unit_cost             NUMERIC(18, 2),
    unit_cost_original    NUMERIC(18, 2),
    currency_original     VARCHAR(10),
    cost_currency         VARCHAR(10),
    last_updated          TIMESTAMPTZ,
    etl_execution_id      UUID NOT NULL,
    etl_pipeline_id       UUID NOT NULL,
    etl_source_file       VARCHAR(1000),
    etl_load_timestamp    TIMESTAMPTZ NOT NULL,
    etl_pipeline_version  VARCHAR(50)
);

CREATE INDEX idx_inventory_levels_exec_id
    ON inventory_levels (etl_execution_id);

CREATE INDEX idx_inventory_levels_staging_exec_id
    ON inventory_levels_staging (etl_execution_id);
