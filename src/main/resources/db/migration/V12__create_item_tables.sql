CREATE TABLE etl_items_staging (
    item_key TEXT,
    item_name TEXT,
    description TEXT,
    unit_price NUMERIC,
    manufacturer_country TEXT,
    supplier_name TEXT,
    unit TEXT,
    etl_execution_id UUID,
    etl_pipeline_id UUID,
    etl_source_file TEXT,
    etl_load_timestamp TIMESTAMP,
    etl_pipeline_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE etl_items (
    item_key TEXT PRIMARY KEY,
    item_name TEXT,
    description TEXT,
    unit_price NUMERIC,
    manufacturer_country TEXT,
    supplier_name TEXT,
    unit TEXT,
    etl_execution_id UUID,
    etl_pipeline_id UUID,
    etl_source_file TEXT,
    etl_load_timestamp TIMESTAMP,
    etl_pipeline_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
