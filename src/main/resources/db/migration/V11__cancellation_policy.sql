-- Phase 3c: Cancellation policy per business
-- Businesses can configure a minimum notice period (in hours) for cancellations/reschedules.
-- Default: 2 hours.

ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS cancellation_notice_hours INT NOT NULL DEFAULT 2;

COMMENT ON COLUMN businesses.cancellation_notice_hours IS
    'Minimum hours before appointment start that a cancellation or reschedule is permitted. 0 = no restriction.';
