CREATE TABLE IF NOT EXISTS clients (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    client_id VARCHAR(255) NOT NULL UNIQUE,
    client_secret VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    redirect_uri VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL DEFAULT 'api.read',
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_clients (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, client_id)
);

CREATE TABLE IF NOT EXISTS social_accounts (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    provider VARCHAR(100) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_social_provider_account UNIQUE (provider, provider_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE
);

-- Upgrade databases previously managed by Hibernate. Existing plaintext refresh
-- tokens are intentionally revoked rather than copied into the hashed column.
ALTER TABLE clients ADD COLUMN IF NOT EXISTS scopes VARCHAR(1000) DEFAULT 'api.read';
UPDATE clients SET scopes = 'api.read' WHERE scopes IS NULL;
ALTER TABLE clients ALTER COLUMN scopes SET NOT NULL;

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS client_id UUID;
DELETE FROM refresh_tokens WHERE token_hash IS NULL OR client_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN token_hash SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS token;

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_clients_client_id ON clients(client_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);

INSERT INTO roles (id, created_at, updated_at, name)
SELECT CAST('00000000-0000-0000-0000-000000000001' AS UUID), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');

INSERT INTO roles (id, created_at, updated_at, name)
SELECT CAST('00000000-0000-0000-0000-000000000002' AS UUID), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');

INSERT INTO permissions (id, created_at, updated_at, name, description)
SELECT CAST('00000000-0000-0000-0000-000000000101' AS UUID), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       'PROFILE_READ', 'Read the authenticated user profile'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'PROFILE_READ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_USER' AND p.name = 'PROFILE_READ'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMP WITH TIME ZONE,
    authorization_code_expires_at TIMESTAMP WITH TIME ZONE,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMP WITH TIME ZONE,
    access_token_expires_at TIMESTAMP WITH TIME ZONE,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_expires_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE,
    refresh_token_expires_at TIMESTAMP WITH TIME ZONE,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMP WITH TIME ZONE,
    user_code_expires_at TIMESTAMP WITH TIME ZONE,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMP WITH TIME ZONE,
    device_code_expires_at TIMESTAMP WITH TIME ZONE,
    device_code_metadata TEXT
);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
