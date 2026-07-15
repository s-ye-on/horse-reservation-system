ALTER TABLE reservation_change_logs
    DROP CHECK chk_reservation_change_logs_change_type,
    ADD CONSTRAINT chk_reservation_change_logs_change_type
        CHECK (change_type IN ('payment_restored', 'no_show_processed')),
    ADD CONSTRAINT chk_reservation_change_logs_no_show_processed CHECK (
        change_type <> 'no_show_processed'
        OR (
            actor_type = 'admin'
            AND from_status = 'confirmed'
            AND to_status = 'no_show'
            AND from_lesson_date IS NOT NULL
            AND from_start_time IS NOT NULL
            AND to_lesson_date = from_lesson_date
            AND to_start_time = from_start_time
            AND coupon_action IN ('deduct', 'return', 'none')
            AND memo IS NOT NULL
            AND CHAR_LENGTH(TRIM(memo)) BETWEEN 1 AND 500
        )
    );
