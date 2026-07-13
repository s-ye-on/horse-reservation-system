CREATE TABLE coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    coupon_type VARCHAR(20) NOT NULL,
    total_count TINYINT UNSIGNED NOT NULL,
    remaining_count TINYINT UNSIGNED NOT NULL,
    held_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    first_used_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    free_change_used BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_coupons PRIMARY KEY (id),
    CONSTRAINT uk_coupons_id_member UNIQUE (id, member_id),
    CONSTRAINT fk_coupons_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT chk_coupons_type CHECK (coupon_type IN ('general', 'dressage', 'jumping')),
    CONSTRAINT chk_coupons_counts CHECK (
        total_count > 0
        AND remaining_count <= total_count
        AND held_count <= remaining_count
    ),
    CONSTRAINT chk_coupons_usage_dates CHECK (
        (first_used_at IS NULL AND expires_at IS NULL)
        OR (first_used_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at >= first_used_at)
    ),
    CONSTRAINT chk_coupons_status CHECK (status IN ('active', 'expired', 'depleted')),
    INDEX idx_coupons_member_status_expiry (member_id, status, expires_at)
);

CREATE TABLE coupon_usage_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    reservation_id BIGINT NULL,
    member_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    count_delta SMALLINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    memo VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_coupon_usage_logs PRIMARY KEY (id),
    CONSTRAINT fk_coupon_usage_logs_coupon_member
        FOREIGN KEY (coupon_id, member_id) REFERENCES coupons (id, member_id),
    CONSTRAINT chk_coupon_usage_logs_action CHECK (
        action IN ('held', 'confirmed', 'used', 'released', 'deducted', 'expired', 'free_change_used')
    ),
    CONSTRAINT chk_coupon_usage_logs_actor_type CHECK (actor_type IN ('system', 'member', 'admin')),
    INDEX idx_coupon_usage_logs_coupon_occurred (coupon_id, occurred_at),
    INDEX idx_coupon_usage_logs_member_occurred (member_id, occurred_at),
    INDEX idx_coupon_usage_logs_reservation (reservation_id)
);
