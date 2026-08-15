package com.horse.schedules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class M31R02ScheduleFoundationMigrationIntegrationTest {

	private static final String CLASS_CAPACITIES = """
		{
			"FIRST_RIDE": 2,
			"ROUND_BEGINNER": 2,
			"ROUND_TROT": 2,
			"LARGE_ARENA_BEGINNER": 3,
			"LARGE_ARENA_TROT": 3,
			"CANTER_BEGINNER": 3,
			"CANTER": 3,
			"DRESSAGE": 1,
			"JUMPING": 1
		}
		""";

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V16_데이터를_일정_운영_기반_스키마로_이관한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r02_valid");
		flyway(databaseUrl, "16").migrate();
		insertTimeSlot(databaseUrl, "09:00:00", "09:45:00", false, "MANUAL");
		insertTimeSlot(databaseUrl, "10:00:00", "10:45:00", true, "MANUAL");
		insertReservation(databaseUrl);

		flyway(databaseUrl, "24").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertColumnNotNull(statement, "time_slot_capacities", "end_time");
			assertColumnNotNull(statement, "reservations", "end_time");
			assertTimeSlotMigration(statement);
			assertScheduleFoundation(statement);
			assertV15UniqueIndex(statement);
		}
	}

	@Test
	void 종료_시각이_NULL이면_첫_DDL_전에_이관을_중단한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r02_null_end");
		flyway(databaseUrl, "16").migrate();
		insertTimeSlot(databaseUrl, "09:00:00", null, false, "MANUAL");

		assertPreflightFailure(databaseUrl, "NULL_END_TIME");
	}

	@Test
	void 수업_구간이_45분이_아니면_첫_DDL_전에_이관을_중단한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r02_wrong_interval");
		flyway(databaseUrl, "16").migrate();
		dropTimeSlotIntervalCheck(databaseUrl);
		insertTimeSlot(databaseUrl, "09:00:00", "09:30:00", false, "MANUAL");

		assertPreflightFailure(databaseUrl, "LESSON_INTERVAL_NOT_45_MINUTES");
	}

	@Test
	void 수업이_자정을_넘으면_첫_DDL_전에_이관을_중단한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r02_midnight");
		flyway(databaseUrl, "16").migrate();
		dropTimeSlotIntervalCheck(databaseUrl);
		insertTimeSlot(databaseUrl, "23:30:00", "24:15:00", false, "MANUAL");

		assertPreflightFailure(databaseUrl, "INVALID_END_TIME");
	}

	@Test
	void 템플릿_메타데이터가_없는_기존_TEMPLATE_출처는_이관을_중단한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r02_template_source");
		flyway(databaseUrl, "16").migrate();
		insertTimeSlot(databaseUrl, "09:00:00", "09:45:00", false, "TEMPLATE");

		assertPreflightFailure(databaseUrl, "TEMPLATE_SOURCE_WITHOUT_TEMPLATE_METADATA");
	}

	private void assertPreflightFailure(String databaseUrl, String issueType) throws Exception {
		final Throwable migrationFailure = catchThrowable(() -> flyway(databaseUrl, "24").migrate());

		assertThat(migrationFailure).isInstanceOf(FlywayException.class);
		assertThat(rootCause(migrationFailure))
			.hasMessageContaining("M31-R02 preflight failed before DDL")
			.hasMessageContaining(issueType)
			.hasMessageContaining("time_slot_capacities")
			.hasMessageContaining("total_issue_count");

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(tableCount(statement, "regular_schedule_templates")).isZero();
			try (ResultSet resultSet = statement.executeQuery("""
				SELECT is_nullable
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = 'time_slot_capacities'
				  AND column_name = 'end_time'
				""")) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString("is_nullable")).isEqualTo("YES");
			}
			assertThat(statement.executeQuery("SELECT COUNT(*) FROM time_slot_capacities").next()).isTrue();
		}
	}

	private Throwable rootCause(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private void assertColumnNotNull(Statement statement, String tableName, String columnName)
		throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT is_nullable, column_default
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = '%s'
			  AND column_name = '%s'
			""".formatted(tableName, columnName))) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("is_nullable")).isEqualTo("NO");
			assertThat(resultSet.getString("column_default")).containsIgnoringCase("addtime");
		}
	}

	private void assertTimeSlotMigration(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT source, template_id, admin_closed, recurring_holiday_closed,
				template_inactive_closed, is_closed
			FROM time_slot_capacities
			ORDER BY start_time
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("source")).isEqualTo("MANUAL");
			assertThat(resultSet.getObject("template_id")).isNull();
			assertThat(resultSet.getBoolean("admin_closed")).isFalse();
			assertThat(resultSet.getBoolean("recurring_holiday_closed")).isFalse();
			assertThat(resultSet.getBoolean("template_inactive_closed")).isFalse();
			assertThat(resultSet.getBoolean("is_closed")).isFalse();

			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("source")).isEqualTo("MANUAL");
			assertThat(resultSet.getBoolean("admin_closed")).isTrue();
			assertThat(resultSet.getBoolean("recurring_holiday_closed")).isFalse();
			assertThat(resultSet.getBoolean("template_inactive_closed")).isFalse();
			assertThat(resultSet.getBoolean("is_closed")).isTrue();
		}

		try (ResultSet resultSet = statement.executeQuery("""
			SELECT extra, generation_expression
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = 'time_slot_capacities'
			  AND column_name = 'is_closed'
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("extra")).containsIgnoringCase("STORED GENERATED");
			assertThat(resultSet.getString("generation_expression"))
				.contains("admin_closed")
				.contains("recurring_holiday_closed")
				.contains("template_inactive_closed");
		}

		assertThatThrownBy(() -> statement.executeUpdate("""
			UPDATE time_slot_capacities
			SET is_closed = FALSE
			WHERE start_time = '10:00:00'
			"""))
			.isInstanceOf(SQLException.class);

		statement.executeUpdate("""
			UPDATE time_slot_capacities
			SET recurring_holiday_closed = TRUE
			WHERE start_time = '09:00:00'
			""");
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT is_closed
			FROM time_slot_capacities
			WHERE start_time = '09:00:00'
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getBoolean("is_closed")).isTrue();
		}
	}

	private void assertScheduleFoundation(Statement statement) throws SQLException {
		assertThat(tableCount(statement, "regular_schedule_templates")).isOne();
		assertThat(tableCount(statement, "recurring_holiday_rules")).isOne();
		assertThat(tableCount(statement, "schedule_config_guard")).isOne();
		assertThat(tableCount(statement, "schedule_dates")).isOne();
		assertThat(tableCount(statement, "reservation_member_day_guards")).isOne();
		assertThat(tableCount(statement, "schedule_audit_logs")).isOne();

		try (ResultSet resultSet = statement.executeQuery("""
			SELECT COUNT(*) AS guard_count, MIN(status) AS status, MIN(active_version) AS active_version
			FROM schedule_config_guard
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt("guard_count")).isEqualTo(1);
			assertThat(resultSet.getString("status")).isEqualTo("ACTIVE");
			assertThat(resultSet.getLong("active_version")).isEqualTo(1);
		}

		assertThatThrownBy(() -> statement.executeUpdate("""
			UPDATE time_slot_capacities
			SET source = 'TEMPLATE'
			WHERE start_time = '09:00:00'
			"""))
			.isInstanceOf(SQLException.class);
	}

	private void assertV15UniqueIndex(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT COUNT(*) AS indexed_columns
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservations'
			  AND index_name = 'uk_reservations_active_member_slot'
			  AND non_unique = 0
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt("indexed_columns")).isEqualTo(4);
		}
	}

	private int tableCount(Statement statement, String tableName) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name = '%s'
			""".formatted(tableName))) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getInt(1);
		}
	}

	private void insertTimeSlot(
		String databaseUrl,
		String startTime,
		String endTime,
		boolean closed,
		String source
	) throws Exception {
		try (Connection connection = connection(databaseUrl);
			var statement = connection.prepareStatement("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, end_time, source, total_capacity,
					round_arena_capacity, class_capacity_json, is_closed
				) VALUES ('2026-08-01', ?, ?, ?, 8, 4, ?, ?)
				""")) {
			statement.setString(1, startTime);
			statement.setString(2, endTime);
			statement.setString(3, source);
			statement.setString(4, CLASS_CAPACITIES);
			statement.setBoolean(5, closed);
			statement.executeUpdate();
		}
	}

	private void insertReservation(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('m31-r02-member', '이관 회원', '010-0000-0000')
				""");
			statement.executeUpdate("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, end_time, status,
					payment_source, payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-01', '11:00:00', '11:45:00',
					'pending_payment', 'single_payment',
					'2026-07-31 12:00:00', '2026-07-31 10:00:00'
				FROM members
				WHERE auth_subject = 'm31-r02-member'
				""");
		}
	}

	private void dropTimeSlotIntervalCheck(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				ALTER TABLE time_slot_capacities
				DROP CHECK chk_time_slot_capacities_lesson_interval
				""");
		}
	}

	private String createDatabase(String databaseName) throws Exception {
		try (Connection connection = DriverManager.getConnection(
			rootJdbcUrl("mysql"), "root", mysqlContainer.getPassword());
			Statement statement = connection.createStatement()) {
			statement.execute("DROP DATABASE IF EXISTS " + databaseName);
			statement.execute("CREATE DATABASE " + databaseName);
		}
		return rootJdbcUrl(databaseName);
	}

	private Flyway flyway(String databaseUrl, String target) {
		return Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion(target))
			.load();
	}

	private Connection connection(String databaseUrl) throws Exception {
		return DriverManager.getConnection(databaseUrl, "root", mysqlContainer.getPassword());
	}

	private String rootJdbcUrl(String databaseName) {
		return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul"
			.formatted(mysqlContainer.getHost(), mysqlContainer.getMappedPort(3306), databaseName);
	}
}
