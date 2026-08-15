package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
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
class M31R10TimeSlotClosureMigrationIntegrationTest {

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V29에서_기존_관리자_마감을_휴강_작업으로_이관한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r10_upgrade");
		flyway(databaseUrl, "29").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			insertScheduleDateAndTimeSlot(statement);
			insertActiveReservation(statement);
		}

		flyway(databaseUrl, "31").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(count(statement, "time_slot_closures")).isOne();
			assertThat(queryString(statement, "SELECT status FROM time_slot_closures"))
				.isEqualTo("IN_PROGRESS");
			assertThat(count(statement, "time_slot_closure_impacts")).isOne();
			assertThat(queryString(
				statement,
				"SELECT reservation_status_at_start FROM time_slot_closure_impacts"))
				.isEqualTo("pending_payment");
		}
	}

	private void insertActiveReservation(Statement statement) throws Exception {
		statement.executeUpdate("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES ('migration-closure-member', '이관 회원', '010-1234-5678', FALSE)
			""");
		statement.executeUpdate("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at
			) VALUES (
				1, 'FIRST_RIDE', '2026-08-02', '09:00:00', '09:45:00',
				'pending_payment', 'single_payment',
				'2026-08-01 12:00:00', '2026-08-01 09:00:00'
			)
			""");
	}

	@Test
	void 동일_시간대에는_IN_PROGRESS_휴강을_하나만_허용하고_종료_이력은_보존한다()
		throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r10_guard");
		flyway(databaseUrl, "31").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			insertScheduleDateAndTimeSlot(statement);
			statement.executeUpdate("""
				INSERT INTO time_slot_closures (
					time_slot_id, status, reason, started_by, started_at
				) VALUES (1, 'IN_PROGRESS', '우천', 'admin', NOW(6))
				""");
			assertThatThrownBy(() -> statement.executeUpdate("""
				INSERT INTO time_slot_closures (
					time_slot_id, status, reason, started_by, started_at
				) VALUES (1, 'IN_PROGRESS', '점검', 'admin', NOW(6))
				""")).isInstanceOf(java.sql.SQLException.class);
			statement.executeUpdate("""
				UPDATE time_slot_closures
				SET status = 'COMPLETED', completed_by = 'admin', completed_at = NOW(6)
				WHERE id = 1
				""");
			statement.executeUpdate("""
				INSERT INTO time_slot_closures (
					time_slot_id, status, reason, started_by, started_at
				) VALUES (1, 'IN_PROGRESS', '점검', 'admin', NOW(6))
				""");
			assertThat(count(statement, "time_slot_closures")).isEqualTo(2);
		}
	}

	private void insertScheduleDateAndTimeSlot(Statement statement) throws Exception {
		statement.executeUpdate("""
			INSERT IGNORE INTO schedule_dates (schedule_date, status, applied_config_version)
			VALUES ('2026-08-02', 'NORMAL', 1)
			""");
		statement.executeUpdate("""
			INSERT INTO time_slot_capacities (
				lesson_date, start_time, end_time, source, total_capacity,
				round_arena_capacity, class_capacity_json, admin_closed
			) VALUES (
				'2026-08-02', '09:00:00', '09:45:00', 'MANUAL', 8, 4,
				JSON_OBJECT(
					'FIRST_RIDE', 2, 'ROUND_BEGINNER', 2, 'ROUND_TROT', 2,
					'LARGE_ARENA_BEGINNER', 3, 'LARGE_ARENA_TROT', 3,
					'CANTER_BEGINNER', 3, 'CANTER', 3,
					'DRESSAGE', 1, 'JUMPING', 1
				), TRUE
			)
			""");
	}

	private int count(Statement statement, String tableName) throws Exception {
		try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getInt(1);
		}
	}

	private String queryString(Statement statement, String sql) throws Exception {
		try (var resultSet = statement.executeQuery(sql)) {
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
