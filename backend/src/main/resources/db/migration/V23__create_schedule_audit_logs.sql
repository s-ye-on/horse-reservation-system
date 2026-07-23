CREATE TABLE schedule_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(30) NOT NULL,
    target_key VARCHAR(191) NOT NULL,
    action VARCHAR(50) NOT NULL,
    from_state JSON NULL,
    to_state JSON NULL,
    actor_auth_subject VARCHAR(191) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_schedule_audit_logs PRIMARY KEY (id),
    CONSTRAINT chk_schedule_audit_logs_target_type CHECK (
        target_type IN ('TEMPLATE', 'RECURRING_HOLIDAY', 'SCHEDULE_DATE', 'TIME_SLOT')
    ),
    CONSTRAINT chk_schedule_audit_logs_required_text CHECK (
        CHAR_LENGTH(TRIM(target_key)) > 0
        AND CHAR_LENGTH(TRIM(action)) > 0
        AND CHAR_LENGTH(TRIM(actor_auth_subject)) > 0
        AND CHAR_LENGTH(TRIM(reason)) > 0
    ),
    INDEX idx_schedule_audit_logs_target_created (
        target_type,
        target_key,
        created_at,
        id
    )
);
