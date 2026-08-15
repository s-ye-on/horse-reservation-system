package com.horse.timeslots.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.horse.TestcontainersConfiguration;
import com.horse.global.exception.BusinessException;
import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.application.AdminReservationCancellationService;
import com.horse.reservations.application.MemberReservationCancellationService;
import com.horse.reservations.application.ReservationCompletionService;
import com.horse.reservations.application.ReservationConfirmService;
import com.horse.reservations.application.ReservationNoShowService;
import com.horse.reservations.application.ReservationPaymentRestoreService;
import com.horse.reservations.application.TimeSlotClosureCancellationService;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.timeslots.infrastructure.TimeSlotClosureImpactRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TimeSlotClosureServiceIntegrationTest {

	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 2);
	private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

	@Autowired
	TimeSlotClosureService closureService;

	@Autowired
	TimeSlotClosureCancellationService cancellationService;

	@Autowired
	AdminReservationCancellationService generalCancellationService;

	@Autowired
	MemberReservationCancellationService memberCancellationService;

	@Autowired
	ReservationConfirmService confirmService;

	@Autowired
	ReservationCompletionService completionService;

	@Autowired
	ReservationNoShowService noShowService;

	@Autowired
	ReservationPaymentRestoreService paymentRestoreService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TimeSlotClosureImpactRepository impactRepository;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 휴강_테스트_데이터를_초기화한다() {
		given(clock.instant()).willReturn(NOW);
		given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
		clearDatabase();
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = 1,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""");
		insertScheduleDate();
	}

	@AfterEach
	void 휴강_테스트_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 휴강_시작은_마감과_영향_예약을_원자적으로_저장한다() {
		final Long timeSlotId = insertTimeSlot();
		final Long reservationId = insertReservation(insertMember("closure-impact"));

		closureService.start(timeSlotId, "admin", "우천");

		assertThat(adminClosed(timeSlotId)).isTrue();
		assertThat(closureStatus(timeSlotId)).isEqualTo("IN_PROGRESS");
		assertThat(impactReservationIds()).containsExactly(reservationId);
		assertThat(impactRepository.findAllByClosureIdOrderByReservationId(closureId(timeSlotId)))
			.singleElement()
			.extracting(impact -> impact.getReservationStatusAtStart())
			.isEqualTo(ReservationStatus.PENDING_PAYMENT);
	}

	@Test
	void 휴강_전용_취소는_1회_결제에_stable_NONE을_적용하고_완료한다() {
		final Long timeSlotId = insertTimeSlot();
		final Long reservationId = insertReservation(insertMember("closure-cancel"));
		closureService.start(timeSlotId, "admin", "우천");

		cancellationService.cancelByAdmin(reservationId, "admin", "고객 연락 완료");
		closureService.complete(timeSlotId, "admin", "정리 완료");

		assertThat(reservationState(reservationId))
			.containsExactly("cancelled", "stable", "none");
		assertThat(closureStatus(timeSlotId)).isEqualTo("COMPLETED");
		assertThat(impactReservationIds()).containsExactly(reservationId);
	}

	@Test
	void 휴강_중에는_일반_관리자_취소를_차단한다() {
		final Long timeSlotId = insertTimeSlot();
		final Long reservationId = insertReservation(insertMember("closure-general-cancel"));
		closureService.start(timeSlotId, "admin", "우천");

		assertThatThrownBy(() -> generalCancellationService.cancel(
			reservationId,
			"admin",
			"stable",
			"none",
			"일반 취소"))
			.isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED.code()));
	}

	@Test
	void 휴강_중에는_승인_입금복구_완료와_노쇼를_차단한다() {
		final Long timeSlotId = insertTimeSlot();
		final Long pendingId = insertReservation(
			insertMember("closure-confirm"),
			"pending_payment");
		final Long completionId = insertReservation(
			insertMember("closure-complete"),
			"confirmed");
		final Long noShowId = insertReservation(
			insertMember("closure-no-show"),
			"confirmed");
		final Long restoreId = insertReservation(
			insertMember("closure-restore"),
			"payment_expired");
		closureService.start(timeSlotId, "admin", "우천");

		assertClosureCommandBlocked(() -> confirmService.confirm(pendingId));
		assertClosureCommandBlocked(() -> paymentRestoreService.restore(
			restoreId,
			"admin",
			"입금 확인"));
		assertClosureCommandBlocked(() -> completionService.complete(completionId));
		assertClosureCommandBlocked(() -> noShowService.process(
			noShowId,
			"admin",
			"none",
			"휴강 중 노쇼 금지"));
	}

	@Test
	void 회원이_휴강_예약을_취소하면_마장_책임으로_쿠폰을_반환한다() {
		final Long timeSlotId = insertTimeSlot();
		final String authSubject = "closure-member-cancel";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId);
		insertHeldLog(memberId, couponId, reservationId);
		closureService.start(timeSlotId, "admin", "우천");

		memberCancellationService.cancel(authSubject, reservationId, "휴강 연락 확인");

		assertThat(reservationState(reservationId))
			.containsExactly("cancelled", "stable", "return");
		assertThat(couponCounts(couponId)).containsExactly(10, 0);
		assertThat(impactReservationIds()).containsExactly(reservationId);
	}

	@Test
	void 처리된_예약이_없으면_철회하고_완료된_휴강은_기존_예약_복구_없이_재개한다() {
		final Long withdrawTimeSlotId = insertTimeSlot();
		insertReservation(insertMember("closure-withdraw"));
		closureService.start(withdrawTimeSlotId, "admin", "우천");

		closureService.withdraw(withdrawTimeSlotId, "admin", "기상 회복");

		assertThat(adminClosed(withdrawTimeSlotId)).isFalse();
		assertThat(closureStatus(withdrawTimeSlotId)).isEqualTo("WITHDRAWN");

		final Long completedTimeSlotId = insertTimeSlot("10:00:00", "10:45:00");
		closureService.start(completedTimeSlotId, "admin", "시설 점검");
		closureService.reopen(completedTimeSlotId, "admin", "점검 완료");

		assertThat(adminClosed(completedTimeSlotId)).isFalse();
		assertThat(closureStatus(completedTimeSlotId)).isEqualTo("COMPLETED");
	}

	@Test
	void 같은_시간대의_동시_휴강_시작은_하나의_작업만_만든다() throws Exception {
		final Long timeSlotId = insertTimeSlot();
		try (var executor = Executors.newFixedThreadPool(2)) {
			final CountDownLatch start = new CountDownLatch(1);
			final var first = executor.submit(() -> {
				start.await();
				closureService.start(timeSlotId, "admin-1", "우천");
				return true;
			});
			final var second = executor.submit(() -> {
				start.await();
				closureService.start(timeSlotId, "admin-2", "우천");
				return true;
			});
			start.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
			assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
		}
		assertThat(closureCount(timeSlotId)).isOne();
	}

	@Test
	void 이미_열린_시간대의_재개와_재개_재시도는_no_op이다() {
		final Long timeSlotId = insertTimeSlot();

		closureService.reopen(timeSlotId, "admin", "재개 재시도");

		assertThat(adminClosed(timeSlotId)).isFalse();
		assertThat(closureCount(timeSlotId)).isZero();
		assertThat(auditCount(timeSlotId, "TIME_SLOT_CLOSURE_REOPENED")).isZero();
	}

	@Test
	void 휴강_시작_중_검증이_실패하면_마감과_작업과_감사를_모두_rollback한다() {
		final Long timeSlotId = insertTimeSlot();

		assertThatThrownBy(() -> closureService.start(timeSlotId, " ", "우천"))
			.isInstanceOf(BusinessException.class);

		assertThat(adminClosed(timeSlotId)).isFalse();
		assertThat(closureCount(timeSlotId)).isZero();
		assertThat(auditCount(timeSlotId, "TIME_SLOT_CLOSURE_STARTED")).isZero();
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM time_slot_closure_impacts");
		jdbcTemplate.update("DELETE FROM time_slot_closures");
		jdbcTemplate.update("DELETE FROM schedule_audit_logs");
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM reservation_member_day_guards");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM schedule_dates");
		jdbcTemplate.update("DELETE FROM members");
	}

	private void insertScheduleDate() {
		jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES (?, 'NORMAL', 1)
			""", LESSON_DATE);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '휴강 회원', ?, FALSE)
			""", authSubject, "010-" + Math.abs(authSubject.hashCode() % 10000) + "-0000");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot() {
		return insertTimeSlot("09:00:00", "09:45:00");
	}

	private Long insertTimeSlot(String startTime, String endTime) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES (?, ?, ?, 'MANUAL', 8, 4,
				JSON_OBJECT(
					'FIRST_RIDE', 2, 'ROUND_BEGINNER', 2, 'ROUND_TROT', 2,
					'LARGE_ARENA_BEGINNER', 3, 'LARGE_ARENA_TROT', 3,
					'CANTER_BEGINNER', 3, 'CANTER', 3,
					'DRESSAGE', 1, 'JUMPING', 1
				))
			""", LESSON_DATE, startTime, endTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(Long memberId) {
		return insertReservation(memberId, "pending_payment");
	}

	private Long insertReservation(Long memberId, String status) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', ?, '09:00:00', '09:45:00', ?,
				'single_payment', '2026-08-01 12:00:00', '2026-08-01 09:00:00',
				CASE WHEN ? = 'confirmed' THEN '2026-08-01 09:30:00' ELSE NULL END)
			""", memberId, LESSON_DATE, status, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(Long memberId, Long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '09:00:00', '09:45:00', 'pending_admin_approval',
				'coupon', ?, '2026-08-01 09:00:00')
			""", memberId, LESSON_DATE, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldLog(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, 'held', 0, '2026-08-01 09:00:00', 'member')
			""", couponId, reservationId, memberId, memberId);
	}

	private java.util.List<Integer> couponCounts(Long couponId) {
		return jdbcTemplate.query("""
			SELECT remaining_count, held_count
			FROM coupons WHERE id = ?
			""", (resultSet, rowNumber) -> java.util.List.of(
				resultSet.getInt("remaining_count"),
				resultSet.getInt("held_count")), couponId).getFirst();
	}

	private boolean adminClosed(Long timeSlotId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
			"SELECT admin_closed FROM time_slot_capacities WHERE id = ?",
			Boolean.class,
			timeSlotId));
	}

	private String closureStatus(Long timeSlotId) {
		return jdbcTemplate.queryForObject("""
			SELECT status FROM time_slot_closures
			WHERE time_slot_id = ? ORDER BY id DESC LIMIT 1
			""", String.class, timeSlotId);
	}

	private int closureCount(Long timeSlotId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM time_slot_closures WHERE time_slot_id = ?",
			Integer.class,
			timeSlotId);
	}

	private Long closureId(Long timeSlotId) {
		return jdbcTemplate.queryForObject("""
			SELECT id FROM time_slot_closures
			WHERE time_slot_id = ? ORDER BY id DESC LIMIT 1
			""", Long.class, timeSlotId);
	}

	private int auditCount(Long timeSlotId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM schedule_audit_logs
			WHERE target_type = 'TIME_SLOT' AND target_key = ? AND action = ?
			""", Integer.class, timeSlotId.toString(), action);
	}

	private java.util.List<Long> impactReservationIds() {
		return jdbcTemplate.queryForList("""
			SELECT reservation_id
			FROM time_slot_closure_impacts
			ORDER BY reservation_id
			""", Long.class);
	}

	private java.util.List<String> reservationState(Long reservationId) {
		return jdbcTemplate.query("""
			SELECT status, cancellation_responsibility, coupon_action
			FROM reservations WHERE id = ?
			""", (resultSet, rowNumber) -> java.util.List.of(
				resultSet.getString("status"),
				resultSet.getString("cancellation_responsibility"),
				resultSet.getString("coupon_action")), reservationId).getFirst();
	}

	private void assertClosureCommandBlocked(Runnable command) {
		assertThatThrownBy(command::run)
			.isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED.code()));
	}
}
