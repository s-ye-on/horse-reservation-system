ALTER TABLE time_slot_closure_impacts
    ADD CONSTRAINT chk_time_slot_closure_impacts_active_status
        CHECK (reservation_status_at_start IN (
            'pending_admin_approval',
            'pending_payment',
            'confirmed'
        ));
