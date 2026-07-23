DROP PROCEDURE IF EXISTS assert_m31_r01_lesson_interval_preflight;

DELIMITER $$

CREATE PROCEDURE assert_m31_r01_lesson_interval_preflight()
BEGIN
	DECLARE issue_count BIGINT DEFAULT 0;
	DECLARE failure_message VARCHAR(255);

	WITH invalid_intervals AS (
		SELECT id
		FROM time_slot_capacities
		WHERE start_time IS NULL
			OR TIME_TO_SEC(start_time) < 0
			OR TIME_TO_SEC(start_time) >= 86400
			OR TIME_TO_SEC(start_time) + 2700 >= 86400
			OR SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700) IS NULL
		UNION ALL
		SELECT id
		FROM reservations
		WHERE start_time IS NULL
			OR TIME_TO_SEC(start_time) < 0
			OR TIME_TO_SEC(start_time) >= 86400
			OR TIME_TO_SEC(start_time) + 2700 >= 86400
			OR SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700) IS NULL
	),
	active_reservations AS (
		SELECT
			id,
			member_id,
			lesson_date,
			start_time,
			SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700) AS end_time
		FROM reservations
		WHERE status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
			AND start_time IS NOT NULL
			AND TIME_TO_SEC(start_time) >= 0
			AND TIME_TO_SEC(start_time) + 2700 < 86400
			AND SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700) IS NOT NULL
	),
	overlapping_active_reservations AS (
		SELECT first_reservation.id
		FROM active_reservations first_reservation
		JOIN active_reservations second_reservation
			ON first_reservation.member_id = second_reservation.member_id
			AND first_reservation.lesson_date = second_reservation.lesson_date
			AND first_reservation.id < second_reservation.id
			AND first_reservation.start_time < second_reservation.end_time
			AND second_reservation.start_time < first_reservation.end_time
	),
	issues AS (
		SELECT id FROM invalid_intervals
		UNION ALL
		SELECT id FROM overlapping_active_reservations
	)
	SELECT COUNT(*) INTO issue_count FROM issues;

	IF issue_count > 0 THEN
		SET failure_message = CONCAT(
			'M31-R01 preflight found ',
			issue_count,
			' issue(s); run mise run db:preflight-m31-r01'
		);
		SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
	END IF;
END$$

DELIMITER ;

CALL assert_m31_r01_lesson_interval_preflight();
DROP PROCEDURE assert_m31_r01_lesson_interval_preflight;

ALTER TABLE time_slot_capacities
	ADD COLUMN end_time TIME NULL AFTER start_time,
	ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL' AFTER end_time;

UPDATE time_slot_capacities
SET
	end_time = SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700),
	source = 'MANUAL';

ALTER TABLE time_slot_capacities
	ADD CONSTRAINT chk_time_slot_capacities_lesson_interval
		CHECK (
			end_time IS NULL
			OR (
				TIME_TO_SEC(start_time) >= 0
				AND TIME_TO_SEC(end_time) = TIME_TO_SEC(start_time) + 2700
				AND TIME_TO_SEC(end_time) < 86400
			)
		),
	ADD CONSTRAINT chk_time_slot_capacities_source
		CHECK (source IN ('TEMPLATE', 'MANUAL'));

ALTER TABLE reservations
	ADD COLUMN end_time TIME NULL AFTER start_time;

UPDATE reservations
SET end_time = SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700);

ALTER TABLE reservations
	ADD CONSTRAINT chk_reservations_lesson_interval
		CHECK (
			end_time IS NULL
			OR (
				TIME_TO_SEC(start_time) >= 0
				AND TIME_TO_SEC(end_time) = TIME_TO_SEC(start_time) + 2700
				AND TIME_TO_SEC(end_time) < 86400
			)
		);
