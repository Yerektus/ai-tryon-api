CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255),
    display_name VARCHAR(120) NOT NULL,
    google_sub VARCHAR(255),
    credits_balance INTEGER NOT NULL DEFAULT 5 CHECK (credits_balance >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_users_email ON users (email);
CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE payment_packages (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    title VARCHAR(120) NOT NULL,
    credits INTEGER NOT NULL CHECK (credits > 0),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_payment_packages_code ON payment_packages (code);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payment_package_id UUID NOT NULL REFERENCES payment_packages(id),
    provider VARCHAR(32) NOT NULL,
    provider_invoice_id VARCHAR(128),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    redirect_url TEXT,
    provider_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_payments_provider_invoice_id ON payments (provider_invoice_id) WHERE provider_invoice_id IS NOT NULL;
CREATE INDEX idx_payments_user_created ON payments (user_id, created_at DESC);

CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_payment_webhook_provider_event_id ON payment_webhook_events (provider_event_id);

CREATE TABLE try_on_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    person_image BYTEA NOT NULL,
    person_image_mime VARCHAR(64) NOT NULL,
    clothing_image BYTEA NOT NULL,
    clothing_image_mime VARCHAR(64) NOT NULL,
    result_image BYTEA,
    result_image_mime VARCHAR(64),
    clothing_name VARCHAR(120) NOT NULL,
    clothing_size VARCHAR(32) NOT NULL,
    height_cm INTEGER NOT NULL,
    weight_kg INTEGER NOT NULL,
    gender VARCHAR(16) NOT NULL,
    age_years INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    credits_spent INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_try_on_jobs_user_created ON try_on_jobs (user_id, created_at DESC);

CREATE TABLE credit_ledger (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delta INTEGER NOT NULL,
    balance_after INTEGER NOT NULL CHECK (balance_after >= 0),
    reason VARCHAR(64) NOT NULL,
    payment_id UUID REFERENCES payments(id),
    try_on_job_id UUID REFERENCES try_on_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_ledger_user_created ON credit_ledger (user_id, created_at DESC);
