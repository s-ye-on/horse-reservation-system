ALTER TABLE reservations
    DROP CHECK chk_reservations_status,
    DROP CHECK chk_reservations_coupon_pending,
    ADD CONSTRAINT chk_reservations_status CHECK (
        status IN (
            'pending_admin_approval',
            'pending_payment',
            'payment_expired',
            'approval_expired',
            'confirmed',
            'completed',
            'rejected',
            'cancelled',
            'no_show'
        )
    ),
    ADD CONSTRAINT chk_reservations_coupon_pending CHECK (
        status NOT IN ('pending_admin_approval', 'approval_expired')
        OR payment_source = 'coupon'
    ),
    ADD INDEX idx_reservations_status_lesson_start (status, lesson_date, start_time);
