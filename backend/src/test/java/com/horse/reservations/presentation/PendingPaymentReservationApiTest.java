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
class PendingPaymentReservationApiTest {

	private static final String ENDPOINT = "/api/reservations";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant REQUEST_INSTANT = Instant.parse("2026-07-14T01:00:00Z");
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);
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
	void 데이터베이스와_기준_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(REQUEST_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 유효한_쿠폰이_없으면_두_시간_입금대기_예약을_생성한다() throws Exception {
		final String authSubject = "pending-payment-member";
		insertMember(authSubject);
		final Long timeSlotId = insertTimeSlot("09:00:00", 8, 4);

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending_payment"))
			.andExpect(jsonPath("$.paymentSource").value("single_payment"))
			.andExpect(jsonPath("$.coupon").doesNotExist())
			.andExpect(jsonPath("$.paymentDueAt").value("2026-07-14T12:00:00"));

		assertThat(reservationCount()).isEqualTo(1);
		assertThat(paymentDueAt()).isEqualTo("2026-07-14 12:00:00");
	}

	@Test
	void 유효한_클래스_일치_쿠폰이_있으면_입금대기로_신청하지_않는다() throws Exception {
		final String authSubject = "coupon-priority-member";
		final Long memberId = insertMember(authSubject);
		final Long couponId = insertCoupon(memberId, "general", null);
		final Long timeSlotId = insertTimeSlot("10:00:00", 8, 4);

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending_admin_approval"))
			.andExpect(jsonPath("$.paymentSource").value("coupon"))
			.andExpect(jsonPath("$.coupon.couponId").value(couponId))
			.andExpect(jsonPath("$.paymentDueAt").doesNotExist());

		assertThat(couponHeldCount(couponId)).isEqualTo(1);
		assertThat(paymentReservationCount()).isZero();
	}

	@Test
	void 수업일에_만료되거나_클래스가_다른_쿠폰은_입금대기를_막지_않는다() throws Exception {
		final String authSubject = "invalid-coupon-member";
		final Long memberId = insertMember(authSubject);
		insertCoupon(memberId, "general", "2026-07-31 23:59:59");
		insertCoupon(memberId, "jumping", null);
		final Long timeSlotId = insertTimeSlot("11:00:00", 8, 4);

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending_payment"))
			.andExpect(jsonPath("$.paymentSource").value("single_payment"));

		assertThat(totalHeldCount()).isZero();
	}

	@Test
	void 입금대기_경쟁_예약도_시간대_정원을_초과하지_않는다() throws Exception {
		final String firstSubject = "pending-capacity-first";
		final String secondSubject = "pending-capacity-second";
		insertMember(firstSubject);
		insertMember(secondSubject);
		final Long timeSlotId = insertTimeSlot("12:00:00", 1, 1);

		final List<Integer> statuses = concurrentApplications(
			List.of(firstSubject, secondSubject),
			timeSlotId);

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		assertThat(reservationCount()).isEqualTo(1);
		assertThat(paymentReservationCount()).isEqualTo(1);
	}

	@Test
	void 입금대기_예약도_권한과_클래스_입력을_검증한다() throws Exception {
		final String authSubject = "pending-validation-member";
		insertMember(authSubject);
		final Long timeSlotId = insertTimeSlot("13:00:00", 8, 4);

		mockMvc.perform(post(ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "UNKNOWN")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_RIDING_CLASS"));

		assertThat(reservationCount()).isZero();
	}

	@Test
	void 당일_수업_시작_직전까지_회원_예약을_허용한다() throws Exception {
		final String authSubject = "same-day-booking-member";
		insertMember(authSubject);
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "09:00:00", 8, 4);
		when(clock.instant()).thenReturn(Instant.parse("2026-07-31T23:59:59.999999999Z"));

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("pending_payment"))
			.andExpect(jsonPath("$.paymentDueAt").value("2026-08-01T09:00:00"));

		assertThat(reservationCount()).isEqualTo(1);
		assertThat(paymentDueAt()).isEqualTo("2026-08-01 09:00:00");
	}

	@Test
	void 정확한_수업_시작_시각부터_직접_예약_신청을_거부한다() throws Exception {
		final String authSubject = "started-lesson-member";
		insertMember(authSubject);
		final Long timeSlotId = insertTimeSlot(LESSON_DATE, "09:00:00", 8, 4);
		when(clock.instant()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));

		mockMvc.perform(post(ENDPOINT)
				.with(memberJwt(authSubject))
				.contentType(MediaType.APPLICATION_JSON)
				.content(request(timeSlotId, "FIRST_RIDE")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESERVATION_LESSON_ALREADY_STARTED"));

		assertThat(reservationCount()).isZero();
	}

	private List<Integer> concurrentApplications(List<String> authSubjects, Long timeSlotId)
		throws Exception {
		final ExecutorService executor = Executors.newFixedThreadPool(authSubjects.size());
		final CountDownLatch ready = new CountDownLatch(authSubjects.size());
		final CountDownLatch start = new CountDownLatch(1);
		try {
			final List<Future<Integer>> futures = authSubjects.stream()
				.map(authSubject -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return apply(authSubject, timeSlotId);
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
			VALUES (?, '입금대기 회원', '010-0000-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertCoupon(Long memberId, String couponType, String expiresAt) {
		if (expiresAt == null) {
			jdbcTemplate.update("""
				INSERT INTO coupons (
					member_id, coupon_type, total_count, remaining_count, held_count, created_by
				) VALUES (?, ?, 10, 10, 0, 'pending-test-admin')
				""", memberId, couponType);
		}
		else {
			jdbcTemplate.update("""
				INSERT INTO coupons (
					member_id, coupon_type, total_count, remaining_count, held_count,
					first_used_at, expires_at, created_by
				) VALUES (?, ?, 10, 10, 0, '2026-04-30 10:00:00', ?, 'pending-test-admin')
				""", memberId, couponType, expiresAt);
		}
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertTimeSlot(String startTime, int totalCapacity, int roundArenaCapacity) {
		return insertTimeSlot(LESSON_DATE, startTime, totalCapacity, roundArenaCapacity);
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
				class_capacity_json, admin_closed
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

	private int reservationCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
	}

	private int paymentReservationCount() {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservations WHERE payment_source = 'single_payment'",
			Integer.class);
	}

	private String paymentDueAt() {
		return jdbcTemplate.queryForObject(
			"SELECT DATE_FORMAT(payment_due_at, '%Y-%m-%d %H:%i:%s') FROM reservations",
			String.class);
	}

	private int couponHeldCount(Long couponId) {
		return jdbcTemplate.queryForObject(
			"SELECT held_count FROM coupons WHERE id = ?", Integer.class, couponId);
	}

	private int totalHeldCount() {
		return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(held_count), 0) FROM coupons", Integer.class);
	}

	private void clearDatabase() {
		jdbcTemplate.update("DELETE FROM coupon_usage_logs");
		jdbcTemplate.update("DELETE FROM reservations");
		jdbcTemplate.update("DELETE FROM time_slot_capacities");
		jdbcTemplate.update("DELETE FROM coupons");
		jdbcTemplate.update("DELETE FROM members");
	}
}
