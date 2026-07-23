CREATE TABLE recurring_holiday_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    day_of_week VARCHAR(9) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    reason VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(191) NOT NULL,
    updated_by VARCHAR(191) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_recurring_holiday_rules PRIMARY KEY (id),
    CONSTRAINT chk_recurring_holiday_rules_day CHECK (
        day_of_week IN (
            'MONDAY',
            'TUESDAY',
            'WEDNESDAY',
            'THURSDAY',
            'FRIDAY',
            'SATURDAY',
            'SUNDAY'
        )
    ),
    CONSTRAINT chk_recurring_holiday_rules_date_range CHECK (
        effective_to IS NULL OR effective_from <= effective_to
    ),
    CONSTRAINT chk_recurring_holiday_rules_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_recurring_holiday_rules_version CHECK (version >= 0),
    CONSTRAINT chk_recurring_holiday_rules_actors CHECK (
        CHAR_LENGTH(TRIM(created_by)) > 0 AND CHAR_LENGTH(TRIM(updated_by)) > 0
    ),
    INDEX idx_recurring_holiday_rules_day_active_dates (
        day_of_week,
        active,
        effective_from,
        effective_to,
        id
    )
);
