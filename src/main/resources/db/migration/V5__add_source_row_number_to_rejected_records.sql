ALTER TABLE etl_rejected_records
    ADD COLUMN IF NOT EXISTS source_row_number BIGINT;

UPDATE etl_rejected_records
SET source_row_number = 0
WHERE source_row_number IS NULL;

ALTER TABLE etl_rejected_records
    ALTER COLUMN source_row_number SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_etl_rejected_source_row_number
    ON etl_rejected_records (source_row_number);
