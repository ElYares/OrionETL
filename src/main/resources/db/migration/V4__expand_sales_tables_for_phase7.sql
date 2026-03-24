ALTER TABLE sales_transactions
    ALTER COLUMN sale_date TYPE TIMESTAMPTZ
    USING CASE
        WHEN sale_date IS NULL THEN NULL
        ELSE sale_date::timestamp AT TIME ZONE 'UTC'
    END;

ALTER TABLE sales_transactions_staging
    ALTER COLUMN sale_date TYPE TIMESTAMPTZ
    USING CASE
        WHEN sale_date IS NULL THEN NULL
        ELSE sale_date::timestamp AT TIME ZONE 'UTC'
    END;

ALTER TABLE sales_transactions
    ADD COLUMN IF NOT EXISTS product_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS amount_original NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS currency_original VARCHAR(10),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS salesperson_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS channel VARCHAR(50),
    ADD COLUMN IF NOT EXISTS quantity INTEGER,
    ADD COLUMN IF NOT EXISTS discount_rate NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS subtotal NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS total_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE sales_transactions_staging
    ADD COLUMN IF NOT EXISTS product_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS amount_original NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS currency_original VARCHAR(10),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(10),
    ADD COLUMN IF NOT EXISTS salesperson_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS channel VARCHAR(50),
    ADD COLUMN IF NOT EXISTS quantity INTEGER,
    ADD COLUMN IF NOT EXISTS discount_rate NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS subtotal NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS total_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS notes TEXT;
