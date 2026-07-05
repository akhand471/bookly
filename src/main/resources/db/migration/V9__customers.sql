-- Phase 3a: Customers table
-- Customers are distinct per-business — same email at Business A is a different customer than at Business B.
-- Tenant-isolated via business_id FK + unique constraint on (business_id, email).

CREATE TABLE IF NOT EXISTS customers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    phone        VARCHAR(50),
    notes        TEXT,
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Same email address is a separate customer record for each business (multi-tenant isolation)
    CONSTRAINT uq_customer_business_email UNIQUE (business_id, email)
);

-- Lookup by business tenant
CREATE INDEX IF NOT EXISTS idx_customer_business
    ON customers (business_id, created_at DESC);

-- Fast email lookup during guest findOrCreate
CREATE INDEX IF NOT EXISTS idx_customer_business_email
    ON customers (business_id, email);
