CREATE INDEX idx_reservations_lesson_status
    ON reservations (lesson_date, status);
