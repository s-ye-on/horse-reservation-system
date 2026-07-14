CREATE TABLE reservation_change_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT NOT NULL,
    actor_auth_subject VARCHAR(191) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    from_lesson_date DATE NULL,
    from_start_time TIME NULL,
    to_lesson_date DATE NULL,
    to_start_time TIME NULL,
    change_type VARCHAR(40) NOT NULL,
    coupon_action VARCHAR(30) NOT NULL,
    memo VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_reservation_change_logs PRIMARY KEY (id),
    CONSTRAINT fk_reservation_change_logs_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT chk_reservation_change_logs_actor_subject
        CHECK (CHAR_LENGTH(TRIM(actor_auth_subject)) BETWEEN 1 AND 191),
    CONSTRAINT chk_reservation_change_logs_actor_type
        CHECK (actor_type IN ('member', 'admin', 'system')),
    CONSTRAINT chk_reservation_change_logs_from_status CHECK (
        from_status IN (
            'pending_admin_approval', 'pending_payment', 'payment_expired', 'confirmed',
            'completed', 'rejected', 'cancelled', 'no_show'
        )
    ),
    CONSTRAINT chk_reservation_change_logs_to_status CHECK (
        to_status IN (
            'pending_admin_approval', 'pending_payment', 'payment_expired', 'confirmed',
            'completed', 'rejected', 'cancelled', 'no_show'
        )
    ),
    CONSTRAINT chk_reservation_change_logs_change_type
        CHECK (change_type IN ('payment_restored')),
    CONSTRAINT chk_reservation_change_logs_coupon_action
        CHECK (coupon_action IN ('none', 'free_change_used', 'deduct', 'return')),
    CONSTRAINT chk_reservation_change_logs_payment_restored CHECK (
        change_type <> 'payment_restored'
        OR (
            actor_type = 'admin'
            AND from_status = 'payment_expired'
            AND to_status = 'confirmed'
            AND from_lesson_date IS NOT NULL
            AND from_start_time IS NOT NULL
            AND to_lesson_date = from_lesson_date
            AND to_start_time = from_start_time
            AND coupon_action = 'none'
            AND memo IS NOT NULL
            AND CHAR_LENGTH(TRIM(memo)) BETWEEN 1 AND 500
        )
    ),
    INDEX idx_reservation_change_logs_reservation_created
        (reservation_id, created_at, id)
);
