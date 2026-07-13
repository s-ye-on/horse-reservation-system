CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    class_type VARCHAR(40) NOT NULL,
    lesson_date DATE NOT NULL,
    start_time TIME NOT NULL,
    status VARCHAR(40) NOT NULL,
    payment_source VARCHAR(30) NOT NULL,
    coupon_id BIGINT NULL,
    payment_due_at DATETIME(6) NULL,
    approval_requested_at DATETIME(6) NOT NULL,
    admin_confirmed_at DATETIME(6) NULL,
    rejected_at DATETIME(6) NULL,
    rejected_by VARCHAR(191) NULL,
    rejection_reason VARCHAR(500) NULL,
    cancelled_at DATETIME(6) NULL,
    cancellation_responsibility VARCHAR(20) NULL,
    coupon_action VARCHAR(20) NULL,
    admin_memo VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT fk_reservations_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_reservations_coupon_member
        FOREIGN KEY (coupon_id, member_id) REFERENCES coupons (id, member_id),
    CONSTRAINT chk_reservations_class_type CHECK (
        class_type IN (
            'FIRST_RIDE',
            'ROUND_BEGINNER',
            'ROUND_TROT',
            'LARGE_ARENA_BEGINNER',
            'LARGE_ARENA_TROT',
            'DRESSAGE',
            'JUMPING'
        )
    ),
    CONSTRAINT chk_reservations_status CHECK (
        status IN (
            'pending_admin_approval',
            'pending_payment',
            'payment_expired',
            'confirmed',
            'completed',
            'rejected',
            'cancelled',
            'no_show'
        )
    ),
    CONSTRAINT chk_reservations_payment_source CHECK (
        (payment_source = 'coupon' AND coupon_id IS NOT NULL AND payment_due_at IS NULL)
        OR (payment_source = 'single_payment' AND coupon_id IS NULL)
    ),
    CONSTRAINT chk_reservations_coupon_pending CHECK (
        status <> 'pending_admin_approval' OR payment_source = 'coupon'
    ),
    CONSTRAINT chk_reservations_payment_pending CHECK (
        status NOT IN ('pending_payment', 'payment_expired')
        OR (payment_source = 'single_payment' AND payment_due_at IS NOT NULL)
    ),
    CONSTRAINT chk_reservations_confirmation_audit CHECK (
        status NOT IN ('confirmed', 'completed', 'no_show') OR admin_confirmed_at IS NOT NULL
    ),
    CONSTRAINT chk_reservations_rejection_audit CHECK (
        (
            status = 'rejected'
            AND rejected_at IS NOT NULL
            AND rejected_by IS NOT NULL
            AND rejection_reason IS NOT NULL
        )
        OR (
            status <> 'rejected'
            AND rejected_at IS NULL
            AND rejected_by IS NULL
            AND rejection_reason IS NULL
        )
    ),
    CONSTRAINT chk_reservations_cancellation_audit CHECK (
        (
            status = 'cancelled'
            AND cancelled_at IS NOT NULL
            AND cancellation_responsibility IS NOT NULL
        )
        OR (
            status <> 'cancelled'
            AND cancelled_at IS NULL
            AND cancellation_responsibility IS NULL
        )
    ),
    CONSTRAINT chk_reservations_cancellation_responsibility CHECK (
        cancellation_responsibility IS NULL
        OR cancellation_responsibility IN ('member', 'stable', 'exception')
    ),
    CONSTRAINT chk_reservations_coupon_action CHECK (
        coupon_action IS NULL OR coupon_action IN ('deduct', 'return', 'none')
    ),
    CONSTRAINT chk_reservations_version CHECK (version >= 0),
    INDEX idx_reservations_occupancy (lesson_date, start_time, status, class_type),
    INDEX idx_reservations_member_lesson (member_id, lesson_date, start_time),
    INDEX idx_reservations_payment_due (status, payment_due_at)
);

ALTER TABLE coupon_usage_logs
    ADD CONSTRAINT fk_coupon_usage_logs_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations (id);
