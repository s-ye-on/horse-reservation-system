ALTER TABLE members
    ADD COLUMN progression_management_started_at DATETIME(6) NULL AFTER jumping_approved,
    ADD COLUMN progression_baseline_class VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER progression_management_started_at,
    ADD COLUMN progression_baseline_threshold INT UNSIGNED NULL
        AFTER progression_baseline_class,
    ADD COLUMN progression_baseline_actual_ride_count INT UNSIGNED NULL
        AFTER progression_baseline_threshold,
    ADD COLUMN special_approval_progression_credit INT UNSIGNED NOT NULL DEFAULT 0
        AFTER progression_baseline_actual_ride_count,
    ADD COLUMN promotion_hold_class VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER special_approval_progression_credit;

CREATE TABLE member_class_progression_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    action VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_state JSON NULL,
    to_state JSON NOT NULL,
    actor_auth_subject VARCHAR(191) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_member_class_progression_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_member_class_progression_audit_logs_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_member_class_progression_audit_action CHECK (
        action IN (
            'PROGRESSION_INITIALIZED',
            'BASELINE_SET',
            'BASELINE_CHANGED',
            'BASELINE_REMOVED',
            'SPECIAL_APPROVAL_CHANGED',
            'SPECIAL_APPROVAL_CREDIT_CORRECTED',
            'PROMOTION_HOLD_SET',
            'PROMOTION_HOLD_CHANGED',
            'PROMOTION_HOLD_REMOVED'
        )
    ),
    CONSTRAINT chk_member_class_progression_audit_required CHECK (
        CHAR_LENGTH(TRIM(actor_auth_subject)) BETWEEN 1 AND 191
        AND CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500
    ),
    CONSTRAINT chk_member_class_progression_audit_shape CHECK (
        (action = 'PROGRESSION_INITIALIZED' AND from_state IS NULL)
        OR (action <> 'PROGRESSION_INITIALIZED' AND from_state IS NOT NULL)
    ),
    INDEX idx_member_class_progression_audit_member_created
        (member_id, created_at, id)
);

SET @m32_06_progression_started_at = CURRENT_TIMESTAMP(6);

UPDATE members
SET progression_management_started_at = @m32_06_progression_started_at,
    special_approval_progression_credit = CASE
        WHEN general_ride_count < 26 THEN 26 - general_ride_count
        ELSE 0
    END
WHERE dressage_approved = TRUE OR jumping_approved = TRUE;

INSERT INTO member_class_progression_audit_logs (
    member_id,
    action,
    from_state,
    to_state,
    actor_auth_subject,
    reason
)
SELECT
    id,
    'PROGRESSION_INITIALIZED',
    NULL,
    JSON_OBJECT(
        'actualCompletedRideCount', general_ride_count,
        'progressionValue', general_ride_count + special_approval_progression_credit,
        'progressionClass', CASE
            WHEN general_ride_count + special_approval_progression_credit >= 100 THEN 'CANTER'
            WHEN general_ride_count + special_approval_progression_credit >= 70 THEN 'CANTER_BEGINNER'
            WHEN general_ride_count + special_approval_progression_credit >= 26 THEN 'LARGE_ARENA_TROT'
            WHEN general_ride_count + special_approval_progression_credit >= 21 THEN 'LARGE_ARENA_BEGINNER'
            WHEN general_ride_count + special_approval_progression_credit >= 6 THEN 'ROUND_TROT'
            WHEN general_ride_count + special_approval_progression_credit >= 1 THEN 'ROUND_BEGINNER'
            ELSE 'FIRST_RIDE'
        END,
        'effectiveClass', CASE
            WHEN general_ride_count + special_approval_progression_credit >= 100 THEN 'CANTER'
            WHEN general_ride_count + special_approval_progression_credit >= 70 THEN 'CANTER_BEGINNER'
            WHEN general_ride_count + special_approval_progression_credit >= 26 THEN 'LARGE_ARENA_TROT'
            WHEN general_ride_count + special_approval_progression_credit >= 21 THEN 'LARGE_ARENA_BEGINNER'
            WHEN general_ride_count + special_approval_progression_credit >= 6 THEN 'ROUND_TROT'
            WHEN general_ride_count + special_approval_progression_credit >= 1 THEN 'ROUND_BEGINNER'
            ELSE 'FIRST_RIDE'
        END,
        'specialApprovalProgressionCredit', special_approval_progression_credit,
        'dressageApproved', dressage_approved,
        'jumpingApproved', jumping_approved,
        'managementStartedAt', progression_management_started_at
    ),
    'MIGRATION_V38',
    '기존 특수 승인 progression 상태 전환'
FROM members
WHERE progression_management_started_at IS NOT NULL;

