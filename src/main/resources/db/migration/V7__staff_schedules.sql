-- Phase 2b: Staff schedule tables
-- Three tables:
--   staff_schedules        — recurring weekly availability per employee
--   staff_schedule_breaks  — break windows within a scheduled shift
--   staff_schedule_overrides — date-specific overrides (holidays, one-off changes)

-- ──────────────────────────────────────────────────────────────────────────────
-- Recurring weekly schedule
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staff_schedules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id    UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    staff_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- ISO day name stored as string (e.g. 'MONDAY') to match @Enumerated(EnumType.STRING)
    day_of_week    VARCHAR(10) NOT NULL CHECK (day_of_week IN (
                       'MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    start_time     TIME NOT NULL,
    end_time       TIME NOT NULL,
    is_working_day BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- One schedule row per staff member per weekday per business
    CONSTRAINT ck_schedule_times CHECK (end_time > start_time)
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_staff_schedule_day
    ON staff_schedules (staff_id, day_of_week);

CREATE INDEX IF NOT EXISTS idx_staff_schedule_business
    ON staff_schedules (business_id, staff_id);

-- ──────────────────────────────────────────────────────────────────────────────
-- Break windows within a shift
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staff_schedule_breaks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id UUID NOT NULL REFERENCES staff_schedules(id) ON DELETE CASCADE,
    break_start TIME NOT NULL,
    break_end   TIME NOT NULL,
    CONSTRAINT ck_break_times CHECK (break_end > break_start)
);

CREATE INDEX IF NOT EXISTS idx_schedule_breaks_schedule
    ON staff_schedule_breaks (schedule_id);

-- ──────────────────────────────────────────────────────────────────────────────
-- Date-specific overrides (holidays, one-off availability changes)
-- ──────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS staff_schedule_overrides (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id   UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    staff_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    override_date DATE NOT NULL,
    -- is_day_off=true means the employee is absent that day
    is_day_off    BOOLEAN NOT NULL DEFAULT FALSE,
    -- Non-null only when is_day_off=false (partial-day override)
    start_time    TIME,
    end_time      TIME,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_override_times
        CHECK (is_day_off = TRUE OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time))
);

CREATE UNIQUE INDEX IF NOT EXISTS uidx_staff_override_date
    ON staff_schedule_overrides (staff_id, override_date);

CREATE INDEX IF NOT EXISTS idx_staff_override_business
    ON staff_schedule_overrides (business_id, staff_id, override_date);
