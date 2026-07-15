ALTER TABLE reservation_change_logs
    DROP CHECK chk_reservation_change_logs_change_type,
    ADD CONSTRAINT chk_reservation_change_logs_change_type
        CHECK (change_type IN (
            'payment_restored',
            'no_show_processed',
            'schedule_changed',
            'reservation_cancelled'
        )),
    ADD CONSTRAINT chk_reservation_change_logs_reservation_cancelled CHECK (
        change_type <> 'reservation_cancelled'
        OR (
            actor_type IN ('member', 'admin')
            AND from_status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
            AND to_status = 'cancelled'
            AND from_lesson_date = to_lesson_date
            AND from_start_time = to_start_time
            AND coupon_action IN ('deduct', 'return', 'none')
            AND memo IS NOT NULL
            AND CHAR_LENGTH(TRIM(memo)) BETWEEN 1 AND 500
        )
    );
