-- Multi-device session support: allow multiple refresh tokens per user
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS user_agent VARCHAR(512);

-- Drop the old OneToOne uniqueness constraint on user_id if it exists
-- (the original schema didn't have an explicit unique constraint on user_id,
-- but the app logic was deleting all tokens before creating a new one)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_device ON refresh_tokens(user_id, device_fingerprint);
