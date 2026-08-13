ALTER TABLE reservations
    DROP FOREIGN KEY fk_reservations_coupon_member,
    ADD CONSTRAINT fk_reservations_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupons (id);

ALTER TABLE coupons
    ADD COLUMN expiry_null_rank TINYINT
        GENERATED ALWAYS AS (CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END) STORED,
    ADD INDEX idx_coupons_family_selection (
        coupon_type,
        status,
        expiry_null_rank,
        expires_at,
        created_at,
        id
    );

ALTER TABLE coupon_usage_logs
    DROP FOREIGN KEY fk_coupon_usage_logs_coupon_member,
    ADD COLUMN coupon_owner_member_id BIGINT NULL AFTER member_id,
    ADD COLUMN family_group_id BIGINT NULL AFTER coupon_owner_member_id;

UPDATE coupon_usage_logs usage_log
JOIN coupons coupon ON coupon.id = usage_log.coupon_id
SET usage_log.coupon_owner_member_id = coupon.member_id;

ALTER TABLE coupon_usage_logs
    MODIFY coupon_owner_member_id BIGINT NOT NULL,
	ADD CONSTRAINT fk_coupon_usage_logs_coupon_owner_pair
		FOREIGN KEY (coupon_id, coupon_owner_member_id) REFERENCES coupons (id, member_id),
    ADD CONSTRAINT fk_coupon_usage_logs_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    ADD CONSTRAINT fk_coupon_usage_logs_coupon_owner
        FOREIGN KEY (coupon_owner_member_id) REFERENCES members (id),
    ADD CONSTRAINT fk_coupon_usage_logs_family_group
        FOREIGN KEY (family_group_id) REFERENCES family_groups (id),
    ADD CONSTRAINT chk_coupon_usage_logs_family_snapshot CHECK (
        (member_id = coupon_owner_member_id AND family_group_id IS NULL)
        OR (member_id <> coupon_owner_member_id AND family_group_id IS NOT NULL)
    ),
    ADD INDEX idx_coupon_usage_logs_owner_occurred
        (coupon_owner_member_id, occurred_at, id),
    ADD INDEX idx_coupon_usage_logs_family_occurred
        (family_group_id, occurred_at, id);
