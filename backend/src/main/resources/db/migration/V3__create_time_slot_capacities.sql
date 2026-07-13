CREATE TABLE time_slot_capacities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_date DATE NOT NULL,
    start_time TIME NOT NULL,
    total_capacity TINYINT UNSIGNED NOT NULL,
    round_arena_capacity TINYINT UNSIGNED NOT NULL,
    class_capacity_json JSON NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_time_slot_capacities PRIMARY KEY (id),
    CONSTRAINT uk_time_slot_capacities_lesson_date_start_time UNIQUE (lesson_date, start_time),
    CONSTRAINT chk_time_slot_capacities_total_capacity CHECK (total_capacity <= 8),
    CONSTRAINT chk_time_slot_capacities_round_arena_capacity CHECK (
        round_arena_capacity <= 4 AND round_arena_capacity <= total_capacity
    ),
    CONSTRAINT chk_time_slot_capacities_class_capacity_json CHECK (
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
                    "LARGE_ARENA_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "LARGE_ARENA_TROT": {"type": "integer", "minimum": 0, "maximum": 8},
                    "DRESSAGE": {"type": "integer", "minimum": 0, "maximum": 8},
                    "JUMPING": {"type": "integer", "minimum": 0, "maximum": 8}
                },
                "additionalProperties": false
            }',
            class_capacity_json
        )
    )
);
