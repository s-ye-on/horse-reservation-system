CREATE INDEX idx_reservations_member_date_active_interval
    ON reservations (
        member_id,
        lesson_date,
        active_slot_guard,
        start_time,
        id,
        end_time
    );
