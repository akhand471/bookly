-- Phase 2d: Appointments table with optimistic locking + double-booking constraint
--
-- Double-booking is prevented by two independent guards:
--   1. @Version (optimistic locking) in the Appointment entity — first writer wins
--      under concurrent JPA transactions targeting the same row.
--   2. UNIQUE(staff_id, start_time) — the DB-level unique constraint is the final
--      safety net that catches races not resolved by optimistic locking (e.g. two
--      new inserts arriving simultaneously for the same slot).

CREATE TYPE appointment_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'CANCELLED',
    'COMPLETED',
    'NO_SHOW'
);

CREATE TABLE IF NOT EXISTS appointments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    service_id   UUID NOT NULL REFERENCES services(id),
    staff_id     UUID NOT NULL REFERENCES users(id),
    customer_id  UUID NOT NULL REFERENCES users(id),
    start_time   TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time     TIMESTAMP WITH TIME ZONE NOT NULL,
    status       appointment_status NOT NULL DEFAULT 'PENDING',
    notes        TEXT,
    -- Optimistic lock version; incremented by Hibernate on every UPDATE
    version      BIGINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_appointment_times CHECK (end_time > start_time)
);

-- ── Double-booking guard ──────────────────────────────────────────────────────
-- A staff member cannot have two active appointments starting at the same time.
-- CANCELLED appointments are excluded to allow rebooking freed slots.
CREATE UNIQUE INDEX IF NOT EXISTS uidx_appointment_staff_slot
    ON appointments (staff_id, start_time)
    WHERE status NOT IN ('CANCELLED');

-- ── Query optimisation indexes ─────────────────────────────────────────────────
-- Listing by tenant + time range (most common query pattern)
CREATE INDEX IF NOT EXISTS idx_appointment_business_time
    ON appointments (business_id, start_time DESC);

-- Customer's own appointment history
CREATE INDEX IF NOT EXISTS idx_appointment_customer
    ON appointments (business_id, customer_id, start_time DESC);

-- Staff workload view
CREATE INDEX IF NOT EXISTS idx_appointment_staff_time
    ON appointments (business_id, staff_id, start_time);

-- Status filtering
CREATE INDEX IF NOT EXISTS idx_appointment_status
    ON appointments (business_id, status);
