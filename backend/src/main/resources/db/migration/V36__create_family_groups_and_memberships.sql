CREATE TABLE family_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    dissolved_at DATETIME(6) NULL,
    CONSTRAINT pk_family_groups PRIMARY KEY (id),
    CONSTRAINT chk_family_groups_name
        CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 100),
    CONSTRAINT chk_family_groups_status
        CHECK (status IN ('ACTIVE', 'DISSOLVED')),
    CONSTRAINT chk_family_groups_dissolution CHECK (
        (status = 'ACTIVE' AND dissolved_at IS NULL)
        OR (status = 'DISSOLVED' AND dissolved_at IS NOT NULL)
    ),
    INDEX idx_family_groups_status_created (status, created_at, id)
);

CREATE TABLE family_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_group_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at DATETIME(6) NULL,
    active_member_guard BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN ended_at IS NULL THEN member_id
                ELSE NULL
            END
        ) STORED,
    CONSTRAINT pk_family_memberships PRIMARY KEY (id),
    CONSTRAINT fk_family_memberships_group
        FOREIGN KEY (family_group_id) REFERENCES family_groups (id),
    CONSTRAINT fk_family_memberships_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uk_family_memberships_active_member UNIQUE (active_member_guard),
    CONSTRAINT chk_family_memberships_period
        CHECK (ended_at IS NULL OR ended_at >= joined_at),
    INDEX idx_family_memberships_group_active (family_group_id, ended_at, id),
    INDEX idx_family_memberships_member_history (member_id, joined_at, id)
);

CREATE TABLE family_group_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    family_group_id BIGINT NOT NULL,
    member_id BIGINT NULL,
    action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_state JSON NULL,
    to_state JSON NULL,
    actor_auth_subject VARCHAR(191) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_family_group_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_family_group_audit_logs_group
        FOREIGN KEY (family_group_id) REFERENCES family_groups (id),
    CONSTRAINT fk_family_group_audit_logs_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_family_group_audit_logs_action CHECK (
        action IN ('GROUP_CREATED', 'MEMBER_ADDED', 'MEMBER_REMOVED', 'GROUP_DISSOLVED')
    ),
    CONSTRAINT chk_family_group_audit_logs_required_text CHECK (
        CHAR_LENGTH(TRIM(actor_auth_subject)) > 0
        AND CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500
    ),
    CONSTRAINT chk_family_group_audit_logs_shape CHECK (
        (action = 'GROUP_CREATED'
            AND member_id IS NULL
            AND from_state IS NULL
            AND to_state IS NOT NULL)
        OR (action IN ('MEMBER_ADDED', 'MEMBER_REMOVED')
            AND member_id IS NOT NULL
            AND from_state IS NOT NULL
            AND to_state IS NOT NULL)
        OR (action = 'GROUP_DISSOLVED'
            AND member_id IS NULL
            AND from_state IS NOT NULL
            AND to_state IS NOT NULL)
    ),
    INDEX idx_family_group_audit_logs_group_created (family_group_id, created_at, id),
    INDEX idx_family_group_audit_logs_member_created (member_id, created_at, id)
);
