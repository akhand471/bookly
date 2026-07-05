-- Phase 2a: Bookable services table
-- Each service (e.g. "Haircut", "Massage") is scoped to a business (tenant).
-- Soft-delete via is_active flag — records are never physically removed.

CREATE TABLE IF NOT EXISTS services (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id      UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name             VARCHAR(150) NOT NULL,
    description      TEXT,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    price            NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Prevent duplicate service names within the same business
CREATE UNIQUE INDEX IF NOT EXISTS uidx_services_name_business
    ON services (business_id, lower(name))
    WHERE is_active = TRUE;

-- Optimise tenant-scoped listing queries
CREATE INDEX IF NOT EXISTS idx_services_business_active
    ON services (business_id, is_active);
