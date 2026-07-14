package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class CouponReservationApplicationApiTest {

	private static final String ENDPOINT = "/api/reservations";
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
	void 쿠폰_예약은_승인대기_예약과_쿠폰_임시_점유를_함께_생성한다() throws Exception {
		final String authSubject = "coupon-application-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId, "general", 10, 0);
		final LocalDate lessonDate = futureDate();
		final Long timeSlotId = insertTimeSlot(lessonDate, "09:00:00", 8, 4);

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending_admin_approval"))
			.andExpect(jsonPath("$.paymentSource").value("coupon"))
			.andExpect(jsonPath("$.coupon.couponId").value(couponId))
			.andExpect(jsonPath("$.coupon.remainingCount").value(10))
			.andExpect(jsonPath("$.coupon.heldCount").value(1))
			.andExpect(jsonPath("$.coupon.availableCount").value(9))
			.andExpect(jsonPath("$.paymentDueAt").doesNotExist());

		assertThat(reservationCount()).isEqualTo(1);
		assertThat(couponHeldCount(couponId)).isEqualTo(1);
		assertThat(usageActionCount(couponId, "held")).isEqualTo(1);
	}

	@Test
	void 회원에게_허용되지_않은_클래스는_예약할_수_없다() throws Exception {
		final String authSubject = "ineligible-reservation-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId, "dressage", 10, 0);
		final Long timeSlotId = insertTimeSlot(futureDate(), "10:00:00", 8, 4);

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "DRESSAGE")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_RIDING_CLASS"));

		assertThat(reservationCount()).isZero();
		assertThat(couponHeldCount(couponId)).isZero();
	}

	@Test
	void 동일_시간대의_경쟁_예약은_정원을_초과하지_않는다() throws Exception {
		final String firstSubject = "capacity-first-member";
		final String secondSubject = "capacity-second-member";
		final Long firstMemberId = insertMember(firstSubject);
		final Long secondMemberId = insertMember(secondSubject);
		insertCoupon(firstMemberId, "general", 10, 0);
		insertCoupon(secondMemberId, "general", 10, 0);
		final Long timeSlotId = insertTimeSlot(futureDate(), "11:00:00", 1, 1);

		final List<Integer> statuses = concurrentApplications(
			List.of(firstSubject, secondSubject),
			List.of(timeSlotId, timeSlotId));

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		assertThat(reservationCount()).isEqualTo(1);
		assertThat(totalHeldCount()).isEqualTo(1);
	}

	@Test
	void 잔여_한_회_쿠폰의_경쟁_예약은_하나만_남기고_실패한_예약을_롤백한다() throws Exception {
		final String authSubject = "single-coupon-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId, "general", 1, 0);
		final LocalDate lessonDate = futureDate();
		final Long firstTimeSlotId = insertTimeSlot(lessonDate, "12:00:00", 8, 4);
		final Long secondTimeSlotId = insertTimeSlot(lessonDate, "13:00:00", 8, 4);

		final List<Integer> statuses = concurrentApplications(
			List.of(authSubject, authSubject),
			List.of(firstTimeSlotId, secondTimeSlotId));

		assertThat(statuses).containsExactlyInAnyOrder(201, 404);
		assertThat(reservationCount()).isEqualTo(1);
		assertThat(couponHeldCount(couponId)).isEqualTo(1);
		assertThat(usageActionCount(couponId, "held")).isEqualTo(1);
	}

	@Test
	void 예약_이력이_생긴_시간대는_물리_삭제할_수_없다() throws Exception {
		final String authSubject = "history-guard-member";
		final Long memberId = insertMember(authSubject);
		insertCoupon(memberId, "general", 10, 0);
		final Long timeSlotId = insertTimeSlot(futureDate(), "14:00:00", 8, 4);
		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/admin/timeslots/{id}", timeSlotId).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_RESERVATION_HISTORY_EXISTS"));

		assertThat(timeSlotCount(timeSlotId)).isEqualTo(1);
	}

	@Test
	void 인증되지_않은_예약_신청은_거부한다() throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(1L, "FIRST_RIDE")))
			.andExpect(status().isUnauthorized());
	}

	private List<Integer> concurrentApplications(List<String> authSubjects, List<Long> timeSlotIds)
		throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(authSubjects.size());
		final CountDownLatch ready = new CountDownLatch(authSubjects.size());
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = java.util.stream.IntStream
				.range(0, authSubjects.size())
				.mapToObj(index -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return apply(authSubjects.get(index), timeSlotIds.get(index));
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

	private int apply(String authSubject, Long timeSlotId) throws Exception {
		return mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
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

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '예약 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, String couponType, int remainingCount, int heldCount) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count, created_by
			) VALUES (?, ?, 10, ?, ?, 'reservation-test-admin')
			""", memberId, couponType, remainingCount, heldCount);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot(
		LocalDate lessonDate,
		String startTime,
		int totalCapacity,
		int roundArenaCapacity
	) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity, round_arena_capacity,
				class_capacity_json, is_closed
			) VALUES (?, ?, ?, ?, ?, FALSE)
			""", lessonDate, startTime, totalCapacity, roundArenaCapacity, CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String request(Long timeSlotId, String classType) {
		return """
			{
			  "timeSlotId": %d,
			  "classType": "%s"
			}
			""".formatted(timeSlotId, classType);
	}

	private RequestPostProcessor memberJwt(String authSubject) {
		return jwt()
			.jwt(token -> token.subject(authSubject))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private int reservationCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
	}

	private int couponHeldCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?", Integer.class, couponId);
	}

	private int totalHeldCount() {
		return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(held_count), 0) FROM coupons", Integer.class);
	}

	private int usageActionCount(Long couponId, String action) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_usage_logs WHERE coupon_id = ? AND action = ?",
			Integer.class,
			couponId,
			action);
	}

	private int timeSlotCount(Long timeSlotId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM time_slot_capacities WHERE id = ?", Integer.class, timeSlotId);
	}

	private LocalDate futureDate() {
		return LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(7);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
