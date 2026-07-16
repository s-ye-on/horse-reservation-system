CREATE INDEX idx_reservation_change_logs_created
    ON reservation_change_logs (created_at, id);
