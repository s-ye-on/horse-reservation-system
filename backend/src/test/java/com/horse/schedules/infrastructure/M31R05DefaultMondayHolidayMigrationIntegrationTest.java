package com.horse.schedules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class M31R05DefaultMondayHolidayMigrationIntegrationTest {

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 기본_월요일_휴일과_pending_version과_감사를_함께_이관한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r05_default_holiday");
		flyway(databaseUrl, "25").migrate();

		flyway(databaseUrl, "26").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertDefaultMondayRule(statement);
			assertGuard(statement, "SYNCING", 1L, 2L);
			assertAudit(statement, 2L);
		}
	}

	@Test
	void 기존_활성_월요일_규칙은_기준_데이터로_덮어쓰지_않는다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r05_existing_holiday");
		flyway(databaseUrl, "25").migrate();
		insertExistingMondayRule(databaseUrl);

		flyway(databaseUrl, "26").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(count(statement, """
				SELECT COUNT(*)
				FROM recurring_holiday_rules
				WHERE day_of_week = 'MONDAY'
				  AND active = TRUE
				""")).isOne();
			assertThat(value(statement, """
				SELECT reason
				FROM recurring_holiday_rules
				WHERE day_of_week = 'MONDAY'
				""")).isEqualTo("기존 월요일 휴일");
			assertGuard(statement, "ACTIVE", 1L, null);
			assertThat(count(statement, """
				SELECT COUNT(*)
				FROM schedule_audit_logs
				WHERE target_type = 'RECURRING_HOLIDAY'
				""")).isZero();
		}
	}

	@Test
	void 감사_기록에_실패하면_기준_휴일과_Guard_전이를_모두_롤백한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r05_atomic_migration");
		flyway(databaseUrl, "25").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE schedule_audit_logs");
		}

		assertThatThrownBy(() -> flyway(databaseUrl, "26").migrate())
			.isInstanceOf(RuntimeException.class);

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(count(statement, "SELECT COUNT(*) FROM recurring_holiday_rules")).isZero();
			assertGuard(statement, "ACTIVE", 1L, null);
		}
	}

	private void assertDefaultMondayRule(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT day_of_week, effective_from, effective_to, reason, active,
				created_by, updated_by
			FROM recurring_holiday_rules
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("day_of_week")).isEqualTo("MONDAY");
			assertThat(resultSet.getDate("effective_from").toLocalDate())
				.isEqualTo(java.time.LocalDate.of(1970, 1, 1));
			assertThat(resultSet.getDate("effective_to")).isNull();
			assertThat(resultSet.getString("reason")).isEqualTo("기본 월요일 정기 휴일");
			assertThat(resultSet.getBoolean("active")).isTrue();
			assertThat(resultSet.getString("created_by")).isEqualTo("system:migration:v26");
			assertThat(resultSet.getString("updated_by")).isEqualTo("system:migration:v26");
			assertThat(resultSet.next()).isFalse();
		}
	}

	private void assertGuard(
		Statement statement,
		String status,
		long activeVersion,
		Long pendingVersion
	) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT status, active_version, pending_version, sync_started_by
			FROM schedule_config_guard
			WHERE id = 1
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("status")).isEqualTo(status);
			assertThat(resultSet.getLong("active_version")).isEqualTo(activeVersion);
			if (pendingVersion == null) {
				assertThat(resultSet.getObject("pending_version")).isNull();
				assertThat(resultSet.getString("sync_started_by")).isNull();
			}
			else {
				assertThat(resultSet.getLong("pending_version")).isEqualTo(pendingVersion);
				assertThat(resultSet.getString("sync_started_by"))
					.isEqualTo("system:migration:v26");
			}
		}
	}

	private void assertAudit(Statement statement, long pendingVersion) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT target_type, target_key, action, actor_auth_subject, reason,
				metadata_json
			FROM schedule_audit_logs
			""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("target_type")).isEqualTo("RECURRING_HOLIDAY");
			assertThat(resultSet.getString("target_key")).startsWith("recurring-holiday:");
			assertThat(resultSet.getString("action")).isEqualTo("DEFAULT_CREATED");
			assertThat(resultSet.getString("actor_auth_subject"))
				.isEqualTo("system:migration:v26");
			assertThat(resultSet.getString("reason")).isEqualTo("기본 월요일 정기 휴일");
			assertThat(resultSet.getString("metadata_json"))
				.contains("\"pendingConfigVersion\": " + pendingVersion);
			assertThat(resultSet.getString("metadata_json"))
				.contains("\"affectedDateCount\":");
			assertThat(resultSet.getString("metadata_json"))
				.contains("\"templateTimeSlotCount\": 0");
			assertThat(resultSet.getString("metadata_json"))
				.contains("\"activeReservationCount\": 0");
			assertThat(resultSet.next()).isFalse();
		}
	}

	private void insertExistingMondayRule(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				INSERT INTO recurring_holiday_rules (
					day_of_week, effective_from, effective_to, reason,
					active, created_by, updated_by
				) VALUES (
					'MONDAY', '2026-01-01', NULL, '기존 월요일 휴일',
					TRUE, 'schedule-admin', 'schedule-admin'
				)
				""");
		}
	}

	private long count(Statement statement, String sql) throws Exception {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private String value(Statement statement, String sql) throws Exception {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
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
		return mysqlContainer.getJdbcUrl().replace(
			mysqlContainer.getDatabaseName(),
			databaseName);
	}
}