ALTER TABLE members
    ADD CONSTRAINT chk_members_progression_baseline_shape CHECK (
        (
            progression_baseline_class IS NULL
            AND progression_baseline_threshold IS NULL
            AND progression_baseline_actual_ride_count IS NULL
        )
        OR (
            progression_baseline_class IS NOT NULL
            AND progression_baseline_threshold IS NOT NULL
            AND progression_baseline_actual_ride_count IS NOT NULL
        )
    ),
    ADD CONSTRAINT chk_members_progression_baseline_class CHECK (
        progression_baseline_class IS NULL
        OR progression_baseline_class IN (
            'FIRST_RIDE',
            'ROUND_BEGINNER',
            'ROUND_TROT',
            'LARGE_ARENA_BEGINNER',
            'LARGE_ARENA_TROT',
            'CANTER_BEGINNER',
            'CANTER'
        )
    ),
    ADD CONSTRAINT chk_members_progression_baseline_threshold CHECK (
        progression_baseline_class IS NULL
        OR (
            (progression_baseline_class = 'FIRST_RIDE' AND progression_baseline_threshold = 0)
            OR (progression_baseline_class = 'ROUND_BEGINNER' AND progression_baseline_threshold = 1)
            OR (progression_baseline_class = 'ROUND_TROT' AND progression_baseline_threshold = 6)
            OR (progression_baseline_class = 'LARGE_ARENA_BEGINNER' AND progression_baseline_threshold = 21)
            OR (progression_baseline_class = 'LARGE_ARENA_TROT' AND progression_baseline_threshold = 26)
            OR (progression_baseline_class = 'CANTER_BEGINNER' AND progression_baseline_threshold = 70)
            OR (progression_baseline_class = 'CANTER' AND progression_baseline_threshold = 100)
        )
    ),
    ADD CONSTRAINT chk_members_promotion_hold_class CHECK (
        promotion_hold_class IS NULL
        OR promotion_hold_class IN (
            'FIRST_RIDE',
            'ROUND_BEGINNER',
            'ROUND_TROT',
            'LARGE_ARENA_BEGINNER',
            'LARGE_ARENA_TROT',
            'CANTER_BEGINNER',
            'CANTER'
        )
    ),
    ADD CONSTRAINT chk_members_progression_initialized_shape CHECK (
        progression_management_started_at IS NOT NULL
        OR (
            progression_baseline_class IS NULL
            AND special_approval_progression_credit = 0
            AND promotion_hold_class IS NULL
            AND dressage_approved = FALSE
            AND jumping_approved = FALSE
        )
    ),
    ADD CONSTRAINT chk_members_special_approval_hold_exclusive CHECK (
        promotion_hold_class IS NULL
        OR (dressage_approved = FALSE AND jumping_approved = FALSE)
    );

ALTER TABLE reservations
	DROP CHECK chk_reservations_class_type,
    ADD CONSTRAINT chk_reservations_class_type CHECK (
        class_type IN (
            'FIRST_RIDE',
            'ROUND_BEGINNER',
            'ROUND_TROT',
            'LARGE_ARENA_BEGINNER',
            'LARGE_ARENA_TROT',
            'CANTER_BEGINNER',
            'CANTER',
            'DRESSAGE',
            'JUMPING'
        )
    );

ALTER TABLE time_slot_capacities
	DROP CHECK chk_time_slot_capacities_class_capacity_json;

UPDATE time_slot_capacities
SET class_capacity_json = JSON_SET(
    class_capacity_json,
    '$.CANTER_BEGINNER', 0,
    '$.CANTER', 0
);

ALTER TABLE time_slot_capacities
    ADD CONSTRAINT chk_time_slot_capacities_class_capacity_json CHECK (
        JSON_SCHEMA_VALID(
            '{
                "type": "object",
                "required": [
                    "FIRST_RIDE",
                    "ROUND_BEGINNER",
                    "ROUND_TROT",
                    "LARGE_ARENA_BEGINNER",
                    "LARGE_ARENA_TROT",
                    "CANTER_BEGINNER",
                    "CANTER",
                    "DRESSAGE",
                    "JUMPING"
                ],
                "properties": {
                    "FIRST_RIDE": {"type": "integer", "minimum": 0, "maximum": 8},
                    "ROUND_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "ROUND_TROT": {"type": "integer", "minimum": 0, "maximum": 8},
                    "LARGE_ARENA_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "LARGE_ARENA_TROT": {"type": "integer", "minimum": 0, "maximum": 8},
                    "CANTER_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "CANTER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "DRESSAGE": {"type": "integer", "minimum": 0, "maximum": 8},
                    "JUMPING": {"type": "integer", "minimum": 0, "maximum": 8}
                },
                "additionalProperties": false
            }',
            class_capacity_json
        )
    );

ALTER TABLE regular_schedule_templates
	DROP CHECK chk_regular_schedule_templates_class_capacity;

UPDATE regular_schedule_templates
SET class_capacity_json = JSON_SET(
    class_capacity_json,
    '$.CANTER_BEGINNER', 0,
    '$.CANTER', 0
);

ALTER TABLE regular_schedule_templates
    ADD CONSTRAINT chk_regular_schedule_templates_class_capacity CHECK (
        JSON_SCHEMA_VALID(
            '{
                "type": "object",
                "required": [
                    "FIRST_RIDE",
                    "ROUND_BEGINNER",
                    "ROUND_TROT",
                    "LARGE_ARENA_BEGINNER",
                    "LARGE_ARENA_TROT",
                    "CANTER_BEGINNER",
                    "CANTER",
                    "DRESSAGE",
                    "JUMPING"
                ],
                "properties": {
                    "FIRST_RIDE": {"type": "integer", "minimum": 0, "maximum": 8},
                    "ROUND_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "ROUND_TROT": {"type": "integer", "minimum": 0, "maximum": 8},
                    "LARGE_ARENA_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "LARGE_ARENA_TROT": {"type": "integer", "minimum": 0, "maximum": 8},
                    "CANTER_BEGINNER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "CANTER": {"type": "integer", "minimum": 0, "maximum": 8},
                    "DRESSAGE": {"type": "integer", "minimum": 0, "maximum": 8},
                    "JUMPING": {"type": "integer", "minimum": 0, "maximum": 8}
                },
                "additionalProperties": false
            }',
            class_capacity_json
        )
    );
