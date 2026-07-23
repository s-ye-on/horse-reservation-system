CREATE TABLE reservation_member_day_guards (
    member_id BIGINT NOT NULL,
    lesson_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_reservation_member_day_guards PRIMARY KEY (member_id, lesson_date),
    CONSTRAINT fk_reservation_member_day_guards_member
        FOREIGN KEY (member_id) REFERENCES members (id)
);
