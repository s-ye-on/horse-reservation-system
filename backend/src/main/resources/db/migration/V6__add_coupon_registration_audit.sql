ALTER TABLE coupons
    ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'system' AFTER status;
