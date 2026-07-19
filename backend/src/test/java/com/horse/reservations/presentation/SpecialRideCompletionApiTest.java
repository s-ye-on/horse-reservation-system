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
class SpecialRideCompletionApiTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant COMPLETED_INSTANT = Instant.parse("2026-08-09T01:00:00Z");

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
	void 마장마술_쿠폰_기승은_마장마술_횟수와_쿠폰만_반영한다() throws Exception {
		final Long memberId = insertMember("dressage-completion-member", 20, 2, 3);
		final Long couponId = insertCoupon(memberId, "dressage", 10, 1);
		final Long reservationId = insertConfirmedReservation(
			memberId, couponId, "DRESSAGE", "coupon", LocalDate.of(2026, 8, 5));
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("completed"))
			.andExpect(jsonPath("$.generalRideCount").value(20))
			.andExpect(jsonPath("$.dressageRideCount").value(3))
			.andExpect(jsonPath("$.jumpingRideCount").value(3));

		assertThat(memberRideCounts(memberId)).containsExactly(20, 3, 3);
		assertThat(couponCounts(couponId)).containsExactly(9, 0);
		assertThat(couponDates(couponId)).containsExactly(
			"2026-08-05 00:00:00", "2026-11-05 00:00:00");
		assertThat(usageActionCount(reservationId, "used")).isEqualTo(1);
	}

	@Test
	void 장애물_일회_결제_기승은_장애물_횟수만_반영한다() throws Exception {
		final Long memberId = insertMember("jumping-completion-member", 25, 4, 1);
		final Long reservationId = insertConfirmedReservation(
			memberId, null, "JUMPING", "single_payment", LocalDate.of(2026, 8, 6));

		mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generalRideCount").value(25))
			.andExpect(jsonPath("$.dressageRideCount").value(4))
			.andExpect(jsonPath("$.jumpingRideCount").value(2));

		assertThat(memberRideCounts(memberId)).containsExactly(25, 4, 2);
		assertThat(totalUsageLogCount()).isZero();
	}

	@Test
	void 반복되고_경쟁하는_특수_완료_요청은_이력과_쿠폰을_한_번만_반영한다() throws Exception {
		final Long memberId = insertMember("concurrent-special-member", 30, 0, 0);
		final Long couponId = insertCoupon(memberId, "jumping", 10, 1);
		final Long reservationId = insertConfirmedReservation(
			memberId, couponId, "JUMPING", "coupon", LocalDate.of(2026, 8, 7));
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		final List<Integer> statuses = concurrentCompletions(reservationId);

		assertThat(statuses).containsExactlyInAnyOrder(200, 200);
		assertThat(memberRideCounts(memberId)).containsExactly(30, 0, 1);
		assertThat(couponCounts(couponId)).containsExactly(9, 0);
		assertThat(usageActionCount(reservationId, "used")).isEqualTo(1);
		assertThat(reservationVersion(reservationId)).isEqualTo(1L);
	}

	@Test
	void 예약_클래스와_쿠폰_종류가_다르면_아무_완료_결과도_반영하지_않는다() throws Exception {
		final Long memberId = insertMember("mismatched-special-member", 10, 0, 0);
		final Long couponId = insertCoupon(memberId, "general", 10, 1);
		final Long reservationId = insertConfirmedReservation(
			memberId, couponId, "DRESSAGE", "coupon", LocalDate.of(2026, 8, 8));
		insertHeldAndConfirmedLogs(memberId, couponId, reservationId);

		mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("COUPON_HOLD_STATE_CONFLICT"));

		assertThat(reservationStatus(reservationId)).isEqualTo("confirmed");
		assertThat(memberRideCounts(memberId)).containsExactly(10, 0, 0);
		assertThat(couponCounts(couponId)).containsExactly(10, 1);
		assertThat(usageActionCount(reservationId, "used")).isZero();
	}

	@Test
	void 관리자_외_사용자는_특수_기승을_완료할_수_없다() throws Exception {
		mockMvc.perform(post(endpoint(1L)).with(memberJwt()))
			.andExpect(status().isForbidden());
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
		return mockMvc.perform(post(endpoint(reservationId)).with(adminJwt()))
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

	private Long insertMember(
		String authSubject,
		int generalRideCount,
		int dressageRideCount,
		int jumpingRideCount
	) {
		jdbcTemplate.update("""
			INSERT INTO members (
				auth_subject, name, phone, general_ride_count, dressage_ride_count,
				jumping_ride_count, large_arena_allowed
			) VALUES (?, '특수 완료 회원', '010-0000-0000', ?, ?, ?, FALSE)
			""", authSubject, generalRideCount, dressageRideCount, jumpingRideCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, String couponType, int remainingCount, int heldCount) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, ?, 10, ?, ?, 'special-completion-test-admin')
			""", memberId, couponType, remainingCount, heldCount);
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

	private void insertHeldAndConfirmedLogs(Long memberId, Long couponId, Long reservationId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type
			) VALUES
				(?, ?, ?, 'held', 1, '2026-07-01 09:00:00', 'member'),
				(?, ?, ?, 'confirmed', 0, '2026-07-01 10:00:00', 'admin')
			""", couponId, reservationId, memberId, couponId, reservationId, memberId);
	}

	private String endpoint(Long reservationId) {
		return "/api/admin/reservations/%d/complete".formatted(reservationId);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private List<Integer> memberRideCounts(Long memberId) {
		return jdbcTemplate.queryForObject("""
			SELECT general_ride_count, dressage_ride_count, jumping_ride_count
			FROM members WHERE id = ?
			""",
			(resultSet, rowNumber) -> List.of(
				resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3)),
			memberId);
	}

	private List<Integer> couponCounts(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT remaining_count, held_count FROM coupons WHERE id = ?",
			(resultSet, rowNumber) -> List.of(resultSet.getInt(1), resultSet.getInt(2)),
			couponId);
	}

	private List<String> couponDates(Long couponId) {
		return jdbcTemplate.queryForObject("""
			SELECT DATE_FORMAT(first_used_at, '%Y-%m-%d %H:%i:%s'),
			       DATE_FORMAT(expires_at, '%Y-%m-%d %H:%i:%s')
			FROM coupons WHERE id = ?
			""",
			(resultSet, rowNumber) -> List.of(resultSet.getString(1), resultSet.getString(2)),
			couponId);
	}

	private String reservationStatus(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT status FROM reservations WHERE id = ?", String.class, reservationId);
	}

	private int usageActionCount(Long reservationId, String action) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id = ? AND action = ?",
			Integer.class,
			reservationId,
			action);
	}

	private long reservationVersion(Long reservationId) {
		return jdbcTemplate.queryForObject(
			"SELECT version FROM reservations WHERE id = ?", Long.class, reservationId);
	}

	private int totalUsageLogCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupon_usage_logs", Integer.class);
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
