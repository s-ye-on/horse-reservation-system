CREATE TABLE auth_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NULL,
    auth_subject VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    normalized_email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    bootstrap_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_auth_accounts_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uk_auth_accounts_member UNIQUE (member_id),
    CONSTRAINT uk_auth_accounts_subject UNIQUE (auth_subject),
    CONSTRAINT uk_auth_accounts_normalized_email UNIQUE (normalized_email),
    CONSTRAINT uk_auth_accounts_bootstrap_key UNIQUE (bootstrap_key),
    CONSTRAINT chk_auth_accounts_role
        CHECK (role IN ('MEMBER', 'ADMIN')),
    CONSTRAINT chk_auth_accounts_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'WITHDRAWN')),
    CONSTRAINT chk_auth_accounts_member_role
        CHECK ((role = 'MEMBER' AND member_id IS NOT NULL)
            OR (role = 'ADMIN' AND member_id IS NULL)),
    CONSTRAINT chk_auth_accounts_bootstrap
        CHECK (bootstrap_key IS NULL
            OR (bootstrap_key = 'INITIAL_ADMIN' AND role = 'ADMIN'))
);

CREATE TABLE refresh_token_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auth_account_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    family_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_session_id BIGINT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    rotated_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_sessions_account
        FOREIGN KEY (auth_account_id) REFERENCES auth_accounts (id),
    CONSTRAINT fk_refresh_token_sessions_parent
        FOREIGN KEY (parent_session_id) REFERENCES refresh_token_sessions (id),
    CONSTRAINT uk_refresh_token_sessions_hash UNIQUE (token_hash),
    CONSTRAINT uk_refresh_token_sessions_parent UNIQUE (parent_session_id),
    CONSTRAINT chk_refresh_token_sessions_status
        CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED')),
    CONSTRAINT chk_refresh_token_sessions_rotation
        CHECK ((status = 'ROTATED' AND rotated_at IS NOT NULL)
            OR (status <> 'ROTATED' AND rotated_at IS NULL)),
    CONSTRAINT chk_refresh_token_sessions_revocation
        CHECK ((status = 'REVOKED' AND revoked_at IS NOT NULL)
            OR (status <> 'REVOKED' AND revoked_at IS NULL)),
    INDEX idx_refresh_token_sessions_account_status_expiry
        (auth_account_id, status, expires_at, id),
    INDEX idx_refresh_token_sessions_family (family_id, id),
    INDEX idx_refresh_token_sessions_expiry (expires_at, id)
);
