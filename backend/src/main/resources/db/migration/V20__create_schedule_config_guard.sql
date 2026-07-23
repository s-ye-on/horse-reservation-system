CREATE TABLE schedule_config_guard (
    id TINYINT NOT NULL,
    status VARCHAR(10) NOT NULL,
    active_version BIGINT NOT NULL,
    pending_version BIGINT NULL,
    sync_started_at DATETIME(6) NULL,
    sync_started_by VARCHAR(191) NULL,
    last_completed_at DATETIME(6) NULL,
    last_failed_at DATETIME(6) NULL,
    last_failure_code VARCHAR(100) NULL,
    last_failure_summary VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_schedule_config_guard PRIMARY KEY (id),
    CONSTRAINT chk_schedule_config_guard_singleton CHECK (id = 1),
    CONSTRAINT chk_schedule_config_guard_status CHECK (status IN ('ACTIVE', 'SYNCING')),
    CONSTRAINT chk_schedule_config_guard_versions CHECK (
        active_version >= 1
        AND (pending_version IS NULL OR pending_version > active_version)
        AND version >= 0
    ),
    CONSTRAINT chk_schedule_config_guard_state CHECK (
        (
            status = 'ACTIVE'
            AND pending_version IS NULL
            AND sync_started_at IS NULL
            AND sync_started_by IS NULL
        )
        OR (
            status = 'SYNCING'
            AND pending_version IS NOT NULL
            AND sync_started_at IS NOT NULL
            AND CHAR_LENGTH(TRIM(sync_started_by)) > 0
        )
    )
);

INSERT INTO schedule_config_guard (
    id,
    status,
    active_version,
    pending_version,
    version
) VALUES (
    1,
    'ACTIVE',
    1,
    NULL,
    0
);
