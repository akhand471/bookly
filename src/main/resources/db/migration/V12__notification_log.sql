-- Phase 3d: Notification log
-- Tracks every email notification attempt (idempotency + audit trail).
-- A unique partial index on (appointment_id, type) WHERE status='SENT' prevents duplicate sends.

CREATE TYPE notification_type AS ENUM (
    'BOOKING_CONFIRMATION',
    'CANCELLATION',
    'RESCHEDULE_CONFIRMATION',
    'REMINDER_24H'
);

CREATE TYPE notification_status AS ENUM (
    'SENT',
    'FAILED'
);

CREATE TABLE IF NOT EXISTS notification_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    appointment_id  UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    customer_email  VARCHAR(255) NOT NULL,
    type            notification_type NOT NULL,
    status          notification_status NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Idempotency guard ─────────────────────────────────────────────────────────
-- Only one SENT log entry is allowed per (appointment, notification type).
-- FAILED entries are allowed to accumulate (for retry diagnostics).
CREATE UNIQUE INDEX IF NOT EXISTS uidx_notification_sent
    ON notification_log (appointment_id, type)
    WHERE status = 'SENT';

-- Tenant-scoped query (business owner views notification history)
CREATE INDEX IF NOT EXISTS idx_notification_business
    ON notification_log (business_id, created_at DESC);

-- Scheduler queries by appointment to check reminder status
CREATE INDEX IF NOT EXISTS idx_notification_appointment
    ON notification_log (appointment_id, type);
