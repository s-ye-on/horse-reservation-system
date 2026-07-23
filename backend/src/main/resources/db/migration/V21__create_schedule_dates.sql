CREATE TABLE schedule_dates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL,
    resume_status VARCHAR(10) NULL,
    reason VARCHAR(500) NULL,
    changed_by VARCHAR(191) NULL,
    applied_config_version BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_schedule_dates PRIMARY KEY (id),
    CONSTRAINT uk_schedule_dates_date UNIQUE (schedule_date),
    CONSTRAINT chk_schedule_dates_status CHECK (
        status IN ('NORMAL', 'OPEN', 'CLOSING', 'CLOSED')
    ),
    CONSTRAINT chk_schedule_dates_resume_status CHECK (
        resume_status IS NULL OR resume_status IN ('NORMAL', 'OPEN')
    ),
    CONSTRAINT chk_schedule_dates_state CHECK (
        (status = 'CLOSING' AND resume_status IS NOT NULL)
        OR (status <> 'CLOSING' AND resume_status IS NULL)
    ),
    CONSTRAINT chk_schedule_dates_applied_version CHECK (
        applied_config_version >= 1 AND version >= 0
    )
);
