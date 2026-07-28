package com.horse.reservations.infrastructure;

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
class AdminManualReservationAuditMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "33";
	private static final String CURRENT_VERSION = "34";

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V33에서_V34로_올리면_기존_감사를_보존하고_관리자_생성_감사를_허용한다()
		throws Exception {
		final String databaseUrl = createDatabase("horse_m31_09_admin_audit_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		final long reservationId = insertReservation(databaseUrl);

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			insertExistingAudit(statement, reservationId);
			assertThatThrownBy(() -> insertAdminCreationAudit(
				statement,
				reservationId,
				"admin"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("chk_reservation_change_logs_change_type");
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			insertAdminCreationAudit(statement, reservationId, "admin");
			assertThat(auditCount(statement)).isEqualTo(2);
			assertThatThrownBy(() -> insertAdminCreationAudit(
				statement,
				reservationId,
				"member"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("chk_reservation_change_logs_admin_reservation_created");
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	@Test
	void 빈_DB에_V34까지_적용하면_관리자_생성_감사_CHECK가_존재한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_09_admin_audit_fresh");

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(adminCreationConstraintCount(statement)).isOne();
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	private long insertReservation(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('admin-audit-target', '감사 대상', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, end_time, status,
					payment_source, payment_due_at, approval_requested_at, admin_confirmed_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-05', '09:00:00', '09:45:00',
					'confirmed', 'single_payment', '2026-08-05 08:00:00',
					'2026-08-05 07:00:00', '2026-08-05 07:00:00'
				FROM members
				WHERE auth_subject = 'admin-audit-target'
				""");
			try (ResultSet resultSet = statement.executeQuery(
				"SELECT id FROM reservations LIMIT 1")) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong(1);
			}
		}
	}

	private void insertExistingAudit(Statement statement, long reservationId) throws SQLException {
		statement.execute("""
			INSERT INTO reservation_change_logs (
				reservation_id, actor_auth_subject, actor_type, from_status, to_status,
				from_lesson_date, from_start_time, to_lesson_date, to_start_time,
				change_type, coupon_action, memo
			) VALUES (
				%d, 'restore-admin', 'admin', 'payment_expired', 'confirmed',
				'2026-08-05', '09:00:00', '2026-08-05', '09:00:00',
				'payment_restored', 'none', '기존 복구 감사'
			)
			""".formatted(reservationId));
	}

	private void insertAdminCreationAudit(
		Statement statement,
		long reservationId,
		String actorType
	) throws SQLException {
		statement.execute("""
			INSERT INTO reservation_change_logs (
				reservation_id, actor_auth_subject, actor_type, from_status, to_status,
				from_lesson_date, from_start_time, to_lesson_date, to_start_time,
				change_type, coupon_action, memo
			) VALUES (
				%d, 'manual-admin', '%s', 'confirmed', 'confirmed',
				'2026-08-05', '09:00:00', '2026-08-05', '09:00:00',
				'admin_reservation_created', 'none', '전화 접수'
			)
			""".formatted(reservationId, actorType));
	}

	private long auditCount(Statement statement) throws SQLException {
		return count(statement, "SELECT COUNT(*) FROM reservation_change_logs");
	}

	private long adminCreationConstraintCount(Statement statement) throws SQLException {
		return count(statement, """
			SELECT COUNT(*)
			FROM information_schema.table_constraints
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_change_logs'
			  AND constraint_name =
			      'chk_reservation_change_logs_admin_reservation_created'
			  AND constraint_type = 'CHECK'
			""");
	}

	private long count(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private String currentMigrationVersion(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT version
			FROM flyway_schema_history
			WHERE success = TRUE
			ORDER BY installed_rank DESC
			LIMIT 1
			""")) {
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
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}
}
