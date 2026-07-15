package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ReservationChangeApiTest {

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

	@BeforeEach
	void 데이터베이스를_초기화한다() {
		clearDatabase();
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
		final Long couponId = insertCoupon(memberId, lessonDate.plusMonths(1));
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
			targetTimeSlotId);

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(activeOccupancy(lessonDate, "11:00:00")).isEqualTo(1);
		assertThat(changeLogCount(firstReservationId) + changeLogCount(secondReservationId)).isEqualTo(1);
	}

	private List<Integer> concurrentChanges(
		List<String> authSubjects,
		List<Long> reservationIds,
		Long targetTimeSlotId
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
							.content(memberRequest(targetTimeSlotId, null)))
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

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '변경 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, LocalDate expiresAt) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count,
				first_used_at, expires_at, created_by
			) VALUES (?, 'general', 10, 10, 1, ?, ?, 'change-test-admin')
			""", memberId, futureDate().atStartOfDay(), expiresAt.atStartOfDay());
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

	private String changeLogMemo(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT memo FROM reservation_change_logs WHERE reservation_id = ?",
			String.class,
			reservationId);
	}

	private int activeOccupancy(LocalDate lessonDate, String startTime) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM reservations
			WHERE lesson_date = ? AND start_time = ?
			  AND status IN ('pending_admin_approval', 'pending_payment', 'confirmed')
			""", Integer.class, lessonDate, startTime);
	}

	private LocalDate futureDate() {
		return LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(7);
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
