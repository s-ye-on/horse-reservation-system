ALTER TABLE reservation_change_logs
    DROP CHECK chk_reservation_change_logs_change_type,
    ADD CONSTRAINT chk_reservation_change_logs_change_type
        CHECK (change_type IN ('payment_restored', 'no_show_processed', 'schedule_changed')),
    ADD CONSTRAINT chk_reservation_change_logs_schedule_changed CHECK (
        change_type <> 'schedule_changed'
        OR (
            actor_type IN ('member', 'admin')
            AND from_status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
            AND to_status = from_status
            AND from_lesson_date IS NOT NULL
            AND from_start_time IS NOT NULL
            AND to_lesson_date IS NOT NULL
            AND to_start_time IS NOT NULL
            AND (
                to_lesson_date <> from_lesson_date
                OR to_start_time <> from_start_time
            )
            AND coupon_action = 'none'
            AND (
                (
                    actor_type = 'member'
                    AND (
                        memo IS NULL
                        OR CHAR_LENGTH(TRIM(memo)) BETWEEN 1 AND 500
                    )
                )
                OR (
                    actor_type = 'admin'
                    AND memo IS NOT NULL
                    AND CHAR_LENGTH(TRIM(memo)) BETWEEN 1 AND 500
                )
            )
        )
    );
