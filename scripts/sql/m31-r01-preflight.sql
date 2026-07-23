WITH interval_candidates AS (
	SELECT
		'time_slot_capacities' AS table_name,
		id AS record_id,
		lesson_date,
		start_time
	FROM time_slot_capacities
	UNION ALL
	SELECT
		'reservations' AS table_name,
		id AS record_id,
		lesson_date,
		start_time
	FROM reservations
),
invalid_intervals AS (
	SELECT
		CASE
			WHEN start_time IS NULL THEN 'NULL_START_TIME'
			WHEN TIME_TO_SEC(start_time) < 0 OR TIME_TO_SEC(start_time) >= 86400
				THEN 'INVALID_START_TIME'
			WHEN TIME_TO_SEC(start_time) + 2700 >= 86400
				THEN 'LESSON_INTERVAL_REACHES_OR_CROSSES_MIDNIGHT'
			WHEN SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700) IS NULL
				THEN 'LESSON_END_TIME_BACKFILL_IMPOSSIBLE'
		END AS issue_type,
		table_name,
		record_id,
		lesson_date,
		start_time,
		SEC_TO_TIME(TIME_TO_SEC(start_time) + 2700) AS calculated_end_time,
		CAST(NULL AS CHAR(255)) AS conflicting_reservation_ids
	FROM interval_candidates
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
	SELECT
		'OVERLAPPING_ACTIVE_RESERVATIONS' AS issue_type,
		'reservations' AS table_name,
		first_reservation.id AS record_id,
		first_reservation.lesson_date,
		first_reservation.start_time,
		first_reservation.end_time AS calculated_end_time,
		CONCAT(first_reservation.id, ',', second_reservation.id)
			AS conflicting_reservation_ids
	FROM active_reservations first_reservation
	JOIN active_reservations second_reservation
		ON first_reservation.member_id = second_reservation.member_id
		AND first_reservation.lesson_date = second_reservation.lesson_date
		AND first_reservation.id < second_reservation.id
		AND first_reservation.start_time < second_reservation.end_time
		AND second_reservation.start_time < first_reservation.end_time
),
issues AS (
	SELECT * FROM invalid_intervals
	UNION ALL
	SELECT * FROM overlapping_active_reservations
)
SELECT
	issue_type,
	table_name,
	record_id,
	lesson_date,
	start_time,
	calculated_end_time,
	conflicting_reservation_ids,
	COUNT(*) OVER (PARTITION BY issue_type) AS issue_type_count,
	COUNT(*) OVER () AS total_issue_count
FROM issues
ORDER BY issue_type, table_name, record_id;
