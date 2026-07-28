ALTER TABLE reservation_change_logs
    DROP CHECK chk_reservation_change_logs_change_type,
    ADD CONSTRAINT chk_reservation_change_logs_change_type
        CHECK (change_type IN (
            'payment_restored',
            'no_show_processed',
            'schedule_changed',
            'reservation_cancelled',
            'admin_reservation_created'
        )),
    ADD CONSTRAINT chk_reservation_change_logs_admin_reservation_created CHECK (
        change_type <> 'admin_reservation_created'
        OR (
            actor_type = 'admin'
            AND from_status = to_status
            AND to_status IN ('pending_payment', 'confirmed')
            AND from_lesson_date = to_lesson_date
            AND from_start_time = to_start_time
            AND coupon_action = 'none'
            AND memo IS NOT NULL
            AND CHAR_LENGTH(TRIM(memo)) BETWEEN 1 AND 500
        )
    );
