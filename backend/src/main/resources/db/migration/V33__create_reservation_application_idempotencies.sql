CREATE TABLE reservation_application_idempotencies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auth_subject VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    operation VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    http_status INT UNSIGNED NULL,
    response_body LONGTEXT NULL,
    reservation_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_reservation_application_idempotencies PRIMARY KEY (id),
    CONSTRAINT fk_reservation_application_idempotencies_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT uk_reservation_application_idempotency_scope
        UNIQUE (auth_subject, operation, idempotency_key),
    CONSTRAINT chk_reservation_application_idempotency_subject
        CHECK (CHAR_LENGTH(TRIM(auth_subject)) BETWEEN 1 AND 191),
    CONSTRAINT chk_reservation_application_idempotency_operation
        CHECK (CHAR_LENGTH(TRIM(operation)) BETWEEN 1 AND 64),
    CONSTRAINT chk_reservation_application_idempotency_key
        CHECK (CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 1 AND 255),
    CONSTRAINT chk_reservation_application_idempotency_fingerprint
        CHECK (CHAR_LENGTH(request_fingerprint) = 64),
    CONSTRAINT chk_reservation_application_idempotency_status
        CHECK (status IN ('processing', 'completed')),
    CONSTRAINT chk_reservation_application_idempotency_result CHECK (
        (
            status = 'processing'
            AND http_status IS NULL
            AND response_body IS NULL
            AND reservation_id IS NULL
            AND completed_at IS NULL
        )
        OR
        (
            status = 'completed'
            AND http_status BETWEEN 100 AND 599
            AND response_body IS NOT NULL
            AND CHAR_LENGTH(response_body) > 0
            AND reservation_id IS NOT NULL
            AND completed_at IS NOT NULL
        )
    ),
    INDEX idx_reservation_application_idempotencies_created (created_at, id)
);
