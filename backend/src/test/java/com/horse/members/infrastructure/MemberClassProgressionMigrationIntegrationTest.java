package com.horse.members.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class MemberClassProgressionMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "37";
	private static final String CURRENT_VERSION = "38";

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V37에서_V38로_올리면_사실_횟수는_보존하고_기존_특수_승인_부족분만_전환한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m32_06_progression_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
					INSERT INTO members (
						auth_subject, name, phone, general_ride_count, dressage_approved, created_at
					) VALUES
						('existing-general', '일반 회원', '010-0000-0001', 20, FALSE, '2020-01-01 00:00:00'),
						('existing-approved', '승인 회원', '010-0000-0002', 20, TRUE, '2020-01-01 00:00:00'),
						('existing-canter', '구보 회원', '010-0000-0003', 100, TRUE, '2020-01-01 00:00:00')
				""");
			statement.execute("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, total_capacity, round_arena_capacity,
					class_capacity_json
				) VALUES (
					'2026-09-01', '09:00:00', 4, 2,
					JSON_OBJECT(
						'FIRST_RIDE', 2, 'ROUND_BEGINNER', 2, 'ROUND_TROT', 2,
						'LARGE_ARENA_BEGINNER', 4, 'LARGE_ARENA_TROT', 4,
						'DRESSAGE', 1, 'JUMPING', 1)
				)
				""");
			statement.execute("""
				INSERT INTO regular_schedule_templates (
					day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
					class_capacity_json, created_by, updated_by
				) VALUES (
					'TUESDAY', '09:00:00', '09:45:00', 4, 2,
					JSON_OBJECT(
						'FIRST_RIDE', 2, 'ROUND_BEGINNER', 2, 'ROUND_TROT', 2,
						'LARGE_ARENA_BEGINNER', 4, 'LARGE_ARENA_TROT', 4,
						'DRESSAGE', 1, 'JUMPING', 1),
					'migration-test', 'migration-test'
				)
				""");
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(value(statement, """
				SELECT general_ride_count
				FROM members
				WHERE auth_subject = 'existing-approved'
				""")).isEqualTo(20);
				assertThat(value(statement, """
					SELECT special_approval_progression_credit
				FROM members
				WHERE auth_subject = 'existing-approved'
				""")).isEqualTo(6);
			assertThat(value(statement, """
				SELECT special_approval_progression_credit
				FROM members
				WHERE auth_subject = 'existing-canter'
					""")).isZero();
				assertThat(count(statement, """
					SELECT COUNT(*)
					FROM members
					WHERE auth_subject = 'existing-general'
					  AND progression_management_started_at IS NULL
					""")).isOne();
				assertThat(count(statement, """
					SELECT COUNT(*)
					FROM members
					WHERE auth_subject IN ('existing-approved', 'existing-canter')
					  AND progression_management_started_at > created_at
					""")).isEqualTo(2);
				assertThat(count(statement, """
					SELECT COUNT(*)
					FROM member_class_progression_audit_logs
					WHERE action = 'PROGRESSION_INITIALIZED'
					""")).isEqualTo(2);
				assertThat(count(statement, """
					SELECT COUNT(*)
					FROM member_class_progression_audit_logs audit_log
					JOIN members member ON member.id = audit_log.member_id
					WHERE member.auth_subject = 'existing-approved'
					  AND JSON_UNQUOTE(JSON_EXTRACT(audit_log.to_state, '$.progressionClass')) = 'LARGE_ARENA_TROT'
					  AND JSON_UNQUOTE(JSON_EXTRACT(audit_log.to_state, '$.effectiveClass')) = 'LARGE_ARENA_TROT'
					""")).isOne();
				assertThat(count(statement, """
					SELECT COUNT(*)
					FROM member_class_progression_audit_logs audit_log
					JOIN members member ON member.id = audit_log.member_id
					WHERE member.auth_subject = 'existing-canter'
					  AND JSON_UNQUOTE(JSON_EXTRACT(audit_log.to_state, '$.progressionClass')) = 'CANTER'
					  AND JSON_UNQUOTE(JSON_EXTRACT(audit_log.to_state, '$.effectiveClass')) = 'CANTER'
					""")).isOne();
				assertThat(value(statement, """
					SELECT JSON_EXTRACT(class_capacity_json, '$.CANTER_BEGINNER')
					FROM time_slot_capacities
					WHERE lesson_date = '2026-09-01' AND start_time = '09:00:00'
					""")).isZero();
				assertThat(value(statement, """
					SELECT JSON_EXTRACT(class_capacity_json, '$.CANTER')
					FROM regular_schedule_templates
					WHERE day_of_week = 'TUESDAY' AND start_time = '09:00:00'
					""")).isZero();
		}
	}

	@Test
	void progression_상태_shape와_특수_승인_hold_상호_배타를_DB에서_강제한다() {
		final long memberId = insertMember("progression-constraint");

		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE members
			SET progression_baseline_class = 'LARGE_ARENA_TROT'
			WHERE id = ?
			""", memberId))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_members_progression_baseline_shape");

		jdbcTemplate.update("""
			UPDATE members
			SET progression_management_started_at = CURRENT_TIMESTAMP(6),
				progression_baseline_class = 'LARGE_ARENA_TROT',
				progression_baseline_threshold = 26,
				progression_baseline_actual_ride_count = 0,
				promotion_hold_class = 'ROUND_TROT'
			WHERE id = ?
			""", memberId);

		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE members
			SET dressage_approved = TRUE
			WHERE id = ?
			""", memberId))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_members_special_approval_hold_exclusive");
	}

	@Test
	void 구보_클래스를_예약과_시간표와_TimeSlot_정원_catalog에_전파한다() {
		final long memberId = insertMember("canter-catalog-member");
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at
			) VALUES (?, 'CANTER_BEGINNER', '2026-09-01', '09:00:00', '09:45:00',
				'pending_payment', 'single_payment', '2026-08-31 12:00:00', '2026-08-31 09:00:00')
			""", memberId);

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservations
			WHERE class_type = 'CANTER_BEGINNER'
			""", Integer.class)).isOne();
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, total_capacity, round_arena_capacity,
				class_capacity_json
			) VALUES (
				'2026-09-01', '10:00:00', 4, 2,
				JSON_OBJECT(
					'FIRST_RIDE', 2, 'ROUND_BEGINNER', 2, 'ROUND_TROT', 2,
					'LARGE_ARENA_BEGINNER', 4, 'LARGE_ARENA_TROT', 4,
					'DRESSAGE', 1, 'JUMPING', 1)
			)
			"""))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_time_slot_capacities_class_capacity_json");

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO regular_schedule_templates (
				day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
				class_capacity_json, created_by, updated_by
			) VALUES (
				'TUESDAY', '10:00:00', '10:45:00', 4, 2,
				JSON_OBJECT(
					'FIRST_RIDE', 2, 'ROUND_BEGINNER', 2, 'ROUND_TROT', 2,
					'LARGE_ARENA_BEGINNER', 4, 'LARGE_ARENA_TROT', 4,
					'DRESSAGE', 1, 'JUMPING', 1),
				'migration-test', 'migration-test'
			)
			"""))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_regular_schedule_templates_class_capacity");
	}

	private long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '클래스 회원', '010-9999-9999')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String createDatabase(String databaseName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			rootJdbcUrl("mysql"),
			"root",
			mysqlContainer.getPassword());
			Statement statement = connection.createStatement()) {
			statement.execute("DROP DATABASE IF EXISTS " + databaseName);
			statement.execute("CREATE DATABASE " + databaseName);
		}
		return rootJdbcUrl(databaseName);
	}

	private Connection connection(String databaseUrl) throws SQLException {
		return DriverManager.getConnection(databaseUrl, "root", mysqlContainer.getPassword());
	}

	private Flyway flyway(String databaseUrl, String targetVersion) {
		return Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion(targetVersion))
			.load();
	}

	private long count(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private int value(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getInt(1);
		}
	}

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}
}
