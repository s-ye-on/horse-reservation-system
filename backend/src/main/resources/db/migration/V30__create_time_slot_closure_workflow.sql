CREATE TABLE time_slot_closures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    time_slot_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    started_by VARCHAR(191) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_by VARCHAR(191) NULL,
    completed_at DATETIME(6) NULL,
    withdrawn_by VARCHAR(191) NULL,
    withdrawn_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_guard TINYINT
        GENERATED ALWAYS AS (CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_time_slot_closures_time_slot
        FOREIGN KEY (time_slot_id) REFERENCES time_slot_capacities (id),
    CONSTRAINT chk_time_slot_closures_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'WITHDRAWN')),
    CONSTRAINT chk_time_slot_closures_transition_columns CHECK (
        (status = 'IN_PROGRESS'
            AND completed_by IS NULL AND completed_at IS NULL
            AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'COMPLETED'
            AND completed_by IS NOT NULL AND completed_at IS NOT NULL
            AND withdrawn_by IS NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN'
            AND withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL
            AND completed_by IS NULL AND completed_at IS NULL)
    ),
    UNIQUE KEY uk_time_slot_closures_active (time_slot_id, active_guard),
    KEY idx_time_slot_closures_history (time_slot_id, created_at, id)
);

CREATE TABLE time_slot_closure_impacts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    closure_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    reservation_status_at_start VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_time_slot_closure_impacts_closure
        FOREIGN KEY (closure_id) REFERENCES time_slot_closures (id),
    CONSTRAINT fk_time_slot_closure_impacts_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    UNIQUE KEY uk_time_slot_closure_impacts_membership (closure_id, reservation_id),
    KEY idx_time_slot_closure_impacts_reservation (reservation_id, closure_id)
);

INSERT INTO time_slot_closures (
    time_slot_id,
    status,
    reason,
    started_by,
    started_at,
    completed_by,
    completed_at
)
SELECT
    time_slot.id,
    CASE WHEN EXISTS (
        SELECT 1
        FROM reservations reservation
        WHERE reservation.lesson_date = time_slot.lesson_date
          AND reservation.start_time = time_slot.start_time
          AND reservation.status IN ('PENDING_ADMIN_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED')
    ) THEN 'IN_PROGRESS' ELSE 'COMPLETED' END,
    'V30 기존 관리자 마감 이관',
    'migration:v30',
    time_slot.updated_at,
    CASE WHEN EXISTS (
        SELECT 1
        FROM reservations reservation
        WHERE reservation.lesson_date = time_slot.lesson_date
          AND reservation.start_time = time_slot.start_time
          AND reservation.status IN ('PENDING_ADMIN_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED')
    ) THEN NULL ELSE 'migration:v30' END,
    CASE WHEN EXISTS (
        SELECT 1
        FROM reservations reservation
        WHERE reservation.lesson_date = time_slot.lesson_date
          AND reservation.start_time = time_slot.start_time
          AND reservation.status IN ('PENDING_ADMIN_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED')
    ) THEN NULL ELSE time_slot.updated_at END
FROM time_slot_capacities time_slot
WHERE time_slot.admin_closed = TRUE;

INSERT INTO time_slot_closure_impacts (
    closure_id,
    reservation_id,
    reservation_status_at_start
)
SELECT
    closure.id,
    reservation.id,
    reservation.status
FROM time_slot_closures closure
JOIN time_slot_capacities time_slot ON time_slot.id = closure.time_slot_id
JOIN reservations reservation
  ON reservation.lesson_date = time_slot.lesson_date
 AND reservation.start_time = time_slot.start_time
WHERE closure.status = 'IN_PROGRESS'
  AND reservation.status IN ('PENDING_ADMIN_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED');
