ALTER TABLE member_class_progression_audit_logs
    DROP CHECK chk_member_class_progression_audit_action,
    ADD CONSTRAINT chk_member_class_progression_audit_action CHECK (
        action IN (
            'PROGRESSION_INITIALIZED',
            'BASELINE_SET',
            'BASELINE_CHANGED',
            'BASELINE_REMOVED',
            'SPECIAL_APPROVAL_CHANGED',
            'SPECIAL_APPROVAL_CREDIT_CORRECTED',
            'RIDE_COUNT_ADJUSTED',
            'PROMOTION_HOLD_SET',
            'PROMOTION_HOLD_CHANGED',
            'PROMOTION_HOLD_REMOVED'
        )
    );
