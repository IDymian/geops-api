-- Identity: user accounts and refresh tokens.

CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    email         varchar(254) NOT NULL UNIQUE,  -- stored lowercase
    password_hash varchar(100) NOT NULL,         -- BCrypt
    full_name     varchar(120) NOT NULL,
    role          varchar(20)  NOT NULL CHECK (role IN ('CONSUMER', 'PROVIDER')),
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL
);

-- Only a SHA-256 hash of each refresh token is stored. Tokens of one login
-- share a family_id: reusing a rotated token revokes the whole family.
CREATE TABLE refresh_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    family_id  uuid        NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
