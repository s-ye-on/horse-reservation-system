CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auth_subject VARCHAR(191) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    general_ride_count INT UNSIGNED NOT NULL DEFAULT 0,
    dressage_ride_count INT UNSIGNED NOT NULL DEFAULT 0,
    jumping_ride_count INT UNSIGNED NOT NULL DEFAULT 0,
    dressage_approved BOOLEAN NOT NULL DEFAULT FALSE,
    jumping_approved BOOLEAN NOT NULL DEFAULT FALSE,
    large_arena_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_members PRIMARY KEY (id),
    CONSTRAINT uk_members_auth_subject UNIQUE (auth_subject)
);
