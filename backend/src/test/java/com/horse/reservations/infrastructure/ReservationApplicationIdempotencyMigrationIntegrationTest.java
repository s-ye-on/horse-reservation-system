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
class ReservationApplicationIdempotencyMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "32";
	private static final String CURRENT_VERSION = "33";

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V32에서_V33으로_올리면_기존_예약을_보존하고_멱등성_scope를_강제한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_08_idempotency_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		final long reservationId = insertExistingReservation(databaseUrl);

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(reservationCount(statement)).isOne();
			insertProcessingLedger(statement, "migration-subject", "same-key");
			assertThatThrownBy(() ->
				insertProcessingLedger(statement, "migration-subject", "same-key"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("uk_reservation_application_idempotency_scope");
			insertProcessingLedger(statement, "other-subject", "same-key");
			insertProcessingLedger(statement, "MIGRATION-SUBJECT", "SAME-KEY");
			statement.executeUpdate("""
				UPDATE reservation_application_idempotencies
				SET status = 'completed',
					http_status = 201,
					response_body = '{"reservationId":1}',
					reservation_id = %d,
					completed_at = CURRENT_TIMESTAMP(6)
				WHERE auth_subject = 'migration-subject'
				""".formatted(reservationId));
			assertThat(ledgerCount(statement)).isEqualTo(3);
		}
	}

	@Test
	void 빈_DB에_V33까지_적용하면_멱등성_원장과_제약이_생성된다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_08_idempotency_fresh");

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(tableCount(statement)).isOne();
			assertThat(uniqueScopeColumns(statement))
				.isEqualTo("auth_subject,operation,idempotency_key");
			assertThat(scopeColumnCollations(statement)).isEqualTo(
				"auth_subject=utf8mb4_bin,"
					+ "idempotency_key=utf8mb4_bin,"
					+ "operation=ascii_bin");
			assertThat(scopeIndexMaximumBytes(statement)).isEqualTo(1_848);
			assertThat(resultColumnCollations(statement)).isEqualTo(
				"request_fingerprint=ascii_bin,status=ascii_bin");
			assertThat(checkConstraintNames(statement)).isEqualTo(
				"chk_reservation_application_idempotency_fingerprint,"
					+ "chk_reservation_application_idempotency_key,"
					+ "chk_reservation_application_idempotency_operation,"
					+ "chk_reservation_application_idempotency_result,"
					+ "chk_reservation_application_idempotency_status,"
					+ "chk_reservation_application_idempotency_subject");
			assertThat(reservationForeignKey(statement))
				.isEqualTo("reservation_id,reservations,id");
			assertThat(createdIndexColumns(statement)).isEqualTo("created_at,id");
			assertInvalidStateConstraints(statement);
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	private void assertInvalidStateConstraints(Statement statement) {
		assertThatThrownBy(() -> statement.execute("""
			INSERT INTO reservation_application_idempotencies (
				auth_subject, operation, idempotency_key, request_fingerprint, status
			) VALUES (
				'invalid-completed', 'member_reservation_create', 'invalid-completed',
				'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
				'completed'
			)
			"""))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("chk_reservation_application_idempotency_result");
		assertThatThrownBy(() -> statement.execute("""
			INSERT INTO reservation_application_idempotencies (
				auth_subject, operation, idempotency_key, request_fingerprint, status,
				http_status, response_body
			) VALUES (
				'invalid-processing', 'member_reservation_create', 'invalid-processing',
				'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
				'processing', 201, '{}'
			)
			"""))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("chk_reservation_application_idempotency_result");
		assertThatThrownBy(() -> statement.execute("""
			INSERT INTO reservation_application_idempotencies (
				auth_subject, operation, idempotency_key, request_fingerprint, status,
				http_status, response_body, reservation_id, completed_at
			) VALUES (
				'invalid-reservation', 'member_reservation_create', 'invalid-reservation',
				'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
				'completed', 201, '{}', 999999999, CURRENT_TIMESTAMP(6)
			)
			"""))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("fk_reservation_application_idempotencies_reservation");
	}

	private long insertExistingReservation(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('migration-existing-member', '기존 회원', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, end_time, status,
					payment_source, payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-05', '09:00:00', '09:45:00',
					'pending_payment', 'single_payment', '2026-08-05 08:00:00',
					'2026-08-05 07:00:00'
				FROM members
				WHERE auth_subject = 'migration-existing-member'
				""");
			try (ResultSet resultSet = statement.executeQuery(
				"SELECT id FROM reservations LIMIT 1")) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getLong(1);
			}
		}
	}

	private void insertProcessingLedger(
		Statement statement,
		String authSubject,
		String idempotencyKey
	) throws SQLException {
		statement.execute("""
			INSERT INTO reservation_application_idempotencies (
				auth_subject, operation, idempotency_key, request_fingerprint, status
			) VALUES (
				'%s', 'member_reservation_create', '%s',
				'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
				'processing'
			)
			""".formatted(authSubject, idempotencyKey));
	}

	private long reservationCount(Statement statement) throws SQLException {
		return count(statement, "SELECT COUNT(*) FROM reservations");
	}

	private long ledgerCount(Statement statement) throws SQLException {
		return count(statement, "SELECT COUNT(*) FROM reservation_application_idempotencies");
	}

	private long tableCount(Statement statement) throws SQLException {
		return count(statement, """
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_application_idempotencies'
			""");
	}

	private long count(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private String uniqueScopeColumns(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_application_idempotencies'
			  AND index_name = 'uk_reservation_application_idempotency_scope'
			""")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String scopeColumnCollations(Statement statement) throws SQLException {
		return columnCollations(statement, "'auth_subject', 'operation', 'idempotency_key'");
	}

	private String resultColumnCollations(Statement statement) throws SQLException {
		return columnCollations(statement, "'request_fingerprint', 'status'");
	}

	private String columnCollations(
		Statement statement,
		String columnNames
	) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT GROUP_CONCAT(
				CONCAT(column_name, '=', collation_name)
				ORDER BY column_name SEPARATOR ','
			)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_application_idempotencies'
			  AND column_name IN (%s)
			""".formatted(columnNames))) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private long scopeIndexMaximumBytes(Statement statement) throws SQLException {
		return count(statement, """
			SELECT SUM(column_definition.character_maximum_length * character_set.maxlen)
			FROM information_schema.statistics index_definition
			JOIN information_schema.columns column_definition
			  ON column_definition.table_schema = index_definition.table_schema
			 AND column_definition.table_name = index_definition.table_name
			 AND column_definition.column_name = index_definition.column_name
			JOIN information_schema.character_sets character_set
			  ON character_set.character_set_name = column_definition.character_set_name
			WHERE index_definition.table_schema = DATABASE()
			  AND index_definition.table_name = 'reservation_application_idempotencies'
			  AND index_definition.index_name =
			      'uk_reservation_application_idempotency_scope'
			""");
	}

	private String checkConstraintNames(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT GROUP_CONCAT(constraint_name ORDER BY constraint_name SEPARATOR ',')
			FROM information_schema.table_constraints
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_application_idempotencies'
			  AND constraint_type = 'CHECK'
			""")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String reservationForeignKey(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT CONCAT(column_name, ',', referenced_table_name, ',',
				referenced_column_name)
			FROM information_schema.key_column_usage
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_application_idempotencies'
			  AND constraint_name =
			      'fk_reservation_application_idempotencies_reservation'
			""")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String createdIndexColumns(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservation_application_idempotencies'
			  AND index_name = 'idx_reservation_application_idempotencies_created'
			""")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
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
