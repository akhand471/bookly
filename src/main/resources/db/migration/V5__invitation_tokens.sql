-- Employee invitation tokens
CREATE TABLE IF NOT EXISTS invitation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_invitation_token_hash ON invitation_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_invitation_business ON invitation_tokens(business_id);
CREATE INDEX IF NOT EXISTS idx_invitation_email ON invitation_tokens(email);
