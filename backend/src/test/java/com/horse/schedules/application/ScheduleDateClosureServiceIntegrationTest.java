package com.horse.schedules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import com.horse.global.exception.ExceptionCode;
import com.horse.global.exception.BusinessException;
import com.horse.reservations.application.AdminReservationCancellationService;
import com.horse.reservations.application.ApprovalExpiryService;
import com.horse.reservations.application.MemberReservationCancellationService;
import com.horse.reservations.application.PendingPaymentExpiryService;
import com.horse.reservations.application.ReservationApplicationService;
import com.horse.reservations.application.ReservationChangeService;
import com.horse.reservations.application.ReservationConfirmService;
import com.horse.reservations.application.ReservationPaymentRestoreService;
import com.horse.reservations.application.ReservationRejectService;
import com.horse.reservations.application.ScheduleDateClosureCancellationService;
import com.horse.schedules.domain.ScheduleDateStatus;
import com.horse.timeslots.application.AdminTimeSlotService;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduleDateClosureServiceIntegrationTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant CURRENT_INSTANT = Instant.parse("2026-07-24T01:00:00Z");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 25);
	private static final String ADMIN_SUBJECT = "schedule-admin";

	@Autowired
	ScheduleDateClosureService closureService;

	@Autowired
	ScheduleDateClosureCancellationService closureCancellationService;

	@Autowired
	ReservationApplicationService reservationApplicationService;

	@Autowired
	ReservationConfirmService confirmService;

	@Autowired
	ReservationRejectService rejectService;

	@Autowired
	MemberReservationCancellationService memberCancellationService;

	@Autowired
	AdminReservationCancellationService adminCancellationService;

	@Autowired
	ReservationChangeService changeService;

	@Autowired
	ReservationPaymentRestoreService paymentRestoreService;

	@Autowired
	PendingPaymentExpiryService paymentExpiryService;

	@Autowired
	ApprovalExpiryService approvalExpiryService;

	@Autowired
	AdminTimeSlotService adminTimeSlotService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 날짜_휴무_테스트_데이터를_초기화한다() {
		given(clock.instant()).willReturn(CURRENT_INSTANT);
		given(clock.getZone()).willReturn(SEOUL_ZONE);
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
	}

	@AfterEach
	void 날짜_휴무_테스트_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 활성_예약이_있으면_CLOSING으로_전환하고_영향_ID를_오름차순으로_반환한다() {
		final Long memberId = insertMember("closure-member-1");
		final Long firstReservationId = insertSinglePaymentReservation(memberId, "09:00:00");
		final Long secondReservationId = insertSinglePaymentReservation(memberId, "10:00:00");
		insertScheduleDate();

		final ScheduleDateClosureResult result = closureService.start(
			LESSON_DATE,
			ADMIN_SUBJECT,
			"우천 휴무");
		final ScheduleDateClosureImpact impact = closureService.impact(LESSON_DATE);

		assertThat(result.status()).isEqualTo(ScheduleDateStatus.CLOSING);
		assertThat(result.resumeStatus()).isEqualTo(ScheduleDateStatus.NORMAL);
		assertThat(result.activeReservationCount()).isEqualTo(2);
		assertThat(impact.reservationIds()).containsExactly(
			firstReservationId,
			secondReservationId);
		assertThatThrownBy(() -> closureService.finalizeClosure(
			LESSON_DATE,
			ADMIN_SUBJECT,
			"예약이 남은 상태"))
			.isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.code()).isEqualTo(
					ExceptionCode.TIMESLOT_ACTIVE_RESERVATIONS_EXIST_ON_CLOSURE_DATE.code()));
		assertThat(scheduleAuditActions()).containsExactly("CLOSING_STARTED");
	}

	@Test
	void 활성_예약이_없으면_휴무_시작과_동시에_CLOSED로_확정한다() {
		insertScheduleDate();

		final ScheduleDateClosureResult result = closureService.start(
			LESSON_DATE,
			ADMIN_SUBJECT,
			"시설 점검");

		assertThat(result.status()).isEqualTo(ScheduleDateStatus.CLOSED);
		assertThat(result.resumeStatus()).isNull();
		assertThat(result.activeReservationCount()).isZero();
		assertThat(scheduleAuditActions()).containsExactly("CLOSING_STARTED", "CLOSED");
	}

	@Test
	void 쿠폰_예약은_stable_RETURN으로_정리한_뒤_CLOSED로_확정한다() {
		final Long memberId = insertMember("closure-coupon-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId, "09:00:00");
		insertHeldLog(memberId, couponId, reservationId);
		insertScheduleDate();
		closureService.start(LESSON_DATE, ADMIN_SUBJECT, "우천 휴무");

		final var cancellation = closureCancellationService.cancel(
			LESSON_DATE,
			reservationId,
			ADMIN_SUBJECT,
			"날짜 휴무 정리");
		final var repeated = closureCancellationService.cancel(
			LESSON_DATE,
			reservationId,
			ADMIN_SUBJECT,
			"날짜 휴무 정리");
		final ScheduleDateClosureResult closed = closureService.finalizeClosure(
			LESSON_DATE,
			ADMIN_SUBJECT,
			"예약 정리 완료");

		assertThat(cancellation.changed()).isTrue();
		assertThat(repeated.changed()).isFalse();
		assertThat(closed.status()).isEqualTo(ScheduleDateStatus.CLOSED);
		assertThat(reservationCancellation(reservationId))
			.containsExactly("cancelled", "stable", "return");
		assertThat(couponCounts(couponId)).containsExactly(10, 0);
		assertThat(usageCount(reservationId, "released")).isOne();
		assertThat(changeLogCount(reservationId)).isOne();
		assertThat(scheduleAuditActions()).containsExactly(
			"CLOSING_STARTED",
			"CLOSURE_RESERVATION_CANCELLED",
			"CLOSED");
	}

	@Test
	void 일회_결제_예약은_stable_NONE으로_정리한다() {
		final Long memberId = insertMember("closure-payment-member");
		final Long reservationId = insertSinglePaymentReservation(memberId, "09:00:00");
		insertScheduleDate();
		closureService.start(LESSON_DATE, ADMIN_SUBJECT, "시설 점검");

		closureCancellationService.cancel(
			LESSON_DATE,
			reservationId,
			ADMIN_SUBJECT,
			"날짜 휴무 정리");

		assertThat(reservationCancellation(reservationId))
			.containsExactly("cancelled", "stable", "none");
		assertThat(usageCount(reservationId, "released")).isZero();
	}

	@Test
	void 쿠폰_반환에_실패하면_예약과_감사_이력을_rollback하고_CLOSING을_유지한다() {
		final Long memberId = insertMember("closure-rollback-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId, "09:00:00");
		insertScheduleDate();
		closureService.start(LESSON_DATE, ADMIN_SUBJECT, "우천 휴무");

		assertThatThrownBy(() -> closureCancellationService.cancel(
			LESSON_DATE,
			reservationId,
			ADMIN_SUBJECT,
			"반환 실패"))
			.isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.COUPON_HOLD_STATE_CONFLICT.code()));

		assertThat(reservationStatus(reservationId)).isEqualTo("pending_admin_approval");
		assertThat(changeLogCount(reservationId)).isZero();
		assertThat(scheduleStatus()).isEqualTo("CLOSING");
		assertThat(scheduleAuditActions()).containsExactly("CLOSING_STARTED");
	}

	@Test
	void CLOSING에서는_승인과_회원_취소를_막고_반려는_허용한다() {
		final Long memberId = insertMember("closure-matrix-member");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertCouponReservation(memberId, couponId, "09:00:00");
		insertHeldLog(memberId, couponId, reservationId);
		insertScheduleDate();
		closureService.start(LESSON_DATE, ADMIN_SUBJECT, "우천 휴무");

		assertScheduleException(() -> confirmService.confirm(reservationId));
		assertScheduleException(() -> memberCancellationService.cancel(
			"closure-matrix-member",
			reservationId,
			"회원 취소"));

		rejectService.reject(reservationId, ADMIN_SUBJECT, "휴무로 반려");

		assertThat(reservationStatus(reservationId)).isEqualTo("rejected");
		assertThat(couponCounts(couponId)).containsExactly(10, 0);
	}

	@Test
	void CLOSING에서는_변경_복구와_일반_관리자_취소를_막는다() {
		final Long memberId = insertMember("closure-reentry-member");
		final Long reservationId = insertSinglePaymentReservation(memberId, "09:00:00");
		insertTimeSlot("09:00:00");
		final Long targetTimeSlotId = insertTimeSlot("10:00:00");
		insertScheduleDate();
		closureService.start(LESSON_DATE, ADMIN_SUBJECT, "우천 휴무");

		assertScheduleException(() -> changeService.changeByAdmin(
			ADMIN_SUBJECT,
			reservationId,
			targetTimeSlotId,
			"휴무 날짜 변경 시도"));
		assertScheduleException(() -> adminCancellationService.cancel(
			reservationId,
			ADMIN_SUBJECT,
			"stable",
			"none",
			"일반 취소 시도"));
		jdbcTemplate.update(
			"UPDATE reservations SET status = 'payment_expired' WHERE id = ?",
			reservationId);
		assertScheduleException(() -> paymentRestoreService.restore(
			reservationId,
			ADMIN_SUBJECT,
			"휴무 날짜 복구 시도"));

		assertThat(reservationStatus(reservationId)).isEqualTo("payment_expired");
		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void CLOSING에서도_자동_입금_만료와_자동_승인_만료를_허용한다() {
		final Long paymentMemberId = insertMember("closure-payment-expiry-member");
		final Long paymentReservationId = insertSinglePaymentReservation(
			paymentMemberId,
			"09:00:00");
		jdbcTemplate.update(
			"UPDATE reservations SET payment_due_at = '2026-07-24 09:00:00' WHERE id = ?",
			paymentReservationId);
		final Long couponMemberId = insertMember("closure-approval-expiry-member");
		final Long couponId = insertCoupon(couponMemberId);
		final Long couponReservationId = insertCouponReservation(
			couponMemberId,
			couponId,
			"10:00:00");
		insertHeldLog(couponMemberId, couponId, couponReservationId);
		insertScheduleDate();
		closureService.start(LESSON_DATE, ADMIN_SUBJECT, "우천 휴무");

		assertThat(paymentExpiryService.expireDuePayments().expiredCount()).isOne();
		given(clock.instant()).willReturn(Instant.parse("2026-07-25T01:00:00Z"));
		assertThat(approvalExpiryService.expireDueApprovals().expiredCount()).isOne();

		assertThat(reservationStatus(paymentReservationId)).isEqualTo("payment_expired");
		assertThat(reservationStatus(couponReservationId)).isEqualTo("approval_expired");
		assertThat(couponCounts(couponId)).containsExactly(10, 0);
	}

	@Test
	void CLOSING을_취소하면_직전_상태로_복귀하고_취소된_예약은_복원하지_않는다() {
		final Long memberId = insertMember("closure-resume-member");
		final Long reservationId = insertSinglePaymentReservation(memberId, "09:00:00");
		insertScheduleDate();
		closureService.start(LESSON_DATE, ADMIN_SUBJECT, "임시 휴무");
		closureCancellationService.cancel(
			LESSON_DATE,
			reservationId,
			ADMIN_SUBJECT,
			"날짜 휴무 정리");

		final ScheduleDateClosureResult resumed = closureService.cancelClosing(
			LESSON_DATE,
			ADMIN_SUBJECT,
			"정상 운영 복귀");

		assertThat(resumed.status()).isEqualTo(ScheduleDateStatus.NORMAL);
		assertThat(resumed.resumeStatus()).isNull();
		assertThat(reservationStatus(reservationId)).isEqualTo("cancelled");
		assertThat(scheduleAuditActions()).containsExactly(
			"CLOSING_STARTED",
			"CLOSURE_RESERVATION_CANCELLED",
			"CLOSING_CANCELLED");
	}

	@Test
	void 예약_신청과_CLOSING_전환이_경쟁해도_CLOSED에_활성_예약이_남지_않는다() throws Exception {
		final Long memberId = insertMember("closure-race-member");
		final Long timeSlotId = insertTimeSlot("13:30:00");
		insertScheduleDate();
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final Future<Boolean> reservation = executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
				try {
					reservationApplicationService.apply(
						"closure-race-member",
						timeSlotId,
						"FIRST_RIDE");
					return true;
				}
				catch (BusinessException exception) {
					return false;
				}
			});
			final Future<ScheduleDateClosureResult> closure = executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
				return closureService.start(
					LESSON_DATE,
					ADMIN_SUBJECT,
					"경쟁 휴무");
			});
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			final boolean reservationCreated = reservation.get(10, TimeUnit.SECONDS);
			final ScheduleDateClosureResult closureResult = closure.get(10, TimeUnit.SECONDS);
			final int activeCount = activeReservationCount();
			if (reservationCreated) {
				assertThat(closureResult.status()).isEqualTo(ScheduleDateStatus.CLOSING);
				assertThat(activeCount).isOne();
			}
			else {
				assertThat(closureResult.status()).isEqualTo(ScheduleDateStatus.CLOSED);
				assertThat(activeCount).isZero();
			}
		}
	}

	@Test
	void 수동_시간대_생성과_CLOSING_전환은_같은_날짜에서_직렬화된다() throws Exception {
		insertScheduleDate();
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			final Future<Boolean> creation = executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
				try {
					adminTimeSlotService.createTimeSlot(
						LESSON_DATE,
						java.time.LocalTime.of(14, 30),
						8,
						4,
						Map.of(
							"FIRST_RIDE", 2,
							"ROUND_BEGINNER", 2,
							"ROUND_TROT", 2,
							"LARGE_ARENA_BEGINNER", 3,
							"LARGE_ARENA_TROT", 3,
							"DRESSAGE", 1,
							"JUMPING", 1));
					return true;
				}
				catch (BusinessException exception) {
					return false;
				}
			});
			final Future<ScheduleDateClosureResult> closure = executor.submit(() -> {
				ready.countDown();
				assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
				return closureService.start(
					LESSON_DATE,
					ADMIN_SUBJECT,
					"수동 시간대 경쟁 휴무");
			});
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			final boolean timeSlotCreated = creation.get(10, TimeUnit.SECONDS);
			final ScheduleDateClosureResult closureResult = closure.get(10, TimeUnit.SECONDS);
			assertThat(closureResult.status()).isEqualTo(ScheduleDateStatus.CLOSED);
			assertThat(timeSlotCount()).isEqualTo(timeSlotCreated ? 1 : 0);
		}
	}

	private void assertScheduleException(Runnable action) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(
				BusinessException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.SCHEDULE_DATE_NOT_RESERVABLE.code()));
	}

	private void clearDatabase() {
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
			VALUES (?, '휴무 회원', ?, FALSE)
			""", authSubject, "010-" + Math.abs(authSubject.hashCode() % 10000) + "-0000");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
			) VALUES (?, 'general', 10, 10, 1, 'active', 'schedule-admin')
			""", memberId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(Long memberId, Long couponId, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status, payment_source,
				coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ADDTIME(?, '00:45:00'),
				'pending_admin_approval', 'coupon', ?, '2026-07-24 09:00:00')
			""", memberId, LESSON_DATE, startTime, startTime, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertSinglePaymentReservation(Long memberId, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ADDTIME(?, '00:45:00'),
				'pending_payment', 'single_payment',
				'2026-07-24 12:00:00', '2026-07-24 09:00:00')
			""", memberId, LESSON_DATE, startTime, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot(String startTime) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES (?, ?, ADDTIME(?, '00:45:00'), 'MANUAL', 8, 4,
				JSON_OBJECT(
					'FIRST_RIDE', 2,
					'ROUND_BEGINNER', 2,
					'ROUND_TROT', 2,
					'LARGE_ARENA_BEGINNER', 3,
					'LARGE_ARENA_TROT', 3,
					'DRESSAGE', 1,
					'JUMPING', 1
				))
			""", LESSON_DATE, startTime, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldLog(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, 'held', 0, '2026-07-24 09:00:00', 'member')
			""", couponId, reservationId, memberId, memberId);
	}

	private List<String> reservationCancellation(Long reservationId) {
		return jdbcTemplate.query("""
			SELECT status, cancellation_responsibility, coupon_action
			FROM reservations
			WHERE id = ?
			""", (resultSet, rowNumber) -> List.of(
				resultSet.getString("status"),
				resultSet.getString("cancellation_responsibility"),
				resultSet.getString("coupon_action")), reservationId).getFirst();
	}

	private List<Integer> couponCounts(Long couponId) {
		return jdbcTemplate.query("""
			SELECT remaining_count, held_count
			FROM coupons
			WHERE id = ?
			""", (resultSet, rowNumber) -> List.of(
				resultSet.getInt("remaining_count"),
				resultSet.getInt("held_count")), couponId).getFirst();
	}

	private int usageCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM coupon_usage_logs
			WHERE reservation_id = ? AND action = ?
			""", Integer.class, reservationId, action);
	}

	private int changeLogCount(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservation_change_logs
			WHERE reservation_id = ?
			""", Integer.class, reservationId);
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?",
			String.class,
			reservationId);
	}

	private String scheduleStatus() {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM schedule_dates WHERE schedule_date = ?",
			String.class,
			LESSON_DATE);
	}

	private List<String> scheduleAuditActions() {
		return jdbcTemplate.queryForList("""
			SELECT action
			FROM schedule_audit_logs
			WHERE target_type = 'SCHEDULE_DATE' AND target_key = ?
			ORDER BY id
			""", String.class, LESSON_DATE.toString());
	}

	private int activeReservationCount() {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservations
			WHERE lesson_date = ? AND active_slot_guard = 1
			""", Integer.class, LESSON_DATE);
	}

	private int timeSlotCount() {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM time_slot_capacities WHERE lesson_date = ?",
			Integer.class,
			LESSON_DATE);
	}
}
