UPDATE time_slot_closures migration_closure
JOIN time_slot_capacities time_slot
  ON time_slot.id = migration_closure.time_slot_id
LEFT JOIN time_slot_closures active_closure
  ON active_closure.time_slot_id = migration_closure.time_slot_id
 AND active_closure.status = 'IN_PROGRESS'
 AND active_closure.id <> migration_closure.id
SET migration_closure.status = 'IN_PROGRESS',
    migration_closure.completed_by = NULL,
    migration_closure.completed_at = NULL
WHERE migration_closure.status = 'COMPLETED'
  AND migration_closure.started_by = 'migration:v30'
  AND migration_closure.completed_by = 'migration:v30'
  AND active_closure.id IS NULL
  AND EXISTS (
      SELECT 1
      FROM reservations reservation
      WHERE reservation.lesson_date = time_slot.lesson_date
        AND reservation.start_time = time_slot.start_time
        AND reservation.active_slot_guard = 1
  );

INSERT INTO time_slot_closure_impacts (
    closure_id,
    reservation_id,
    reservation_status_at_start
)
SELECT
    migration_closure.id,
    reservation.id,
    reservation.status
FROM time_slot_closures migration_closure
JOIN time_slot_capacities time_slot
  ON time_slot.id = migration_closure.time_slot_id
JOIN reservations reservation
  ON reservation.lesson_date = time_slot.lesson_date
 AND reservation.start_time = time_slot.start_time
 AND reservation.active_slot_guard = 1
LEFT JOIN time_slot_closure_impacts existing_impact
  ON existing_impact.closure_id = migration_closure.id
 AND existing_impact.reservation_id = reservation.id
WHERE migration_closure.status = 'IN_PROGRESS'
  AND migration_closure.started_by = 'migration:v30'
  AND existing_impact.id IS NULL
ORDER BY migration_closure.id, reservation.id;
