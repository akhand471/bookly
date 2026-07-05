-- Phase 3b: Migrate appointments.customer_id from users → customers
--
-- The existing customer_id column references users(id) (Phase 2).
-- Phase 3 introduces a dedicated customers table; this migration replaces the FK.
--
-- Strategy (safe for empty/test data):
--   1. Add a new nullable column customer_entity_id → customers(id)
--   2. Drop the old customer_id column (was FK to users)
--   3. Rename customer_entity_id → customer_id
--   4. Apply NOT NULL constraint
--   5. Recreate indexes referencing the column

-- Step 1: Add the new FK column (nullable initially so the rename works cleanly)
ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS customer_entity_id UUID REFERENCES customers(id);

-- Step 2: Drop old FK constraint and column (FK to users — Phase 2 column)
ALTER TABLE appointments
    DROP COLUMN IF EXISTS customer_id;

-- Step 3: Rename the new column to customer_id
ALTER TABLE appointments
    RENAME COLUMN customer_entity_id TO customer_id;

-- Step 4: Apply NOT NULL now that the rename is done
ALTER TABLE appointments
    ALTER COLUMN customer_id SET NOT NULL;

-- Step 5: Recreate the customer lookup index (originally created in V8 on old column)
DROP INDEX IF EXISTS idx_appointment_customer;
CREATE INDEX IF NOT EXISTS idx_appointment_customer
    ON appointments (business_id, customer_id, start_time DESC);
