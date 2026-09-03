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
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class MemberRideCountAdjustmentMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "38";
	private static final String CURRENT_VERSION = "39";

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V38에서_V39로_올리면_기존_감사를_보존하고_ride_count_adjusted_action을_허용한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m32_07_adjustment_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (
					auth_subject, name, phone, general_ride_count, progression_management_started_at
				) VALUES ('m32-07-migration-member', '보정 회원', '010-0000-0000', 20, CURRENT_TIMESTAMP(6))
				""");
			statement.execute("""
				INSERT INTO member_class_progression_audit_logs (
					member_id, action, from_state, to_state, actor_auth_subject, reason
				)
				SELECT id, 'BASELINE_SET', JSON_OBJECT('actualCompletedRideCount', 20),
					JSON_OBJECT('actualCompletedRideCount', 20), 'm32-06-admin', '기존 감사'
				FROM members WHERE auth_subject = 'm32-07-migration-member'
				""");
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(count(statement, "SELECT COUNT(*) FROM member_class_progression_audit_logs"))
				.isOne();
			statement.execute("""
				INSERT INTO member_class_progression_audit_logs (
					member_id, action, from_state, to_state, actor_auth_subject, reason
				)
				SELECT id, 'RIDE_COUNT_ADJUSTED', JSON_OBJECT('actualCompletedRideCount', 20),
					JSON_OBJECT('actualCompletedRideCount', 21, 'rideCountDelta', 1),
					'm32-07-admin', '누락 집계 정정'
				FROM members WHERE auth_subject = 'm32-07-migration-member'
				""");
			assertThat(count(statement, """
				SELECT COUNT(*) FROM member_class_progression_audit_logs
				WHERE action = 'RIDE_COUNT_ADJUSTED'
				""")).isOne();

			assertThatThrownBy(() -> statement.execute("""
				INSERT INTO member_class_progression_audit_logs (
					member_id, action, from_state, to_state, actor_auth_subject, reason
				)
				SELECT id, 'UNKNOWN_ACTION', JSON_OBJECT(), JSON_OBJECT(), 'm32-07-admin', '잘못된 action'
				FROM members WHERE auth_subject = 'm32-07-migration-member'
				"""))
				.isInstanceOf(SQLException.class);
		}
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

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}
}
