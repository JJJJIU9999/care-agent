CREATE TABLE app.conversation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app.app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    last_active_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX conversation_user_active_idx ON app.conversation (user_id, last_active_at DESC);

CREATE TABLE app.message_metadata (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES app.conversation(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    char_count INTEGER NOT NULL CHECK (char_count >= 0),
    token_count INTEGER,
    model VARCHAR(100),
    duration_ms BIGINT,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX message_metadata_conversation_created_idx
    ON app.message_metadata (conversation_id, created_at);
