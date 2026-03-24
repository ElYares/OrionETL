CREATE TABLE ${SCAFFOLD_TABLE_PREFIX}_staging (
    ${SCAFFOLD_BUSINESS_KEY} TEXT,
    -- TODO: agrega aquí columnas reales del negocio
    etl_execution_id UUID,
    etl_pipeline_id UUID,
    etl_source_file TEXT,
    etl_load_timestamp TIMESTAMP,
    etl_pipeline_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE ${SCAFFOLD_TABLE_PREFIX} (
    ${SCAFFOLD_BUSINESS_KEY} TEXT PRIMARY KEY,
    -- TODO: agrega aquí columnas reales del negocio
    etl_execution_id UUID,
    etl_pipeline_id UUID,
    etl_source_file TEXT,
    etl_load_timestamp TIMESTAMP,
    etl_pipeline_version VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
