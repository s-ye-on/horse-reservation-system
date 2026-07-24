package com.horse.timeslots.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.UserRole;
import com.horse.members.domain.RidingClass;
import com.horse.timeslots.application.TimeSlotReservationHistoryQuery;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminTimeSlotApiTest {

	private static final String ENDPOINT = "/api/admin/timeslots";
	private static final String CLASS_CAPACITIES = """
		{
		  "FIRST_RIDE": 2,
		  "ROUND_BEGINNER": 2,
		  "ROUND_TROT": 2,
		  "LARGE_ARENA_BEGINNER": 3,
		  "LARGE_ARENA_TROT": 3,
		  "DRESSAGE": 1,
		  "JUMPING": 1
		}
		""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	TimeSlotReservationHistoryQuery reservationHistoryQuery;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void setUpClock() {
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
		for (LocalDate date = LocalDate.of(2026, 8, 1);
			!date.isAfter(LocalDate.of(2026, 8, 12));
			date = date.plusDays(1)) {
			jdbcTemplate.update("""
				INSERT IGNORE INTO schedule_dates (
					schedule_date, status, applied_config_version
				) VALUES (?, 'NORMAL', 1)
				""", date);
		}
	}

	@Test
	void 비인증과_회원_권한은_관리자_시간대_API에_접근할_수_없다() throws Exception {
		mockMvc.perform(get(ENDPOINT))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get(ENDPOINT).with(memberJwt()))
			.andExpect(status().isForbidden());
	}

	@Test
	void 관리자는_시간대를_생성하고_날짜와_시각순으로_조회한다() throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest("2026-08-06", "10:00:00", 8, 4, CLASS_CAPACITIES)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.lessonDate").value("2026-08-06"))
			.andExpect(jsonPath("$.startTime").value("10:00:00"))
			.andExpect(jsonPath("$.closed").value(false));
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest("2026-08-05", "09:00:00", 8, 4, CLASS_CAPACITIES)))
			.andExpect(status().isCreated());

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].lessonDate").value("2026-08-05"))
			.andExpect(jsonPath("$[1].lessonDate").value("2026-08-06"));
	}

	@Test
	void 기본_조회는_미래_시간대만_과거_조회는_시작된_시간대만_반환한다() throws Exception {
		insertTimeSlot("2026-07-31", "11:00:00");
		insertTimeSlot("2026-08-01", "09:00:00");
		insertTimeSlot("2026-08-01", "10:00:00");
		insertTimeSlot("2026-08-01", "10:00:01");
		insertTimeSlot("2026-08-02", "09:00:00");

		mockMvc.perform(get(ENDPOINT).with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].startTime").value("10:00:01"))
			.andExpect(jsonPath("$[1].lessonDate").value("2026-08-02"));

		mockMvc.perform(get(ENDPOINT + "/history").with(adminJwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].startTime").value("10:00:00"))
			.andExpect(jsonPath("$[1].startTime").value("09:00:00"))
			.andExpect(jsonPath("$[2].lessonDate").value("2026-07-31"));
	}

	@Test
	void 수업_시작_정각부터_시간대_생성과_일반_관리를_차단한다() throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest("2026-08-01", "10:00:00", 8, 4, CLASS_CAPACITIES)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_LESSON_ALREADY_STARTED"));

		final Long timeSlotId = insertTimeSlot("2026-08-01", "10:00:00");
		assertStartedTimeSlotIsRejected(patch(ENDPOINT + "/{timeSlotId}", timeSlotId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"closed\": true}"));
		assertStartedTimeSlotIsRejected(put(ENDPOINT + "/{timeSlotId}/capacity", timeSlotId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(capacityRequest(7, 3, CLASS_CAPACITIES)));
		assertStartedTimeSlotIsRejected(delete(ENDPOINT + "/{timeSlotId}", timeSlotId));

		assertThatTimeSlotCount(timeSlotId, 1);
	}

	@Test
	void 같은_날짜와_시각의_시간대는_중복_생성할_수_없다() throws Exception {
		final String request = createRequest("2026-08-07", "09:00:00", 8, 4, CLASS_CAPACITIES);
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isCreated());

		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_ALREADY_EXISTS"));
	}

	@Test
	void CLOSING과_CLOSED_날짜에는_수동_시간대를_생성할_수_없다() throws Exception {
		final LocalDate lessonDate = LocalDate.of(2026, 8, 6);
		jdbcTemplate.update("""
			UPDATE schedule_dates
			SET status = 'CLOSING', resume_status = 'NORMAL'
			WHERE schedule_date = ?
			""", lessonDate);
		assertManualCreationBlocked(lessonDate);
		jdbcTemplate.update("""
			UPDATE schedule_dates
			SET status = 'CLOSED', resume_status = NULL
			WHERE schedule_date = ?
			""", lessonDate);
		assertManualCreationBlocked(lessonDate);
	}

	@Test
	void 전체와_원형과_클래스_정원_제한을_넘을_수_없다() throws Exception {
		assertInvalidCapacity(
			createRequest("2026-08-08", "09:00:00", 9, 4, CLASS_CAPACITIES),
			"TIMESLOT_INVALID_TOTAL_CAPACITY");
		assertInvalidCapacity(
			createRequest("2026-08-08", "10:00:00", 8, 5, CLASS_CAPACITIES),
			"TIMESLOT_INVALID_ROUND_ARENA_CAPACITY");
		assertInvalidCapacity(
			createRequest(
				"2026-08-08",
				"11:00:00",
				8,
				4,
				CLASS_CAPACITIES.replace("\"JUMPING\": 1", "\"JUMPING\": 9")),
			"TIMESLOT_INVALID_CLASS_CAPACITIES");
	}

	@Test
	void 운영_마감과_재개는_시간대_행을_유지한다() throws Exception {
		final Long timeSlotId = insertTimeSlot("2026-08-09", "09:00:00");

		mockMvc.perform(patch(ENDPOINT + "/{timeSlotId}", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"closed\": true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.closed").value(true));
		mockMvc.perform(patch(ENDPOINT + "/{timeSlotId}", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"closed\": false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.closed").value(false));

		assertThatTimeSlotCount(timeSlotId, 1);
	}

	@Test
	void 전체와_원형과_클래스_정원을_수정한다() throws Exception {
		final Long timeSlotId = insertTimeSlot("2026-08-10", "09:00:00");
		final String updatedClassCapacities = CLASS_CAPACITIES.replace("\"DRESSAGE\": 1", "\"DRESSAGE\": 2");

		mockMvc.perform(put(ENDPOINT + "/{timeSlotId}/capacity", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(capacityRequest(7, 3, updatedClassCapacities)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCapacity").value(7))
			.andExpect(jsonPath("$.roundArenaCapacity").value(3))
			.andExpect(jsonPath("$.classCapacities.DRESSAGE").value(2));
	}

	@Test
	void 활성_예약보다_전체_원형_클래스_정원을_작게_줄일_수_없다() throws Exception {
		final Long timeSlotId = insertTimeSlot("2026-08-10", "10:00:00");
		given(reservationHistoryQuery.findActiveRidingClassesForUpdate(
			java.time.LocalDate.of(2026, 8, 10), java.time.LocalTime.of(10, 0)))
			.willReturn(java.util.List.of(RidingClass.ROUND_BEGINNER, RidingClass.ROUND_BEGINNER));

		assertCapacityBelowOccupancy(timeSlotId, capacityRequest(1, 1, CLASS_CAPACITIES));
		assertCapacityBelowOccupancy(timeSlotId, capacityRequest(8, 1, CLASS_CAPACITIES));
		assertCapacityBelowOccupancy(
			timeSlotId,
			capacityRequest(
				8,
				4,
				CLASS_CAPACITIES.replace("\"ROUND_BEGINNER\": 2", "\"ROUND_BEGINNER\": 1")));
	}

	@Test
	void 예약_이력이_없는_시간대는_물리_삭제한다() throws Exception {
		final Long timeSlotId = insertTimeSlot("2026-08-11", "09:00:00");

		mockMvc.perform(delete(ENDPOINT + "/{timeSlotId}", timeSlotId).with(adminJwt()))
			.andExpect(status().isNoContent());

		assertThatTimeSlotCount(timeSlotId, 0);
	}

	@Test
	void 예약_이력이_있는_시간대는_삭제할_수_없다() throws Exception {
		final Long timeSlotId = insertTimeSlot("2026-08-12", "09:00:00");
		given(reservationHistoryQuery.existsByLessonDateAndStartTime(
			java.time.LocalDate.of(2026, 8, 12), java.time.LocalTime.of(9, 0)))
			.willReturn(true);

		mockMvc.perform(delete(ENDPOINT + "/{timeSlotId}", timeSlotId).with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_RESERVATION_HISTORY_EXISTS"));

		assertThatTimeSlotCount(timeSlotId, 1);
	}

	@Test
	void 존재하지_않는_시간대는_변경할_수_없다() throws Exception {
		mockMvc.perform(patch(ENDPOINT + "/{timeSlotId}", 999_999L)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"closed\": true}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("TIMESLOT_NOT_FOUND"));
	}

	private void assertInvalidCapacity(String request, String exceptionCode) throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(exceptionCode));
	}

	private void assertManualCreationBlocked(LocalDate lessonDate) throws Exception {
		mockMvc.perform(post(ENDPOINT)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createRequest(
					lessonDate.toString(),
					"13:30:00",
					8,
					4,
					CLASS_CAPACITIES)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SCHEDULE_DATE_NOT_RESERVABLE"));
	}

	private void assertCapacityBelowOccupancy(Long timeSlotId, String request) throws Exception {
		mockMvc.perform(put(ENDPOINT + "/{timeSlotId}/capacity", timeSlotId)
				.with(adminJwt())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_CAPACITY_BELOW_OCCUPANCY"));
	}

	private void assertStartedTimeSlotIsRejected(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
	) throws Exception {
		mockMvc.perform(request.with(adminJwt()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TIMESLOT_LESSON_ALREADY_STARTED"));
	}

	private void assertThatTimeSlotCount(Long timeSlotId, int expectedCount) {
		final Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM time_slot_capacities WHERE id = ?",
			Integer.class,
			timeSlotId);
		org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expectedCount);
	}

	private Long insertTimeSlot(String lessonDate, String startTime) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date,
				start_time,
				total_capacity,
				round_arena_capacity,
				class_capacity_json
			) VALUES (?, ?, 8, 4, ?)
			""", lessonDate, startTime, CLASS_CAPACITIES);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM time_slot_capacities WHERE lesson_date = ? AND start_time = ?",
			Long.class,
			lessonDate,
			startTime);
	}

	private String createRequest(
		String lessonDate,
		String startTime,
		int totalCapacity,
		int roundArenaCapacity,
		String classCapacities
	) {
		return """
			{
			  "lessonDate": "%s",
			  "startTime": "%s",
			  "totalCapacity": %d,
			  "roundArenaCapacity": %d,
			  "classCapacities": %s
			}
			""".formatted(lessonDate, startTime, totalCapacity, roundArenaCapacity, classCapacities);
	}

	private String capacityRequest(
		int totalCapacity,
		int roundArenaCapacity,
		String classCapacities
	) {
		return """
			{
			  "totalCapacity": %d,
			  "roundArenaCapacity": %d,
			  "classCapacities": %s
			}
			""".formatted(totalCapacity, roundArenaCapacity, classCapacities);
	}

	private RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()));
	}

	private RequestPostProcessor memberJwt() {
		return jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));
	}

}
