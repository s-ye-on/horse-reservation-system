package com.horse.schedules.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.jayway.jsonpath.JsonPath;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminScheduleConfigurationApiTest {

	private static final String TEMPLATE_ENDPOINT = "/api/admin/schedule-templates";
	private static final String HOLIDAY_ENDPOINT = "/api/admin/recurring-holidays";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void 설정_초기화() {
		given(clock.instant()).willReturn(Instant.parse("2026-08-01T01:00:00Z"));
		given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = 1,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""");
		insertScheduleDate(LocalDate.of(2026, 8, 4));
	}

	@Test
	void 비인증과_회원은_일정_설정_API를_호출할_수_없다() throws Exception {
		mockMvc.perform(get(TEMPLATE_ENDPOINT))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get(TEMPLATE_ENDPOINT).with(memberJwt()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get(HOLIDAY_ENDPOINT).with(memberJwt()))
			.andExpect(status().isForbidden());
	}

	@Test
	void Template_영향을_미리보고_생성한_뒤_SYNCING_상태를_반환한다() throws Exception {
		mockMvc.perform(post(TEMPLATE_ENDPOINT + "/preview")
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "dayOfWeek": "TUESDAY",
					  "startTime": "12:00:00"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.affectedDateCount").isNumber())
			.andExpect(jsonPath("$.activeReservationCount").isNumber());

		final MvcResult created = mockMvc.perform(post(TEMPLATE_ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(templateRequest(1)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.template.dayOfWeek").value("TUESDAY"))
			.andExpect(jsonPath("$.template.endTime").value("12:45:00"))
			.andExpect(jsonPath("$.pendingConfigVersion").value(2))
			.andExpect(jsonPath("$.synchronization.status").value("SYNCING"))
			.andReturn();
		final Number templateId = JsonPath.read(
			created.getResponse().getContentAsString(),
			"$.template.templateId");

		mockMvc.perform(patch(TEMPLATE_ENDPOINT + "/{templateId}/activation", templateId.longValue())
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "active": false,
					  "expectedConfigVersion": 1,
					  "reason": "중복 요청"
					}
					"""))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SCHEDULE_CONFIG_SYNC_IN_PROGRESS"))
			.andExpect(jsonPath("$.details").isMap());
	}

	@Test
	void 정기_휴일은_영향_미리보기와_overlap_오류를_제공한다() throws Exception {
		mockMvc.perform(post(HOLIDAY_ENDPOINT + "/preview")
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(holidayPreviewRequest(null)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.combined.affectedDateCount").isNumber());

		mockMvc.perform(post(HOLIDAY_ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(holidayRequest(1, "2026-08-04", "2026-09-01")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.holiday.dayOfWeek").value("TUESDAY"))
			.andExpect(jsonPath("$.pendingConfigVersion").value(2));

		resetConfigGuard(2);
		mockMvc.perform(post(HOLIDAY_ENDPOINT + "/preview")
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(holidayPreviewRequest(null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULE_RECURRING_HOLIDAY_OVERLAP"));
	}

	@Test
	void 오래된_설정_version은_409로_거부한다() throws Exception {
		resetConfigGuard(3);

		mockMvc.perform(post(TEMPLATE_ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(templateRequest(2)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULE_CONFIG_VERSION_CONFLICT"));
	}

	private void insertScheduleDate(LocalDate date) {
		jdbcTemplate.update("""
			INSERT IGNORE INTO schedule_dates (
				schedule_date, status, applied_config_version
			) VALUES (?, 'NORMAL', 1)
			""", date);
	}

	private void resetConfigGuard(long activeVersion) {
		jdbcTemplate.update("""
			UPDATE schedule_config_guard
			SET status = 'ACTIVE',
				active_version = ?,
				pending_version = NULL,
				sync_started_at = NULL,
				sync_started_by = NULL
			WHERE id = 1
			""", activeVersion);
	}

	private String templateRequest(long expectedVersion) {
		return """
			{
			  "dayOfWeek": "TUESDAY",
			  "startTime": "12:00:00",
			  "endTime": "12:45:00",
			  "totalCapacity": 8,
			  "roundArenaCapacity": 4,
			  "classCapacities": {
			    "FIRST_RIDE": 2,
			    "ROUND_BEGINNER": 2,
			    "ROUND_TROT": 2,
			    "LARGE_ARENA_BEGINNER": 3,
			    "LARGE_ARENA_TROT": 3,
			    "DRESSAGE": 1,
			    "JUMPING": 1
			  },
			  "expectedConfigVersion": %d,
			  "reason": "운영 시간표 추가"
			}
			""".formatted(expectedVersion);
	}

	private String holidayPreviewRequest(Long holidayId) {
		final String id = holidayId == null ? "null" : holidayId.toString();
		return """
			{
			  "holidayId": %s,
			  "dayOfWeek": "TUESDAY",
			  "effectiveFrom": "2026-08-04",
			  "effectiveTo": "2026-09-01"
			}
			""".formatted(id);
	}

	private String holidayRequest(
		long expectedVersion,
		String effectiveFrom,
		String effectiveTo
	) {
		return """
			{
			  "dayOfWeek": "TUESDAY",
			  "effectiveFrom": "%s",
			  "effectiveTo": "%s",
			  "holidayReason": "정기 휴무",
			  "expectedConfigVersion": %d,
			  "changeReason": "운영 정책 반영"
			}
			""".formatted(effectiveFrom, effectiveTo, expectedVersion);
	}

	private RequestPostProcessor adminJwt() {
		return jwt()
			.jwt(token -> token.subject("admin-r11"))
			.authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt()
			.jwt(token -> token.subject("member-r11"))
			.authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}
}
