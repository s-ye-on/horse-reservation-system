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
class AdminReservationSummaryApiTest {

	private static final String ENDPOINT = "/api/admin/reservations/summary";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Instant QUERY_INSTANT = Instant.parse("2026-07-17T01:00:00Z");

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
	void 날짜_범위의_예약을_전체_상태별_일자별로_집계한다() throws Exception {
		final Long memberId = insertMember("summary-range-member");
		insertReservation(memberId, "2026-07-17", "09:00:00", "pending_payment");
		insertReservation(memberId, "2026-07-17", "10:00:00", "pending_payment");
		insertReservation(memberId, "2026-07-17", "11:00:00", "confirmed");
		insertReservation(memberId, "2026-07-18", "09:00:00", "completed");
		insertReservation(memberId, "2026-07-19", "09:00:00", "confirmed");

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("lessonDateFrom", "2026-07-17")
				.param("lessonDateTo", "2026-07-18"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lessonDateFrom").value("2026-07-17"))
			.andExpect(jsonPath("$.lessonDateTo").value("2026-07-18"))
			.andExpect(jsonPath("$.totalCount").value(4))
			.andExpect(jsonPath("$.statusCounts.length()").value(8))
			.andExpect(jsonPath("$.statusCounts[0].status").value("pending_admin_approval"))
			.andExpect(jsonPath("$.statusCounts[0].count").value(0))
			.andExpect(jsonPath("$.statusCounts[1].status").value("pending_payment"))
			.andExpect(jsonPath("$.statusCounts[1].count").value(2))
			.andExpect(jsonPath("$.statusCounts[3].status").value("confirmed"))
			.andExpect(jsonPath("$.statusCounts[3].count").value(1))
			.andExpect(jsonPath("$.statusCounts[4].status").value("completed"))
			.andExpect(jsonPath("$.statusCounts[4].count").value(1))
			.andExpect(jsonPath("$.dailyCounts.length()").value(2))
			.andExpect(jsonPath("$.dailyCounts[0].lessonDate").value("2026-07-17"))
			.andExpect(jsonPath("$.dailyCounts[0].totalCount").value(3))
			.andExpect(jsonPath("$.dailyCounts[1].lessonDate").value("2026-07-18"))
			.andExpect(jsonPath("$.dailyCounts[1].totalCount").value(1));
	}

	@Test
	void 날짜를_생략하거나_한쪽만_지정하면_하루를_집계한다() throws Exception {
		final Long memberId = insertMember("summary-default-member");
		insertReservation(memberId, "2026-07-17", "09:00:00", "pending_payment");
		insertReservation(memberId, "2026-07-18", "09:00:00", "pending_payment");

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lessonDateFrom").value("2026-07-17"))
			.andExpect(jsonPath("$.lessonDateTo").value("2026-07-17"))
			.andExpect(jsonPath("$.totalCount").value(1));

		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("lessonDateTo", "2026-07-18"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lessonDateFrom").value("2026-07-18"))
			.andExpect(jsonPath("$.lessonDateTo").value("2026-07-18"))
			.andExpect(jsonPath("$.totalCount").value(1));
	}

	@Test
	void 잘못된_날짜_범위와_권한을_거부하고_조회는_데이터를_변경하지_않는다() throws Exception {
		final Long memberId = insertMember("summary-readonly-member");
		insertReservation(memberId, "2026-07-17", "09:00:00", "pending_payment");
		final Integer beforeCount = reservationCount();

		mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt())).andExpect(status().isForbidden());
		mockMvc.perform(get(ENDPOINT)
				.with(adminJwt())
				.param("lessonDateFrom", "2026-07-18")
				.param("lessonDateTo", "2026-07-17"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("RESERVATION_INVALID_QUERY_DATE_RANGE"));
		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk());

		assertThat(reservationCount()).isEqualTo(beforeCount);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES (?, '집계 회원', '010-1111-2222', FALSE)
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertReservation(
		Long memberId,
		String lessonDate,
		String startTime,
		String reservationStatus
	) {
		final String confirmedAt = switch (reservationStatus) {
			case "confirmed", "completed", "no_show" -> "2026-07-16 10:00:00";
			default -> null;
		};
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (?, 'ROUND_BEGINNER', ?, ?, ?, 'single_payment',
				'2026-07-16 12:00:00', '2026-07-16 09:00:00', ?)
			""", memberId, lessonDate, startTime, reservationStatus, confirmedAt);
	}

	private Integer reservationCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class);
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
