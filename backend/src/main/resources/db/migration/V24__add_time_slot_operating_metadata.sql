ALTER TABLE time_slot_capacities
    ADD COLUMN template_id BIGINT NULL AFTER source,
    ADD COLUMN admin_closed BOOLEAN NOT NULL DEFAULT FALSE AFTER is_closed,
    ADD COLUMN recurring_holiday_closed BOOLEAN NOT NULL DEFAULT FALSE AFTER admin_closed,
    ADD COLUMN template_inactive_closed BOOLEAN NOT NULL DEFAULT FALSE
        AFTER recurring_holiday_closed;

UPDATE time_slot_capacities
SET
    admin_closed = is_closed,
    recurring_holiday_closed = FALSE,
    template_inactive_closed = FALSE;

ALTER TABLE time_slot_capacities
    ADD CONSTRAINT fk_time_slot_capacities_template
        FOREIGN KEY (template_id) REFERENCES regular_schedule_templates (id),
    ADD CONSTRAINT chk_time_slot_capacities_source_template CHECK (
        (source = 'TEMPLATE' AND template_id IS NOT NULL)
        OR (source = 'MANUAL' AND template_id IS NULL)
    );

ALTER TABLE time_slot_capacities
    DROP COLUMN is_closed,
    ADD COLUMN is_closed BOOLEAN
        GENERATED ALWAYS AS (
            admin_closed OR recurring_holiday_closed OR template_inactive_closed
        ) STORED AFTER template_inactive_closed;
