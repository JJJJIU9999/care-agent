CREATE SCHEMA IF NOT EXISTS app;

CREATE TABLE app.app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app.service_item (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(32) NOT NULL,
    district VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app.service_slot (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES app.service_item(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    total_capacity INTEGER NOT NULL CHECK (total_capacity > 0),
    remaining_capacity INTEGER NOT NULL CHECK (remaining_capacity >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_at > start_at),
    CHECK (remaining_capacity <= total_capacity)
);

CREATE INDEX service_slot_service_start_idx ON app.service_slot (service_id, start_at);
CREATE INDEX service_slot_start_capacity_idx ON app.service_slot (start_at, remaining_capacity);

CREATE TABLE app.appointment_draft (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app.app_user(id),
    service_id UUID NOT NULL REFERENCES app.service_item(id),
    slot_id UUID NOT NULL REFERENCES app.service_slot(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'USED')),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app.appointment (
    id UUID PRIMARY KEY,
    draft_id UUID NOT NULL UNIQUE REFERENCES app.appointment_draft(id),
    user_id UUID NOT NULL REFERENCES app.app_user(id),
    service_id UUID NOT NULL REFERENCES app.service_item(id),
    slot_id UUID NOT NULL REFERENCES app.service_slot(id),
    confirmed_price NUMERIC(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    idempotency_key VARCHAR(128) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX appointment_user_start_idx ON app.appointment (user_id, confirmed_at DESC);

CREATE TABLE app.critical_audit_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'CREATE_APPOINTMENT', 'CANCEL_APPOINTMENT')),
    user_id UUID REFERENCES app.app_user(id),
    request_id UUID NOT NULL,
    result VARCHAR(32) NOT NULL,
    target_resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX critical_audit_event_request_idx ON app.critical_audit_event (request_id);
