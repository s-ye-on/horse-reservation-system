package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReservationChangeApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant BEFORE_CUTOFF_INSTANT = Instant.parse("2026-07-15T01:00:00Z");
	private static final Instant AFTER_CUTOFF_INSTANT = Instant.parse("2026-07-31T13:00:00Z");
	private static final Instant AFTER_CUTOFF_WEEKDAY_INSTANT = Instant.parse("2026-08-02T13:00:00Z");
	private static final String CLASS_CAPACITIES = """
		{
		  "FIRST_RIDE": 8,
		  "ROUND_BEGINNER": 4,
		  "ROUND_TROT": 4,
		  "LARGE_ARENA_BEGINNER": 8,
		  "LARGE_ARENA_TROT": 8,
		  "DRESSAGE": 8,
		  "JUMPING": 8
		}
		""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(BEFORE_CUTOFF_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 회원은_마감_전_본인_쿠폰_예약을_다른_시간대로_변경한다() throws Exception {
		final String authSubject = "change-member";
		final Long memberId = insertMember(authSubject);
		final LocalDate lessonDate = futureDate();
		final Long couponId = insertCoupon(memberId, lessonDate.plusMonths(1), false);
		final Long sourceTimeSlotId = insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate.plusDays(1), "10:00:00", 8);
		final Long reservationId = insertCouponReservation(
			memberId, couponId, lessonDate, "09:00:00", "confirmed");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, "개인 일정 변경")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationId").value(reservationId))
			.andExpect(jsonPath("$.lessonDate").value(lessonDate.plusDays(1).toString()))
			.andExpect(jsonPath("$.startTime").value("10:00:00"))
			.andExpect(jsonPath("$.status").value("confirmed"))
			.andExpect(jsonPath("$.couponAction").value("none"))
			.andExpect(jsonPath("$.freeChangeUsed").value(false))
			.andExpect(jsonPath("$.changed").value(true));

		assertThat(sourceTimeSlotId).isNotEqualTo(targetTimeSlotId);
		assertThat(reservationSchedule(reservationId)).containsExactly(
			lessonDate.plusDays(1).toString(), "10:00:00");
		assertThat(reservationAuditValue(reservationId, "status")).isEqualTo("confirmed");
		assertThat(reservationAuditValue(reservationId, "coupon_id")).isEqualTo(couponId.toString());
		assertThat(changeLogCount(reservationId)).isEqualTo(1);
	}

	@Test
	void 관리자는_필수_메모와_함께_입금대기_예약을_변경하고_기존_마감을_유지한다() throws Exception {
		final Long memberId = insertMember("admin-change-member");
		final LocalDate lessonDate = futureDate();
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate, "11:00:00", 8);
		final Long reservationId = insertSinglePaymentReservation(memberId, lessonDate, "09:00:00");
		final String paymentDueAt = reservationAuditValue(reservationId, "payment_due_at");
		final String approvalRequestedAt = reservationAuditValue(reservationId, "approval_requested_at");

		mockMvc.perform(post("/api/admin/reservations/{reservationId}/change", reservationId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adminRequest(targetTimeSlotId, "회원과 통화 후 시간 변경")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("pending_payment"));

		assertThat(reservationAuditValue(reservationId, "payment_due_at")).isEqualTo(paymentDueAt);
		assertThat(reservationAuditValue(reservationId, "approval_requested_at"))
			.isEqualTo(approvalRequestedAt);
		assertThat(changeLogMemo(reservationId)).isEqualTo("회원과 통화 후 시간 변경");
	}

	@Test
	void 다른_회원의_예약과_메모_없는_관리자_변경은_거부한다() throws Exception {
		final Long ownerId = insertMember("change-owner");
		insertMember("change-other");
		final LocalDate lessonDate = futureDate();
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate, "10:00:00", 8);
		final Long reservationId = insertSinglePaymentReservation(ownerId, lessonDate, "09:00:00");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
				.with(memberJwt("change-other"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, null)))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/admin/reservations/{reservationId}/change", reservationId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(adminRequest(targetTimeSlotId, " ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_ADMIN_MEMO"));

		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void 대상_정원이_가득_차면_원본_예약을_유지한다() throws Exception {
		final Long firstMemberId = insertMember("capacity-change-member");
		final Long occupyingMemberId = insertMember("capacity-occupying-member");
		final LocalDate lessonDate = futureDate();
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate, "10:00:00", 1);
		final Long reservationId = insertSinglePaymentReservation(firstMemberId, lessonDate, "09:00:00");
		insertSinglePaymentReservation(occupyingMemberId, lessonDate, "10:00:00");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
				.with(memberJwt("capacity-change-member"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_CAPACITY_EXCEEDED"));

		assertThat(reservationSchedule(reservationId)).containsExactly(lessonDate.toString(), "09:00:00");
		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void 동일한_대상으로_재시도하면_변경_이력을_중복_생성하지_않는다() throws Exception {
		final Long memberId = insertMember("retry-change-member");
		final LocalDate lessonDate = futureDate();
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate, "10:00:00", 8);
		final Long reservationId = insertSinglePaymentReservation(memberId, lessonDate, "09:00:00");

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
					.with(memberJwt("retry-change-member"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(memberRequest(targetTimeSlotId, "같은 요청 재시도")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.changed").value(attempt == 0));
		}

		assertThat(changeLogCount(reservationId)).isEqualTo(1);
	}

	@Test
	void 마감_후_쿠폰_예약은_무료_변경권을_소비하고_로그를_한_번만_남긴다() throws Exception {
		when(clock.instant()).thenReturn(AFTER_CUTOFF_INSTANT);

		final Long memberId = insertMember("after-cutoff-member");
		final LocalDate lessonDate = LocalDate.of(2026, 8, 1);
		final Long couponId = insertCoupon(memberId, lessonDate.plusMonths(1), false);
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate.plusDays(1), "10:00:00", 8);
		final Long reservationId = insertCouponReservation(
			memberId, couponId, lessonDate, "09:00:00", "confirmed");

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
					.with(memberJwt("after-cutoff-member"))
					.contentType(MediaType.APPLICATION_JSON)
					.content(memberRequest(targetTimeSlotId, "마감 후 일정 변경")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.couponAction").value("free_change_used"))
				.andExpect(jsonPath("$.freeChangeUsed").value(true))
				.andExpect(jsonPath("$.changed").value(attempt == 0));
		}

		assertThat(reservationSchedule(reservationId)).containsExactly(
			lessonDate.plusDays(1).toString(), "10:00:00");
		assertThat(couponFreeChangeUsed(couponId)).isTrue();
		assertThat(couponUsageCount(reservationId, "free_change_used")).isEqualTo(1);
		assertThat(changeLogCount(reservationId)).isEqualTo(1);
		assertThat(changeLogCouponAction(reservationId)).isEqualTo("free_change_used");
	}

	@Test
	void 마감_후_일회_결제_예약과_이미_무료_변경권을_쓴_쿠폰_예약은_거부한다() throws Exception {
		when(clock.instant()).thenReturn(AFTER_CUTOFF_INSTANT);

		final Long singlePaymentMemberId = insertMember("after-cutoff-single-payment");
		final Long usedCouponMemberId = insertMember("after-cutoff-used-coupon");
		final LocalDate lessonDate = LocalDate.of(2026, 8, 1);
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate.plusDays(1), "10:00:00", 8);
		final Long singlePaymentReservationId = insertSinglePaymentReservation(
			singlePaymentMemberId, lessonDate, "09:00:00");
		final Long usedCouponId = insertCoupon(usedCouponMemberId, lessonDate.plusMonths(1), true);
		final Long usedCouponReservationId = insertCouponReservation(
			usedCouponMemberId, usedCouponId, lessonDate, "09:00:00", "confirmed");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", singlePaymentReservationId)
				.with(memberJwt("after-cutoff-single-payment"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, "마감 후 변경 시도")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_CHANGE_NOT_ALLOWED"));
		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", usedCouponReservationId)
				.with(memberJwt("after-cutoff-used-coupon"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, "무료 변경권 재사용 시도")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_CHANGE_NOT_ALLOWED"));

		assertThat(couponUsageCount(usedCouponReservationId, "free_change_used")).isZero();
		assertThat(changeLogCount(usedCouponReservationId)).isZero();
	}

	@Test
	void 마감_후_대상_정원이_가득_차면_무료_변경권과_원본_예약을_유지한다() throws Exception {
		when(clock.instant()).thenReturn(AFTER_CUTOFF_INSTANT);

		final Long memberId = insertMember("full-target-free-change-member");
		final Long occupyingMemberId = insertMember("full-target-occupying-member");
		final LocalDate lessonDate = LocalDate.of(2026, 8, 1);
		final Long couponId = insertCoupon(memberId, lessonDate.plusMonths(1), false);
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate.plusDays(1), "10:00:00", 1);
		final Long reservationId = insertCouponReservation(
			memberId, couponId, lessonDate, "09:00:00", "confirmed");
		insertSinglePaymentReservation(
			occupyingMemberId, lessonDate.plusDays(1), "10:00:00");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
				.with(memberJwt("full-target-free-change-member"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, "마감 후 정원 충돌")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_CAPACITY_EXCEEDED"));

		assertThat(reservationSchedule(reservationId)).containsExactly(
			lessonDate.toString(), "09:00:00");
		assertThat(couponFreeChangeUsed(couponId)).isFalse();
		assertThat(couponUsageCount(reservationId, "free_change_used")).isZero();
		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void 같은_쿠폰의_동시_마감후_변경은_하나만_성공한다() throws Exception {
		when(clock.instant()).thenReturn(AFTER_CUTOFF_INSTANT);

		final Long memberId = insertMember("shared-coupon-member");
		final LocalDate lessonDate = LocalDate.of(2026, 8, 1);
		final Long couponId = insertCoupon(memberId, lessonDate.plusMonths(1), false);
		insertTimeSlot(lessonDate, "09:00:00", 8);
		insertTimeSlot(lessonDate, "10:00:00", 8);
		final Long firstTargetTimeSlotId = insertTimeSlot(lessonDate.plusDays(1), "11:00:00", 8);
		final Long secondTargetTimeSlotId = insertTimeSlot(lessonDate.plusDays(1), "12:00:00", 8);
		final Long firstReservationId = insertCouponReservation(
			memberId, couponId, lessonDate, "09:00:00", "confirmed");
		final Long secondReservationId = insertCouponReservation(
			memberId, couponId, lessonDate, "10:00:00", "confirmed");

		final List<Integer> statuses = concurrentChanges(
			List.of("shared-coupon-member", "shared-coupon-member"),
			List.of(firstReservationId, secondReservationId),
			List.of(firstTargetTimeSlotId, secondTargetTimeSlotId));

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(couponFreeChangeUsed(couponId)).isTrue();
		assertThat(couponUsageCountByCoupon(couponId, "free_change_used")).isEqualTo(1);
		assertThat(changeLogCouponActions(couponId)).containsExactly("free_change_used");
	}

	@Test
	void 마감_후_평일_당일_변경은_결제수단과_무관하게_무료_변경권을_사용하지_않는다() throws Exception {
		when(clock.instant()).thenReturn(AFTER_CUTOFF_WEEKDAY_INSTANT);

		final LocalDate lessonDate = LocalDate.of(2026, 8, 3);
		final Long couponMemberId = insertMember("weekday-coupon-member");
		final Long singlePaymentMemberId = insertMember("weekday-single-payment-member");
		final Long couponId = insertCoupon(couponMemberId, lessonDate.plusMonths(1), false);
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long couponTargetId = insertTimeSlot(lessonDate, "10:00:00", 8);
		insertTimeSlot(lessonDate, "11:00:00", 8);
		final Long singlePaymentTargetId = insertTimeSlot(lessonDate, "12:00:00", 8);
		final Long couponReservationId = insertCouponReservation(
			couponMemberId, couponId, lessonDate, "09:00:00", "confirmed");
		final Long singlePaymentReservationId = insertSinglePaymentReservation(
			singlePaymentMemberId, lessonDate, "11:00:00");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", couponReservationId)
				.with(memberJwt("weekday-coupon-member"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(couponTargetId, "평일 당일 변경")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.couponAction").value("none"))
			.andExpect(jsonPath("$.freeChangeUsed").value(false));
		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", singlePaymentReservationId)
				.with(memberJwt("weekday-single-payment-member"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(singlePaymentTargetId, "평일 당일 변경")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.couponAction").value("none"));

		assertThat(couponFreeChangeUsed(couponId)).isFalse();
		assertThat(couponUsageCount(couponReservationId, "free_change_used")).isZero();
		assertThat(changeLogCouponAction(couponReservationId)).isEqualTo("none");
	}

	@Test
	void 마감_후_평일이라도_무료_변경권이_없으면_다른_날짜로_변경할_수_없다() throws Exception {
		when(clock.instant()).thenReturn(AFTER_CUTOFF_WEEKDAY_INSTANT);

		final LocalDate lessonDate = LocalDate.of(2026, 8, 3);
		final Long memberId = insertMember("weekday-other-date-member");
		final Long couponId = insertCoupon(memberId, lessonDate.plusMonths(1), true);
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate.plusDays(1), "10:00:00", 8);
		final Long reservationId = insertCouponReservation(
			memberId, couponId, lessonDate, "09:00:00", "confirmed");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
				.with(memberJwt("weekday-other-date-member"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, "다른 날짜 변경")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_CHANGE_NOT_ALLOWED"));

		assertThat(reservationSchedule(reservationId)).containsExactly(
			lessonDate.toString(), "09:00:00");
		assertThat(changeLogCount(reservationId)).isZero();
	}

	@Test
	void 마감_후_토요일과_일요일_당일_변경은_무료_변경권이_있어도_거부한다() throws Exception {
		assertWeekendSameDayChangeRejected(
			LocalDate.of(2026, 8, 1),
			Instant.parse("2026-07-31T13:00:00Z"),
			"saturday");
		assertWeekendSameDayChangeRejected(
			LocalDate.of(2026, 8, 2),
			Instant.parse("2026-08-01T13:00:00Z"),
			"sunday");
	}

	@Test
	void 두_예약이_한_자리로_동시에_변경되면_하나만_성공한다() throws Exception {
		final Long firstMemberId = insertMember("concurrent-change-first");
		final Long secondMemberId = insertMember("concurrent-change-second");
		final LocalDate lessonDate = futureDate();
		insertTimeSlot(lessonDate, "09:00:00", 8);
		insertTimeSlot(lessonDate, "10:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate, "11:00:00", 1);
		final Long firstReservationId = insertSinglePaymentReservation(
			firstMemberId, lessonDate, "09:00:00");
		final Long secondReservationId = insertSinglePaymentReservation(
			secondMemberId, lessonDate, "10:00:00");

		final List<Integer> statuses = concurrentChanges(
			List.of("concurrent-change-first", "concurrent-change-second"),
			List.of(firstReservationId, secondReservationId),
			List.of(targetTimeSlotId, targetTimeSlotId));

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(activeOccupancy(lessonDate, "11:00:00")).isEqualTo(1);
		assertThat(changeLogCount(firstReservationId) + changeLogCount(secondReservationId)).isEqualTo(1);
	}

	private List<Integer> concurrentChanges(
		List<String> authSubjects,
		List<Long> reservationIds,
		List<Long> targetTimeSlotIds
	) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationIds.get(index))
							.with(memberJwt(authSubjects.get(index)))
							.contentType(MediaType.APPLICATION_JSON)
							.content(memberRequest(targetTimeSlotIds.get(index), null)))
						.andReturn().getResponse().getStatus();
				}))
				.toList();
			ready.await();
			start.countDown();
			return futures.stream().map(this::getResult).toList();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private int getResult(Future<Integer> future) {
		try {
			return future.get();
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private void assertWeekendSameDayChangeRejected(
		LocalDate lessonDate,
		Instant requestedAt,
		String suffix
	) throws Exception {
		when(clock.instant()).thenReturn(requestedAt);
		final String authSubject = "weekend-change-" + suffix;
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId, lessonDate.plusMonths(1), false);
		insertTimeSlot(lessonDate, "09:00:00", 8);
		final Long targetTimeSlotId = insertTimeSlot(lessonDate, "10:00:00", 8);
		final Long reservationId = insertCouponReservation(
			memberId, couponId, lessonDate, "09:00:00", "confirmed");

		mockMvc.perform(post("/api/me/reservations/{reservationId}/change", reservationId)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(memberRequest(targetTimeSlotId, "주말 당일 변경")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code")
				.value("RESERVATION_WEEKEND_SAME_DAY_CHANGE_NOT_ALLOWED"));

		assertThat(reservationSchedule(reservationId)).containsExactly(
			lessonDate.toString(), "09:00:00");
		assertThat(couponFreeChangeUsed(couponId)).isFalse();
		assertThat(couponUsageCount(reservationId, "free_change_used")).isZero();
		assertThat(changeLogCount(reservationId)).isZero();
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '변경 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, LocalDate expiresAt, boolean freeChangeUsed) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, free_change_used, created_by
			) VALUES (?, 'general', 10, 10, 1, ?, ?, ?, 'change-test-admin')
			""", memberId, expiresAt.minusMonths(3).atStartOfDay(), expiresAt.atStartOfDay(), freeChangeUsed);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot(LocalDate lessonDate, String startTime, int totalCapacity) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity, round_arena_capacity,
				class_capacity_json, is_closed
			) VALUES (?, ?, ?, ?, ?, FALSE)
			""", lessonDate, startTime, totalCapacity, Math.min(totalCapacity, 4), CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCouponReservation(
		Long memberId,
		Long couponId,
		LocalDate lessonDate,
		String startTime,
		String status
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, ?, 'coupon', ?, NOW(6), NOW(6))
			""", memberId, lessonDate, startTime, status, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertSinglePaymentReservation(Long memberId, LocalDate lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, ?, 'pending_payment', 'single_payment',
				NOW(6) + INTERVAL 2 HOUR, NOW(6))
			""", memberId, lessonDate, startTime);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String[] reservationSchedule(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT CAST(lesson_date AS CHAR), CAST(start_time AS CHAR)
			FROM reservations WHERE id = ?
			""", (resultSet, rowNumber) -> new String[] {resultSet.getString(1), resultSet.getString(2)}, reservationId);
	}

	private String reservationAuditValue(Long reservationId, String column) {
		return jdbcTemplate.queryForObject(
			"SELECT CAST(" + column + " AS CHAR) FROM reservations WHERE id = ?",
			String.class,
			reservationId);
	}

	private int changeLogCount(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservation_change_logs WHERE reservation_id = ?",
			Integer.class,
			reservationId);
	}

	private String changeLogCouponAction(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT coupon_action FROM reservation_change_logs WHERE reservation_id = ?",
			String.class,
			reservationId);
	}

	private List<String> changeLogCouponActions(Long couponId) {
		return jdbcTemplate.queryForList("""
			SELECT rcl.coupon_action
			FROM reservation_change_logs rcl
			JOIN reservations r ON r.id = rcl.reservation_id
			WHERE r.coupon_id = ?
			ORDER BY rcl.id
			""", String.class, couponId);
	}

	private String changeLogMemo(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT memo FROM reservation_change_logs WHERE reservation_id = ?",
			String.class,
			reservationId);
	}

	private boolean couponFreeChangeUsed(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT free_change_used FROM coupons WHERE id = ?",
			Boolean.class,
			couponId);
	}

	private int couponUsageCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM coupon_usage_logs
			WHERE reservation_id = ? AND action = ?
			""", Integer.class, reservationId, action);
	}

	private int couponUsageCountByCoupon(Long couponId, String action) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM coupon_usage_logs
			WHERE coupon_id = ? AND action = ?
			""", Integer.class, couponId, action);
	}

	private int activeOccupancy(LocalDate lessonDate, String startTime) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM reservations
			WHERE lesson_date = ? AND start_time = ?
			  AND status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
			""", Integer.class, lessonDate, startTime);
	}

	private LocalDate futureDate() {
		return LocalDate.now(clock).plusDays(7);
	}

	private String memberRequest(Long targetTimeSlotId, String reason) {
		return """
			{"targetTimeSlotId": %d, "reason": %s}
			""".formatted(targetTimeSlotId, reason == null ? "null" : "\"" + reason + "\"");
	}

	private String adminRequest(Long targetTimeSlotId, String memo) {
		return """
			{"targetTimeSlotId": %d, "memo": "%s"}
			""".formatted(targetTimeSlotId, memo);
	}

	private RequestPostProcessor memberJwt(String authSubject) {
		return jwt().jwt(token -> token.subject(authSubject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private RequestPostProcessor adminJwt() {
		return jwt().jwt(token -> token.subject("change-admin"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM members");
	}
}
