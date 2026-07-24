package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.application.ReservationCapacityService;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReservationExactStartConstraintTranslationIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 5);
	private static final LocalTime START_TIME = LocalTime.of(10, 0);
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 8, 4, 10, 0);

	@Autowired
	ReservationCapacityService reservationCapacityService;

	@Autowired
	TimeSlotCapacityRepository timeSlotRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	void V15_exact_start_UNIQUE_위반은_통합_overlap_예외로_변환하고_rollback한다() {
		final Long memberId = insertMember();
		final TimeSlotCapacity timeSlot = createTimeSlot();
		insertExistingReservation(memberId);

		assertThatThrownBy(() -> transactionTemplate().executeWithoutResult(status ->
			reservationCapacityService.createSinglePaymentReservation(
				timeSlot,
				memberId,
				RidingClass.FIRST_RIDE,
				REQUESTED_AT.plusHours(2),
				REQUESTED_AT)))
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.RESERVATION_OVERLAPPING_ACTIVE_RESERVATION.code()));
		assertThat(reservationCount(memberId)).isOne();
	}

	private Long insertMember() {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES ('exact-start-translation-member', '제약 변환 회원', '010-0000-0000')
			""");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private TimeSlotCapacity createTimeSlot() {
		final Map<RidingClass, Integer> capacities = new EnumMap<>(RidingClass.class);
		for (RidingClass ridingClass : RidingClass.values()) {
			capacities.put(ridingClass, 8);
		}
		final Map<String, Integer> classCapacities = new java.util.HashMap<>();
		capacities.forEach((ridingClass, capacity) ->
			classCapacities.put(ridingClass.name(), capacity));
		return timeSlotRepository.saveAndFlush(TimeSlotCapacity.create(
			LESSON_DATE,
			START_TIME,
			8,
			4,
			classCapacities));
	}

	private void insertExistingReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at
			) VALUES (
				?, 'FIRST_RIDE', ?, ?, '10:45:00', 'pending_payment',
				'single_payment', '2026-08-04 12:00:00', '2026-08-04 10:00:00'
			)
			""", memberId, LESSON_DATE, START_TIME);
	}

	private int reservationCount(Long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservations WHERE member_id = ?",
			Integer.class,
			memberId);
	}

	private TransactionTemplate transactionTemplate() {
		return new TransactionTemplate(transactionManager);
	}
}
