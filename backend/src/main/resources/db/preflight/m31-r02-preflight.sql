WITH interval_candidates AS (
	SELECT
		'time_slot_capacities' AS table_name,
		id AS record_id,
		lesson_date,
		start_time,
		end_time
	FROM time_slot_capacities
	UNION ALL
	SELECT
		'reservations' AS table_name,
		id AS record_id,
		lesson_date,
		start_time,
		end_time
	FROM reservations
),
interval_issues AS (
	SELECT
		CASE
			WHEN start_time IS NULL THEN 'NULL_START_TIME'
			WHEN end_time IS NULL THEN 'NULL_END_TIME'
			WHEN TIME_TO_SEC(start_time) < 0 OR TIME_TO_SEC(start_time) >= 86400
				THEN 'INVALID_START_TIME'
			WHEN TIME_TO_SEC(end_time) < 0 OR TIME_TO_SEC(end_time) >= 86400
				THEN 'INVALID_END_TIME'
			WHEN start_time >= end_time THEN 'START_TIME_NOT_BEFORE_END_TIME'
			WHEN TIME_TO_SEC(end_time) - TIME_TO_SEC(start_time) <> 2700
				THEN 'LESSON_INTERVAL_NOT_45_MINUTES'
		END AS issue_type,
		table_name,
		record_id,
		lesson_date,
		start_time,
		end_time,
		CAST(NULL AS CHAR(500)) AS details
	FROM interval_candidates
	WHERE start_time IS NULL
		OR end_time IS NULL
		OR TIME_TO_SEC(start_time) < 0
		OR TIME_TO_SEC(start_time) >= 86400
		OR TIME_TO_SEC(end_time) < 0
		OR TIME_TO_SEC(end_time) >= 86400
		OR start_time >= end_time
		OR TIME_TO_SEC(end_time) - TIME_TO_SEC(start_time) <> 2700
),
time_slot_metadata_issues AS (
	SELECT
		CASE
			WHEN source NOT IN ('MANUAL', 'TEMPLATE') THEN 'INVALID_TIME_SLOT_SOURCE'
			WHEN source = 'TEMPLATE' THEN 'TEMPLATE_SOURCE_WITHOUT_TEMPLATE_METADATA'
			WHEN is_closed NOT IN (0, 1) THEN 'INVALID_TIME_SLOT_CLOSED_VALUE'
		END AS issue_type,
		'time_slot_capacities' AS table_name,
		id AS record_id,
		lesson_date,
		start_time,
		end_time,
		CONCAT('source=', source, ',is_closed=', is_closed) AS details
	FROM time_slot_capacities
	WHERE source NOT IN ('MANUAL', 'TEMPLATE')
		OR source = 'TEMPLATE'
		OR is_closed NOT IN (0, 1)
),
issues AS (
	SELECT * FROM interval_issues
	UNION ALL
	SELECT * FROM time_slot_metadata_issues
)
SELECT
	issue_type,
	table_name,
	record_id,
	lesson_date,
	start_time,
	end_time,
	details,
	COUNT(*) OVER (PARTITION BY issue_type) AS issue_type_count,
	COUNT(*) OVER () AS total_issue_count
FROM issues
ORDER BY issue_type, table_name, record_id;
