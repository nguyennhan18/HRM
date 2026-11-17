ALTER TABLE users ADD COLUMN failed_attempts integer;
UPDATE users SET failed_attempts = 0;
ALTER TABLE users ALTER COLUMN failed_attempts SET NOT NULL;
ALTER TABLE users ADD COLUMN locked_until timestamptz;
ALTER TABLE users ADD CONSTRAINT ck_users_failed_attempts CHECK (failed_attempts >= 0);
CREATE TABLE auth_tokens (
 id uuid CONSTRAINT pk_auth_tokens PRIMARY KEY,
 user_id uuid NOT NULL CONSTRAINT fk_auth_tokens_user REFERENCES users(id),
 family_id uuid NOT NULL,
 token_hash varchar(64) NOT NULL CONSTRAINT uq_auth_tokens_hash UNIQUE,
 purpose varchar(10) NOT NULL CONSTRAINT ck_auth_tokens_purpose CHECK (purpose IN ('REFRESH','RESET')),
 expires_at timestamptz NOT NULL,
 used boolean NOT NULL,
 revoked boolean NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 created_by varchar(100) NOT NULL, updated_by varchar(100) NOT NULL, version bigint NOT NULL
);
CREATE INDEX idx_auth_tokens_family ON auth_tokens(family_id);
CREATE INDEX idx_auth_tokens_user ON auth_tokens(user_id);

