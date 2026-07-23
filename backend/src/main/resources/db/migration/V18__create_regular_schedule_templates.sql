CREATE TABLE regular_schedule_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    day_of_week VARCHAR(9) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    total_capacity TINYINT UNSIGNED NOT NULL,
    round_arena_capacity TINYINT UNSIGNED NOT NULL,
    class_capacity_json JSON NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(191) NOT NULL,
    updated_by VARCHAR(191) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_regular_schedule_templates PRIMARY KEY (id),
    CONSTRAINT uk_regular_schedule_templates_day_start UNIQUE (day_of_week, start_time),
    CONSTRAINT chk_regular_schedule_templates_day CHECK (
        day_of_week IN (
            'MONDAY',
            'TUESDAY',
            'WEDNESDAY',
            'THURSDAY',
            'FRIDAY',
            'SATURDAY',
            'SUNDAY'
        )
    ),
    CONSTRAINT chk_regular_schedule_templates_interval CHECK (
        start_time < end_time
        AND TIME_TO_SEC(end_time) - TIME_TO_SEC(start_time) = 2700
        AND TIME_TO_SEC(start_time) >= 0
        AND TIME_TO_SEC(end_time) < 86400
    ),
    CONSTRAINT chk_regular_schedule_templates_total_capacity CHECK (total_capacity <= 8),
    CONSTRAINT chk_regular_schedule_templates_round_capacity CHECK (
        round_arena_capacity <= 4 AND round_arena_capacity <= total_capacity
    ),
    CONSTRAINT chk_regular_schedule_templates_class_capacity CHECK (
        JSON_SCHEMA_VALID(
            '{
                "type": "object",
                "required": [
                    "FIRST_RIDE",
                    "ROUND_BEGINNER",
                    "ROUND_TROT",
                    "LARGE_ARENA_BEGINNER",
                    "LARGE_ARENA_TROT",
                    "DRESSAGE",
                    "JUMPING"
                ],
                "properties": {
                    "FIRST_RIDE": {"type": "integer", "minimum": 0, "maximum": 8},
                    "ROUND_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "ROUND_TROT": {"type": "integer", "minimum": 0, "maximum": 8},
                    "LARGE_ARENA_BEGINNER": {
                        "type": "integer", "minimum": 0, "maximum": 8
                    },
                    "LARGE_ARENA_TROT": {
                        "type": "integer", "minimum": 0, "maximum": 8
                    },
                    "DRESSAGE": {"type": "integer", "minimum": 0, "maximum": 8},
                    "JUMPING": {"type": "integer", "minimum": 0, "maximum": 8}
                },
                "additionalProperties": false
            }',
            class_capacity_json
        )
    ),
    CONSTRAINT chk_regular_schedule_templates_version CHECK (version >= 0),
    CONSTRAINT chk_regular_schedule_templates_actors CHECK (
        CHAR_LENGTH(TRIM(created_by)) > 0 AND CHAR_LENGTH(TRIM(updated_by)) > 0
    )
);
