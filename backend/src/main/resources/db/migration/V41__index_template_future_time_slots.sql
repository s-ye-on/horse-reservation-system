CREATE INDEX idx_time_slot_template_lesson_start
    ON time_slot_capacities (template_id, lesson_date, start_time);
