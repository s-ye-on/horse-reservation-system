ALTER TABLE reservations
    ADD COLUMN active_slot_guard TINYINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status IN ('pending_admin_approval', 'pending_payment', 'confirmed') THEN 1
                ELSE NULL
            END
        ) STORED,
    ADD CONSTRAINT uk_reservations_active_member_slot
        UNIQUE (member_id, lesson_date, start_time, active_slot_guard);
