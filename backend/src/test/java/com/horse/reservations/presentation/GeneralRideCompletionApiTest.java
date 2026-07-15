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
class GeneralRideCompletionApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant COMPLETED_INSTANT = Instant.parse("2026-07-14T01:00:00Z");
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
	void 데이터베이스와_완료_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(COMPLETED_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 관리자는_일반_쿠폰_기승을_완료하고_쿠폰과_일반_횟수를_한_번_반영한다() throws Exception {
		final Long memberId = insertMember("general-completion-member", 5);
		final Long couponId = insertCoupon(memberId, 10, 1, null, null, "active");
		final Long reservationId = insertConfirmedReservation(
			memberId, couponId, "FIRST_RIDE", "coupon", LocalDate.of(2026, 8, 1));
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		mockMvc.perform(post(completionEndpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reservationId").value(reservationId))
			.andExpect(jsonPath("$.status").value("completed"))
			.andExpect(jsonPath("$.paymentSource").value("coupon"))
			.andExpect(jsonPath("$.couponId").value(couponId))
			.andExpect(jsonPath("$.generalRideCount").value(6));

		assertThat(reservationStatus(reservationId)).isEqualTo("completed");
		assertThat(memberGeneralRideCount(memberId)).isEqualTo(6);
		assertThat(couponCounts(couponId)).containsExactly(9, 0);
		assertThat(couponDate(couponId, "first_used_at")).isEqualTo("2026-08-01 00:00:00");
		assertThat(couponDate(couponId, "expires_at")).isEqualTo("2026-11-01 00:00:00");
		assertThat(usageActionCount(reservationId, "used")).isEqualTo(1);
		assertThat(usedLog(reservationId)).containsExactly("-1", "admin");
	}

	@Test
	void 기존에_정상_점유된_예약은_이후_계산된_만료일을_넘어도_완료하고_차감한다() throws Exception {
		final Long memberId = insertMember("fallback-completion-member", 2);
		final Long couponId = insertCoupon(
			memberId, 9, 1, "2026-01-01 00:00:00", "2026-04-01 00:00:00", "expired");
		final Long reservationId = insertConfirmedReservation(
			memberId, couponId, "ROUND_BEGINNER", "coupon", LocalDate.of(2026, 8, 1));
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		mockMvc.perform(post(completionEndpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("completed"));

		assertThat(couponCounts(couponId)).containsExactly(8, 0);
		assertThat(couponStatus(couponId)).isEqualTo("expired");
		assertThat(memberGeneralRideCount(memberId)).isEqualTo(3);
	}

	@Test
	void 일회_결제_일반_기승은_쿠폰을_변경하지_않고_완료한다() throws Exception {
		final Long memberId = insertMember("single-payment-completion-member", 0);
		final Long reservationId = insertConfirmedReservation(
			memberId, null, "FIRST_RIDE", "single_payment", LocalDate.of(2026, 8, 2));

		mockMvc.perform(post(completionEndpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentSource").value("single_payment"))
			.andExpect(jsonPath("$.couponId").doesNotExist())
			.andExpect(jsonPath("$.generalRideCount").value(1));

		assertThat(totalUsageLogCount()).isZero();
		assertThat(memberGeneralRideCount(memberId)).isEqualTo(1);
	}

	@Test
	void 반복되고_경쟁하는_완료_요청은_쿠폰과_일반_횟수를_한_번만_반영한다() throws Exception {
		final Long memberId = insertMember("concurrent-completion-member", 4);
		final Long couponId = insertCoupon(memberId, 10, 1, null, null, "active");
		final Long reservationId = insertConfirmedReservation(
			memberId, couponId, "ROUND_TROT", "coupon", LocalDate.of(2026, 8, 3));
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		final List<Integer> statuses = concurrentCompletions(reservationId);

		assertThat(statuses).containsExactlyInAnyOrder(200, 200);
		assertThat(memberGeneralRideCount(memberId)).isEqualTo(5);
		assertThat(couponCounts(couponId)).containsExactly(9, 0);
		assertThat(usageActionCount(reservationId, "used")).isEqualTo(1);
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 확정되지_않은_예약과_없는_예약은_완료할_수_없다() throws Exception {
		final Long memberId = insertMember("invalid-completion-member", 0);
		final Long pendingId = insertPendingPaymentReservation(memberId, LocalDate.of(2026, 8, 4));

		mockMvc.perform(post(completionEndpoint(pendingId)).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_STATUS"));
		mockMvc.perform(post(completionEndpoint(999999L)).with(adminJwt()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void 관리자_외_사용자는_일반_기승을_완료할_수_없다() throws Exception {
		mockMvc.perform(post(completionEndpoint(1L)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(completionEndpoint(1L)).with(memberJwt("member-subject")))
			.andExpect(status().isForbidden());
	}

	@Test
	void 신청일로부터_정확히_삼개월인_수업은_쿠폰으로_예약할_수_있다() throws Exception {
		final String authSubject = "reservation-window-coupon-member";
		final Long memberId = insertMember(authSubject, 0);
		insertCoupon(memberId, 10, 0, null, null, "active");
		final Long timeSlotId = insertTimeSlot(LocalDate.of(2026, 10, 14), "09:00:00");

		mockMvc.perform(post("/api/reservations")
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(reservationRequest(timeSlotId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending_admin_approval"));
	}

	@Test
	void 쿠폰과_일회_결제_모두_신청일로부터_삼개월을_넘는_수업은_예약할_수_없다() throws Exception {
		final String couponSubject = "reservation-window-over-coupon-member";
		final Long couponMemberId = insertMember(couponSubject, 0);
		insertCoupon(couponMemberId, 10, 0, null, null, "active");
		final Long couponTimeSlotId = insertTimeSlot(LocalDate.of(2026, 10, 15), "09:00:00");
		final String paymentSubject = "reservation-window-over-payment-member";
		insertMember(paymentSubject, 0);
		final Long paymentTimeSlotId = insertTimeSlot(LocalDate.of(2026, 10, 15), "10:00:00");

		mockMvc.perform(post("/api/reservations")
				.with(memberJwt(couponSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(reservationRequest(couponTimeSlotId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_LESSON_DATE"));
		mockMvc.perform(post("/api/reservations")
				.with(memberJwt(paymentSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(reservationRequest(paymentTimeSlotId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_LESSON_DATE"));

		assertThat(reservationCount()).isZero();
	}

	private List<Integer> concurrentCompletions(Long reservationId) throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream.range(0, 2)
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return complete(reservationId);
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

	private int complete(Long reservationId) throws Exception {
		return mockMvc.perform(post(completionEndpoint(reservationId)).with(adminJwt()))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private int getResult(Future<Integer> future) {
		try {
			return future.get();
		}
		catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private Long insertMember(String authSubject, int generalRideCount) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, general_ride_count, large_arena_allowed)
			VALUES (?, '완료 회원', '010-0000-0000', ?, FALSE)
			""", authSubject, generalRideCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(
		Long memberId,
		int remainingCount,
		int heldCount,
		String firstUsedAt,
		String expiresAt,
		String status
	) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, status, created_by
			) VALUES (?, 'general', 10, ?, ?, ?, ?, ?, 'completion-test-admin')
			""", memberId, remainingCount, heldCount, firstUsedAt, expiresAt, status);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertConfirmedReservation(
		Long memberId,
		Long couponId,
		String classType,
		String paymentSource,
		LocalDate lessonDate
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				coupon_id, payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, ?, ?, '09:00:00', 'confirmed', ?, ?, ?,
				'2026-07-14 09:00:00', '2026-07-14 09:30:00')
			""",
			memberId,
			classType,
			lessonDate,
			paymentSource,
			couponId,
			"single_payment".equals(paymentSource) ? "2026-07-14 11:00:00" : null);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertPendingPaymentReservation(Long memberId, LocalDate lessonDate) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', ?, '10:00:00', 'pending_payment', 'single_payment',
				'2026-07-14 12:00:00', '2026-07-14 09:00:00')
			""", memberId, lessonDate);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertHeldAndConfirmedLogs(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type
			) VALUES
				(?, ?, ?, 'held', 1, '2026-07-01 09:00:00', 'member'),
				(?, ?, ?, 'confirmed', 0, '2026-07-01 10:00:00', 'admin')
			""", couponId, reservationId, memberId, couponId, reservationId, memberId);
	}

	private Long insertTimeSlot(LocalDate lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity, round_arena_capacity,
				class_capacity_json, is_closed
			) VALUES (?, ?, 8, 4, ?, FALSE)
			""", lessonDate, startTime, CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String reservationRequest(Long timeSlotId) {
		return """
			{
			  "timeSlotId": %d,
			  "classType": "FIRST_RIDE"
			}
			""".formatted(timeSlotId);
	}

	private String completionEndpoint(Long reservationId) {
		return "/api/admin/reservations/%d/complete".formatted(reservationId);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt(String authSubject) {
		return jwt()
			.jwt(token -> token.subject(authSubject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private int memberGeneralRideCount(Long memberId) {
		return jdbcTemplate.queryForObject(
			"SELECT general_ride_count FROM members WHERE id = ?", Integer.class, memberId);
	}

	private List<Integer> couponCounts(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT remaining_count, held_count FROM coupons WHERE id = ?",
			(resultSet, rowNumber) -> List.of(resultSet.getInt(1), resultSet.getInt(2)),
			couponId);
	}

	private String couponDate(Long couponId, String column) {
		return jdbcTemplate.queryForObject(
			"SELECT DATE_FORMAT(%s, '%%Y-%%m-%%d %%H:%%i:%%s') FROM coupons WHERE id = ?"
				.formatted(column),
			String.class,
			couponId);
	}

	private String couponStatus(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM coupons WHERE id = ?", String.class, couponId);
	}

	private int usageActionCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id = ? AND action = ?",
			Integer.class,
			reservationId,
			action);
	}

	private List<String> usedLog(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT count_delta, actor_type FROM coupon_usage_logs WHERE reservation_id = ? AND action = 'used'",
			(resultSet, rowNumber) -> List.of(resultSet.getString(1), resultSet.getString(2)),
			reservationId);
	}

	private long reservationVersion(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT version FROM reservations WHERE id = ?", Long.class, reservationId);
	}

	private int totalUsageLogCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class);
	}

	private int reservationCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM reservation_change_logs");
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
