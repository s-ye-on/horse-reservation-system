package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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
class M31R08ReservationOverlapIndexMigrationIntegrationTest {

	private static final String INDEX_NAME = "idx_reservations_member_date_active_interval";

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 기존_예약을_보존하며_overlap_잠금_인덱스를_추가한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r08_overlap_index");
		flyway(databaseUrl, "27").migrate();
		insertExistingReservation(databaseUrl);

		flyway(databaseUrl, "29").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(reservationCount(statement)).isOne();
			assertThat(indexColumns(statement)).containsExactly(
				"member_id",
				"lesson_date",
				"active_slot_guard",
				"start_time",
				"id",
				"end_time");
			assertThat(explainAnalyze(statement)).contains(INDEX_NAME);
			assertThat(explainJson(statement))
				.contains("\"access_type\": \"range\"")
				.contains("\"key\": \"" + INDEX_NAME + "\"")
				.contains("\"member_id\"")
				.contains("\"lesson_date\"")
				.contains("\"active_slot_guard\"")
				.contains("\"start_time\"");
		}
	}

	@Test
	void V27에_활성_interval_overlap이_있으면_V29_migration을_중단한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r08_overlap_preflight");
		flyway(databaseUrl, "27").migrate();
		insertOverlappingReservations(databaseUrl);

		assertThatThrownBy(() -> flyway(databaseUrl, "29").migrate())
			.isInstanceOf(FlywayException.class)
			.hasMessageContaining("M31-R08 preflight found 1 active reservation overlap");
	}

	private void insertExistingReservation(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('m31-r08-migration-member', '이관 회원', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, end_time, status,
					payment_source, payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-05', '09:00:00', '09:45:00',
					'pending_payment', 'single_payment', '2026-08-04 12:00:00',
					'2026-08-04 10:00:00'
				FROM members
				WHERE auth_subject = 'm31-r08-migration-member'
				""");
		}
	}

	private void insertOverlappingReservations(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('m31-r08-overlap-member', '겹침 회원', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, end_time, status,
					payment_source, payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-05', '09:00:00', '09:45:00',
					'pending_payment', 'single_payment', '2026-08-04 12:00:00',
					'2026-08-04 10:00:00'
				FROM members
				WHERE auth_subject = 'm31-r08-overlap-member'
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, end_time, status,
					payment_source, payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-05', '09:30:00', '10:15:00',
					'pending_payment', 'single_payment', '2026-08-04 12:00:00',
					'2026-08-04 10:01:00'
				FROM members
				WHERE auth_subject = 'm31-r08-overlap-member'
				""");
		}
	}

	private long reservationCount(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM reservations")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private List<String> indexColumns(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT column_name
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservations'
			  AND index_name = 'idx_reservations_member_date_active_interval'
			ORDER BY seq_in_index
			""")) {
			final List<String> columns = new ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}
			return columns;
		}
	}

	private String explainAnalyze(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			EXPLAIN ANALYZE
			SELECT *
			FROM reservations
			FORCE INDEX (idx_reservations_member_date_active_interval)
			WHERE member_id = (
				SELECT id FROM members WHERE auth_subject = 'm31-r08-migration-member'
			)
			  AND lesson_date = '2026-08-05'
			  AND active_slot_guard = 1
			  AND start_time < '09:45:00'
			  AND end_time > '09:00:00'
			ORDER BY start_time, id
			FOR UPDATE
			""")) {
			final StringBuilder plan = new StringBuilder();
			while (resultSet.next()) {
				plan.append(resultSet.getString(1));
			}
			return plan.toString();
		}
	}

	private String explainJson(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			EXPLAIN FORMAT=JSON
			SELECT *
			FROM reservations
			FORCE INDEX (idx_reservations_member_date_active_interval)
			WHERE member_id = (
				SELECT id FROM members WHERE auth_subject = 'm31-r08-migration-member'
			)
			  AND lesson_date = '2026-08-05'
			  AND active_slot_guard = 1
			  AND start_time < '09:45:00'
			  AND end_time > '09:00:00'
			ORDER BY start_time, id
			FOR UPDATE
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
