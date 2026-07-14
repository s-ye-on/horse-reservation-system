package com.horse.reservations.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

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
class AdminPendingPaymentQueryApiTest {

	private static final String ENDPOINT = "/api/admin/pending-payments";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant QUERY_INSTANT = Instant.parse("2026-07-15T01:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 데이터베이스와_조회_시각을_초기화한다() {
		clearDatabase();
		when(clock.instant()).thenReturn(QUERY_INSTANT);
		when(clock.getZone()).thenReturn(SEOUL_ZONE);
	}

	@AfterEach
	void 생성한_데이터를_정리한다() {
		clearDatabase();
	}

	@Test
	void 입금대기와_만료_예약을_마감_시각순으로_조회한다() throws Exception {
		final Long memberId = insertMember("pending-query-member");
		final Long laterId = insertReservation(memberId, "pending_payment", "2026-07-15 11:00:00");
		final Long expiredId = insertReservation(memberId, "payment_expired", "2026-07-15 09:00:00");
		final Long boundaryId = insertReservation(memberId, "pending_payment", "2026-07-15 10:00:00");
		insertConfirmedReservation(memberId);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].reservationId").value(expiredId))
			.andExpect(jsonPath("$[0].status").value("payment_expired"))
			.andExpect(jsonPath("$[0].deadlineExceeded").value(true))
			.andExpect(jsonPath("$[1].reservationId").value(boundaryId))
			.andExpect(jsonPath("$[1].deadlineExceeded").value(true))
			.andExpect(jsonPath("$[2].reservationId").value(laterId))
			.andExpect(jsonPath("$[2].deadlineExceeded").value(false))
			.andExpect(jsonPath("$[2].memberName").value("입금 조회 회원"))
			.andExpect(jsonPath("$[2].memberPhone").value("010-1234-0000"));
	}

	@Test
	void 조회는_예약_상태와_버전을_변경하지_않는다() throws Exception {
		final Long memberId = insertMember("readonly-payment-query-member");
		final Long reservationId = insertReservation(
			memberId, "pending_payment", "2026-07-15 09:00:00");
		final String before = reservationState(reservationId);

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].deadlineExceeded").value(true));

		assertThat(reservationState(reservationId)).isEqualTo(before);
	}

	@Test
	void 관리자만_입금대기_목록을_조회할_수_있다() throws Exception {
		mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt())).andExpect(status().isForbidden());
		mockMvc.perform(get(ENDPOINT).with(adminJwt())).andExpect(status().isOk());
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '입금 조회 회원', '010-1234-0000', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertReservation(Long memberId, String status, String paymentDueAt) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '09:00:00', ?, 'single_payment',
				?, '2026-07-15 08:00:00')
			""", memberId, status, paymentDueAt);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertConfirmedReservation(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-01', '10:00:00', 'confirmed',
				'single_payment', '2026-07-15 09:00:00', '2026-07-15 08:00:00',
				'2026-07-15 08:30:00')
			""", memberId);
	}

	private String reservationState(Long reservationId) {
		return jdbcTemplate.queryForObject("""
			SELECT CONCAT(status, ':', version, ':', updated_at)
			FROM reservations
			WHERE id = ?
			""", String.class, reservationId);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
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
