package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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
class M31G02TimeSlotClosureRepairMigrationIntegrationTest {

	private static final String CASE_SENSITIVE_COLLATION = "utf8mb4_0900_as_cs";

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 대소문자_구분_DB의_잘못_완료된_휴강을_V32에서_복구한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_g02_case_sensitive");
		flyway(databaseUrl, "29").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			makeReservationStatusCaseSensitive(statement);
			insertScheduleDateAndTimeSlot(statement);
			insertActiveReservations(statement);
		}

		flyway(databaseUrl, "31").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(queryString(statement, "SELECT status FROM time_slot_closures"))
				.isEqualTo("COMPLETED");
			assertThat(count(statement, "time_slot_closure_impacts")).isZero();
		}

		final Flyway repairFlyway = flyway(databaseUrl, "32");
		repairFlyway.migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(queryString(statement, "SELECT status FROM time_slot_closures"))
				.isEqualTo("IN_PROGRESS");
			assertThat(queryNullableString(statement, """
				SELECT completed_by
				FROM time_slot_closures
				""")).isNull();
			assertThat(queryNullableString(statement, """
				SELECT completed_at
				FROM time_slot_closures
				""")).isNull();
			assertThat(count(statement, "time_slot_closure_impacts")).isEqualTo(3);
			assertThat(queryString(statement, """
				SELECT GROUP_CONCAT(reservation_status_at_start ORDER BY reservation_status_at_start)
				FROM time_slot_closure_impacts
				""")).isEqualTo("confirmed,pending_admin_approval,pending_payment");
		}
		assertThat(repairFlyway.validateWithResult().validationSuccessful).isTrue();
	}

	@Test
	void 활성_예약이_없는_완료_휴강은_V32에서_유지한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_g02_no_active");
		flyway(databaseUrl, "29").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			makeReservationStatusCaseSensitive(statement);
			insertScheduleDateAndTimeSlot(statement);
		}

		flyway(databaseUrl, "32").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(queryString(statement, "SELECT status FROM time_slot_closures"))
				.isEqualTo("COMPLETED");
			assertThat(queryString(statement, "SELECT completed_by FROM time_slot_closures"))
				.isEqualTo("migration:v30");
			assertThat(count(statement, "time_slot_closure_impacts")).isZero();
		}
	}

	@Test
	void 다른_활성_휴강이_있으면_V30_완료_이력을_다시_열지_않는다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_g02_existing_active");
		flyway(databaseUrl, "29").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			makeReservationStatusCaseSensitive(statement);
			insertScheduleDateAndTimeSlot(statement);
			insertActiveReservations(statement);
		}
		flyway(databaseUrl, "31").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				INSERT INTO time_slot_closures (
					time_slot_id, status, reason, started_by, started_at
				) VALUES (1, 'IN_PROGRESS', '운영 휴강', 'admin', NOW(6))
				""");
		}

		flyway(databaseUrl, "32").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(count(statement, "time_slot_closures")).isEqualTo(2);
			assertThat(queryString(statement, """
				SELECT status
				FROM time_slot_closures
				WHERE started_by = 'migration:v30'
				""")).isEqualTo("COMPLETED");
			assertThat(queryString(statement, """
				SELECT status
				FROM time_slot_closures
				WHERE started_by = 'admin'
				""")).isEqualTo("IN_PROGRESS");
			assertThat(count(statement, "time_slot_closure_impacts")).isZero();
		}
	}

	@Test
	void 기본_콜레이션의_정상_이관_결과를_V32가_유지한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_g02_default");
		flyway(databaseUrl, "29").migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			insertScheduleDateAndTimeSlot(statement);
			insertActiveReservations(statement);
		}

		flyway(databaseUrl, "32").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(queryString(statement, "SELECT status FROM time_slot_closures"))
				.isEqualTo("IN_PROGRESS");
			assertThat(count(statement, "time_slot_closures")).isOne();
			assertThat(count(statement, "time_slot_closure_impacts")).isEqualTo(3);
		}
	}

	@Test
	void 빈_스키마를_V32까지_생성하고_검증한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_g02_fresh");
		final Flyway freshFlyway = flyway(databaseUrl, "32");

		freshFlyway.migrate();

		assertThat(freshFlyway.info().current().getVersion().getVersion()).isEqualTo("32");
		assertThat(freshFlyway.validateWithResult().validationSuccessful).isTrue();
	}

	private void insertActiveReservations(Statement statement) throws Exception {
		statement.executeUpdate("""
			INSERT INTO members (auth_subject, name, phone, large_arena_allowed)
			VALUES
				('g02-repair-member-1', '보정 회원1', '010-1234-5671', FALSE),
				('g02-repair-member-2', '보정 회원2', '010-1234-5672', FALSE),
				('g02-repair-member-3', '보정 회원3', '010-1234-5673', FALSE)
			""");
		statement.executeUpdate("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, held_count
			) VALUES (1, 'general', 10, 10, 1)
			""");
		statement.executeUpdate("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, coupon_id, payment_due_at, approval_requested_at,
				admin_confirmed_at
			) VALUES
				(
					1, 'FIRST_RIDE', '2026-08-02', '09:00:00', '09:45:00',
					'pending_admin_approval', 'coupon', 1, NULL,
					'2026-08-01 09:00:00', NULL
				),
				(
					2, 'FIRST_RIDE', '2026-08-02', '09:00:00', '09:45:00',
					'pending_payment', 'single_payment', NULL,
					'2026-08-01 12:00:00', '2026-08-01 09:00:00', NULL
				),
				(
					3, 'FIRST_RIDE', '2026-08-02', '09:00:00', '09:45:00',
					'confirmed', 'single_payment', NULL, NULL,
					'2026-08-01 09:00:00', '2026-08-01 10:00:00'
				)
			""");
	}

	private void makeReservationStatusCaseSensitive(Statement statement) throws Exception {
		statement.executeUpdate("""
			ALTER TABLE reservations
			MODIFY COLUMN status VARCHAR(40)
				CHARACTER SET utf8mb4
				COLLATE utf8mb4_0900_as_cs
				NOT NULL
			""");
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

	private String queryNullableString(Statement statement, String sql) throws Exception {
		return queryString(statement, sql);
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
