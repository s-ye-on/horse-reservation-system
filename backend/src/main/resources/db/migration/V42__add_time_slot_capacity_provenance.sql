ALTER TABLE time_slot_capacities
    ADD COLUMN capacity_overridden BOOLEAN NOT NULL DEFAULT TRUE AFTER class_capacity_json;

UPDATE time_slot_capacities
SET capacity_overridden = FALSE
WHERE source = 'TEMPLATE';

ALTER TABLE time_slot_capacities
    ADD CONSTRAINT chk_time_slot_capacities_manual_capacity_overridden CHECK (
        source <> 'MANUAL' OR capacity_overridden = TRUE
    );
