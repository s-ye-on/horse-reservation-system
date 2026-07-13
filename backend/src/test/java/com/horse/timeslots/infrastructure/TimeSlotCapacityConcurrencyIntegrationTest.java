package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.application.ReservationCapacityService;
import com.horse.reservations.domain.Reservation;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.domain.exception.TimeSlotException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TimeSlotCapacityConcurrencyIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 20);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 7, 14, 10, 0);

	@Autowired
	ReservationCapacityService reservationCapacityService;

	@Autowired
	TimeSlotCapacityRepository timeSlotRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM members");
	}

	@Test
	void 마감된_시간대에는_예약을_생성하지_않는다() {
		final TestMember member = createMemberWithCoupon("closed-timeslot-member");
		final TimeSlotCapacity timeSlot = createTimeSlot(
			LESSON_DATE,
			START_TIME,
			2,
			2,
			RidingClass.ROUND_BEGINNER,
			2);
		timeSlot.changeClosedStatus(true);
		timeSlotRepository.saveAndFlush(timeSlot);

		assertTimeSlotException(
			() -> reserveWithCoupon(timeSlot, member, RidingClass.ROUND_BEGINNER),
			ExceptionCode.TIMESLOT_CLOSED);
		assertThat(reservationCount()).isZero();
	}

	@Test
	void 전체_원형_클래스_정원_경계를_각각_검증한다() {
		final TestMember member = createMemberWithCoupon("capacity-boundary-member");
		final TimeSlotCapacity totalLimited = createTimeSlot(
			LESSON_DATE,
			START_TIME,
			1,
			1,
			RidingClass.LARGE_ARENA_BEGINNER,
			2);
		reserveWithCoupon(totalLimited, member, RidingClass.LARGE_ARENA_BEGINNER);

		assertCapacityExceeded(
			() -> reserveWithCoupon(totalLimited, member, RidingClass.LARGE_ARENA_BEGINNER));

		final TimeSlotCapacity roundLimited = createTimeSlot(
			LESSON_DATE,
			LocalTime.of(10, 0),
			2,
			1,
			RidingClass.ROUND_BEGINNER,
			2);
		reserveWithCoupon(roundLimited, member, RidingClass.ROUND_BEGINNER);

		assertCapacityExceeded(() -> reserveWithCoupon(roundLimited, member, RidingClass.ROUND_BEGINNER));

		final TimeSlotCapacity classLimited = createTimeSlot(
			LESSON_DATE,
			LocalTime.of(11, 0),
			2,
			2,
			RidingClass.ROUND_BEGINNER,
			1);
		reserveWithCoupon(classLimited, member, RidingClass.ROUND_BEGINNER);

		assertCapacityExceeded(() -> reserveWithCoupon(classLimited, member, RidingClass.ROUND_BEGINNER));
	}

	@Test
	void 세_활성_상태만_점유하고_비점유_상태가_되면_다시_예약한다() {
		final TestMember member = createMemberWithCoupon("occupying-status-member");
		final TimeSlotCapacity timeSlot = createTimeSlot(
			LESSON_DATE,
			START_TIME,
			3,
			3,
			RidingClass.ROUND_BEGINNER,
			3);
		final Reservation confirmed = reserveWithCoupon(timeSlot, member, RidingClass.ROUND_BEGINNER);
		jdbcTemplate.update("""
			UPDATE reservations
			SET status = 'confirmed', admin_confirmed_at = '2026-07-14 10:10:00'
			WHERE id = ?
			""", confirmed.getId());
		final Reservation pendingPayment = reservationCapacityService.reserveWithSinglePayment(
			timeSlot.getId(),
			member.memberId(),
			RidingClass.ROUND_BEGINNER,
			REQUESTED_AT.plusHours(2),
			REQUESTED_AT);
		reserveWithCoupon(timeSlot, member, RidingClass.ROUND_BEGINNER);

		assertCapacityExceeded(() -> reserveWithCoupon(timeSlot, member, RidingClass.ROUND_BEGINNER));

		jdbcTemplate.update(
			"UPDATE reservations SET status = 'payment_expired' WHERE id = ?",
			pendingPayment.getId());

		final Reservation replacement = reserveWithCoupon(
			timeSlot,
			member,
			RidingClass.ROUND_BEGINNER);

		assertThat(replacement.getId()).isNotNull();
		assertThat(reservationCount()).isEqualTo(4);
	}

	@Test
	void 동일_시간대_병렬_예약은_허용된_수만_성공한다() throws Exception {
		final TestMember member = createMemberWithCoupon("concurrency-member");
		final TimeSlotCapacity timeSlot = createTimeSlot(
			LESSON_DATE,
			START_TIME,
			2,
			2,
			RidingClass.ROUND_BEGINNER,
			2);
		final int requestCount = 4;
		final CountDownLatch startSignal = new CountDownLatch(1);
		final ExecutorService executor = Executors.newFixedThreadPool(requestCount);

		try {
			final List<Future<Boolean>> results = IntStream.range(0, requestCount)
				.mapToObj(index -> executor.submit(() ->
					attemptReservation(startSignal, timeSlot, member)))
				.toList();
			startSignal.countDown();

			final long successCount = results.stream()
				.map(this::getResult)
				.filter(Boolean::booleanValue)
				.count();

			assertThat(successCount).isEqualTo(2);
			assertThat(reservationCount()).isEqualTo(2);
		}
		finally {
			executor.shutdown();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private boolean attemptReservation(
		CountDownLatch startSignal,
		TimeSlotCapacity timeSlot,
		TestMember member
	) throws InterruptedException {
		startSignal.await();
		try {
			reserveWithCoupon(timeSlot, member, RidingClass.ROUND_BEGINNER);
			return true;
		}
		catch (TimeSlotException exception) {
			assertThat(exception.code()).isEqualTo(ExceptionCode.TIMESLOT_CAPACITY_EXCEEDED.code());
			return false;
		}
	}

	private boolean getResult(Future<Boolean> result) {
		try {
			return result.get(10, TimeUnit.SECONDS);
		}
		catch (Exception exception) {
			throw new AssertionError("병렬 예약 결과를 확인할 수 없습니다.", exception);
		}
	}

	private Reservation reserveWithCoupon(
		TimeSlotCapacity timeSlot,
		TestMember member,
		RidingClass ridingClass
	) {
		return reservationCapacityService.reserveWithCoupon(
			timeSlot.getId(),
			member.memberId(),
			ridingClass,
			member.couponId(),
			REQUESTED_AT);
	}

	private TimeSlotCapacity createTimeSlot(
		LocalDate lessonDate,
		LocalTime startTime,
		int totalCapacity,
		int roundArenaCapacity,
		RidingClass selectedClass,
		int selectedClassCapacity
	) {
		final Map<String, Integer> classCapacities = new HashMap<>();
		for (RidingClass ridingClass : RidingClass.values()) {
			classCapacities.put(ridingClass.name(), totalCapacity);
		}
		classCapacities.put(selectedClass.name(), selectedClassCapacity);
		return timeSlotRepository.saveAndFlush(TimeSlotCapacity.create(
			lessonDate,
			startTime,
			totalCapacity,
			roundArenaCapacity,
			classCapacities));
	}

	private TestMember createMemberWithCoupon(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '정원 회원', '010-0000-0000')
			""", authSubject);
		final Long memberId = jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
		jdbcTemplate.update("""
			INSERT INTO coupons (member_id, coupon_type, total_count, remaining_count)
			VALUES (?, 'general', 10, 10)
			""", memberId);
		final Long couponId = jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM coupons WHERE member_id = ?", Long.class, memberId);
		return new TestMember(memberId, couponId);
	}

	private int reservationCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
	}

	private void assertCapacityExceeded(Runnable action) {
		assertTimeSlotException(action, ExceptionCode.TIMESLOT_CAPACITY_EXCEEDED);
	}

	private void assertTimeSlotException(Runnable action, ExceptionCode expectedCode) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				TimeSlotException.class,
				exception -> assertThat(exception.code()).isEqualTo(expectedCode.code()));
	}

	private record TestMember(Long memberId, Long couponId) {
	}
}
