DROP PROCEDURE IF EXISTS assert_m31_r08_active_overlap_preflight;

DELIMITER $$

CREATE PROCEDURE assert_m31_r08_active_overlap_preflight()
BEGIN
    DECLARE overlap_count BIGINT DEFAULT 0;
    DECLARE failure_message VARCHAR(255);

    SELECT COUNT(*)
    INTO overlap_count
    FROM reservations first_reservation
    JOIN reservations second_reservation
        ON first_reservation.member_id = second_reservation.member_id
        AND first_reservation.lesson_date = second_reservation.lesson_date
        AND first_reservation.id < second_reservation.id
        AND first_reservation.active_slot_guard = 1
        AND second_reservation.active_slot_guard = 1
        AND first_reservation.start_time < second_reservation.end_time
        AND second_reservation.start_time < first_reservation.end_time;

    IF overlap_count > 0 THEN
        SET failure_message = CONCAT(
            'M31-R08 preflight found ',
            overlap_count,
            ' active reservation overlap(s)'
        );
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = failure_message;
    END IF;
END$$

DELIMITER ;

CALL assert_m31_r08_active_overlap_preflight();
DROP PROCEDURE assert_m31_r08_active_overlap_preflight;
