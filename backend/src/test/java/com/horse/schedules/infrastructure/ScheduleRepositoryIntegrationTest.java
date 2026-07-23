package com.horse.schedules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.members.domain.Member;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.schedules.domain.RecurringHolidayRule;
import com.horse.schedules.domain.RegularScheduleTemplate;
import com.horse.schedules.domain.ReservationMemberDayGuard;
import com.horse.schedules.domain.ReservationMemberDayGuardId;
import com.horse.schedules.domain.ScheduleAuditLog;
import com.horse.schedules.domain.ScheduleAuditTargetType;
import com.horse.schedules.domain.ScheduleConfigStatus;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.timeslots.domain.TimeSlotCapacity;
import com.horse.timeslots.infrastructure.TimeSlotCapacityRepository;

import jakarta.persistence.EntityManager;

@Import({
	TestcontainersConfiguration.class,
	ScheduleAuditLogRepository.class,
	ScheduleConfigGuardRepository.class,
	ReservationMemberDayGuardRepository.class
})
@DataJpaTest
class ScheduleRepositoryIntegrationTest {

	private static final String CLASS_CAPACITIES_JSON = """
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
	RegularScheduleTemplateRepository templateRepository;

	@Autowired
	RecurringHolidayRuleRepository holidayRepository;

	@Autowired
	ScheduleConfigGuardRepository configGuardRepository;

	@Autowired
	ScheduleDateRepository scheduleDateRepository;

	@Autowired
	ReservationMemberDayGuardRepository memberDayGuardRepository;

	@Autowired
	ScheduleAuditLogRepository auditLogRepository;

	@Autowired
	TimeSlotCapacityRepository timeSlotRepository;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void 운영_날짜_fixture를_초기화한다() {
		jdbcTemplate.update("DELETE FROM schedule_dates");
	}

	@Test
	void 정규_템플릿과_정기_휴일을_저장하고_조회한다() {
		final RegularScheduleTemplate template = templateRepository.saveAndFlush(createTemplate());
		final RecurringHolidayRule holiday = holidayRepository.saveAndFlush(
			RecurringHolidayRule.create(
				DayOfWeek.MONDAY,
				LocalDate.of(2026, 7, 1),
				null,
				"정기 휴무",
				"schedule-admin"));
		entityManager.clear();

		final RegularScheduleTemplate foundTemplate = templateRepository
			.findByDayOfWeekAndStartTime(DayOfWeek.TUESDAY, LocalTime.of(9, 0))
			.orElseThrow();
		final RecurringHolidayRule foundHoliday = holidayRepository.findById(holiday.getId()).orElseThrow();

		assertThat(foundTemplate.getId()).isEqualTo(template.getId());
		assertThat(foundTemplate.getEndTime()).isEqualTo(LocalTime.of(9, 45));
		assertThat(foundTemplate.getCreatedAt()).isNotNull();
		assertThat(foundHoliday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(foundHoliday.getCreatedAt()).isNotNull();
	}

	@Test
	void 같은_요일과_시작_시각의_템플릿은_중복_저장할_수_없다() {
		templateRepository.saveAndFlush(createTemplate());

		assertThatThrownBy(() -> templateRepository.saveAndFlush(createTemplate()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 정기_휴일의_잘못된_기간은_DB에서도_거부한다() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO recurring_holiday_rules (
				day_of_week, effective_from, effective_to, reason, active,
				created_by, updated_by
			) VALUES (
				'MONDAY', '2026-08-02', '2026-08-01', '잘못된 기간', TRUE,
				'schedule-admin', 'schedule-admin'
			)
			"""))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 일정_운영_enum과_singleton_제약을_DB에서도_검증한다() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO schedule_config_guard (id, status, active_version)
			VALUES (2, 'ACTIVE', 1)
			"""))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES ('2026-08-09', 'INVALID', 1)
			"""))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO schedule_audit_logs (
				target_type, target_key, action, actor_auth_subject, reason
			) VALUES ('INVALID', 'target:1', 'CREATED', 'schedule-admin', '검증')
			"""))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 설정_Guard는_정확히_하나의_ACTIVE_행으로_시작한다() {
		final Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM schedule_config_guard",
			Integer.class);

		assertThat(count).isEqualTo(1);
		assertThat(configGuardRepository.findSingletonForShare().getStatus())
			.isEqualTo(ScheduleConfigStatus.ACTIVE);
	}

	@Test
	void 운영_날짜는_날짜_유일성을_보장한다() {
		scheduleDateRepository.saveAndFlush(ScheduleDate.create(LocalDate.of(2026, 8, 1), 1L));

		assertThatThrownBy(() -> scheduleDateRepository.saveAndFlush(
			ScheduleDate.create(LocalDate.of(2026, 8, 1), 1L)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 회원_날짜_Guard는_정렬된_순서로_획득한다() {
		final Member firstMember = memberRepository.saveAndFlush(
			Member.create("r02-guard-first", "첫 회원", "010-0000-0001"));
		final Member secondMember = memberRepository.saveAndFlush(
			Member.create("r02-guard-second", "둘 회원", "010-0000-0002"));

		final List<ReservationMemberDayGuard> guards = memberDayGuardRepository.acquireAllOrdered(
			List.of(
				ReservationMemberDayGuardId.of(secondMember.getId(), LocalDate.of(2026, 8, 2)),
				ReservationMemberDayGuardId.of(firstMember.getId(), LocalDate.of(2026, 8, 2)),
				ReservationMemberDayGuardId.of(firstMember.getId(), LocalDate.of(2026, 8, 1))));

		assertThat(guards)
			.extracting(ReservationMemberDayGuard::getId)
			.containsExactly(
				ReservationMemberDayGuardId.of(firstMember.getId(), LocalDate.of(2026, 8, 1)),
				ReservationMemberDayGuardId.of(firstMember.getId(), LocalDate.of(2026, 8, 2)),
				ReservationMemberDayGuardId.of(secondMember.getId(), LocalDate.of(2026, 8, 2)));
	}

	@Test
	void 일정_감사_로그는_추가_순서대로_조회한다() {
		final ScheduleAuditLog first = auditLogRepository.append(createAuditLog("CREATED"));
		final ScheduleAuditLog second = auditLogRepository.append(createAuditLog("UPDATED"));
		entityManager.flush();
		entityManager.clear();

		final List<ScheduleAuditLog> logs = auditLogRepository.findAllByTarget(
			ScheduleAuditTargetType.TEMPLATE,
			"template:1");

		assertThat(logs).extracting(ScheduleAuditLog::getId)
			.containsExactly(first.getId(), second.getId());
		assertThat(logs).extracting(ScheduleAuditLog::getAction)
			.containsExactly("CREATED", "UPDATED");
	}

	@Test
	void 시간대_마감_상태는_세_원인의_OR로_DB가_계산한다() {
		final TimeSlotCapacity timeSlot = timeSlotRepository.saveAndFlush(TimeSlotCapacity.create(
			LocalDate.of(2026, 8, 3),
			LocalTime.of(9, 0),
			8,
			4,
			validClassCapacities()));

		jdbcTemplate.update("""
			UPDATE time_slot_capacities
			SET recurring_holiday_closed = TRUE
			WHERE id = ?
			""", timeSlot.getId());

		assertThat(jdbcTemplate.queryForObject(
			"SELECT is_closed FROM time_slot_capacities WHERE id = ?",
			Boolean.class,
			timeSlot.getId())).isTrue();
		assertThatThrownBy(() -> jdbcTemplate.update(
			"UPDATE time_slot_capacities SET is_closed = FALSE WHERE id = ?",
			timeSlot.getId()))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 시간대_출처와_템플릿_참조의_결합을_DB가_검증한다() {
		final Long templateId = templateRepository.saveAndFlush(createTemplate()).getId();

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, source, template_id, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES ('2026-08-04', '09:00:00', 'MANUAL', ?, 8, 4, ?)
			""", templateId, CLASS_CAPACITIES_JSON))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, source, total_capacity,
				round_arena_capacity, class_capacity_json
			) VALUES ('2026-08-04', '10:00:00', 'TEMPLATE', 8, 4, ?)
			""", CLASS_CAPACITIES_JSON))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 잠금_조회는_고유_인덱스를_사용한다() {
		scheduleDateRepository.saveAndFlush(ScheduleDate.create(LocalDate.of(2026, 8, 5), 1L));
		final Member member = memberRepository.saveAndFlush(
			Member.create("r02-explain-member", "인덱스 회원", "010-0000-0003"));
		memberDayGuardRepository.acquire(member.getId(), LocalDate.of(2026, 8, 5));

		assertThat(explain("""
			SELECT * FROM schedule_config_guard WHERE id = 1 FOR SHARE
			""")).contains("\"key\": \"PRIMARY\"");
		assertThat(explain("""
			SELECT * FROM schedule_dates WHERE schedule_date = '2026-08-05' FOR UPDATE
			""")).contains("\"key\": \"uk_schedule_dates_date\"");
		assertThat(explain("""
			SELECT * FROM reservation_member_day_guards
			WHERE member_id = %d AND lesson_date = '2026-08-05'
			FOR UPDATE
			""".formatted(member.getId()))).contains("\"key\": \"PRIMARY\"");
	}

	@Test
	void 템플릿_영향_조회에_필요한_인덱스를_유지한다() {
		final List<String> indexes = jdbcTemplate.queryForList("""
			SELECT DISTINCT index_name
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'time_slot_capacities'
			  AND index_name IN (
				'fk_time_slot_capacities_template',
				'uk_time_slot_capacities_lesson_date_start_time'
			  )
			ORDER BY index_name
			""", String.class);

		assertThat(indexes).containsExactly(
			"fk_time_slot_capacities_template",
			"uk_time_slot_capacities_lesson_date_start_time");
	}

	private String explain(String sql) {
		return jdbcTemplate.queryForObject("EXPLAIN FORMAT=JSON " + sql, String.class);
	}

	private RegularScheduleTemplate createTemplate() {
		return RegularScheduleTemplate.create(
			DayOfWeek.TUESDAY,
			LocalTime.of(9, 0),
			LocalTime.of(9, 45),
			8,
			4,
			validClassCapacities(),
			"schedule-admin");
	}

	private ScheduleAuditLog createAuditLog(String action) {
		return ScheduleAuditLog.create(
			ScheduleAuditTargetType.TEMPLATE,
			"template:1",
			action,
			null,
			Map.of("active", true),
			"schedule-admin",
			"일정 변경",
			null);
	}

	private Map<String, Integer> validClassCapacities() {
		return Map.of(
			"FIRST_RIDE", 2,
			"ROUND_BEGINNER", 2,
			"ROUND_TROT", 2,
			"LARGE_ARENA_BEGINNER", 3,
			"LARGE_ARENA_TROT", 3,
			"DRESSAGE", 1,
			"JUMPING", 1);
	}
}
