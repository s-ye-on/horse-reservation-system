package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
class M31R01LessonIntervalMigrationIntegrationTest {

	private static final String VALID_CLASS_CAPACITY_JSON = """
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
	MySQLContainer mysqlContainer;

	@Test
	void 기존_수업과_예약에_종료_시각을_채우고_수동_출처로_이관한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r01_valid");
		flyway(databaseUrl, "15").migrate();
		insertTimeSlot(databaseUrl, "09:00:00");
		insertPendingPayment(databaseUrl, "migration-valid-member", "09:00:00");

		flyway(databaseUrl, "16").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			try (ResultSet resultSet = statement.executeQuery("""
				SELECT start_time, end_time, source
				FROM time_slot_capacities
				""")) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString("start_time")).isEqualTo("09:00:00");
				assertThat(resultSet.getString("end_time")).isEqualTo("09:45:00");
				assertThat(resultSet.getString("source")).isEqualTo("MANUAL");
			}
			try (ResultSet resultSet = statement.executeQuery("""
				SELECT start_time, end_time
				FROM reservations
				""")) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getString("start_time")).isEqualTo("09:00:00");
				assertThat(resultSet.getString("end_time")).isEqualTo("09:45:00");
			}
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
	}

	@Test
	void 자정에_닿거나_넘는_기존_수업이_있으면_이관을_중단한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r01_midnight");
		flyway(databaseUrl, "15").migrate();
		insertTimeSlot(databaseUrl, "23:15:00");

		assertThatThrownBy(() -> flyway(databaseUrl, "16").migrate())
			.isInstanceOf(FlywayException.class)
			.hasMessageContaining("M31-R01 preflight");

		assertPreflightFailureDidNotAddColumns(databaseUrl);
	}

	@Test
	void 기존_활성_예약_구간이_겹치면_이관을_중단한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r01_overlap");
		flyway(databaseUrl, "15").migrate();
		insertPendingPayment(databaseUrl, "migration-overlap-member", "09:00:00");
		insertPendingPayment(databaseUrl, "migration-overlap-member", "09:30:00");

		assertThatThrownBy(() -> flyway(databaseUrl, "16").migrate())
			.isInstanceOf(FlywayException.class)
			.hasMessageContaining("M31-R01 preflight");

		assertPreflightFailureDidNotAddColumns(databaseUrl);
	}

	private void assertPreflightFailureDidNotAddColumns(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("""
				SELECT COUNT(*) AS added_columns
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
					AND (
						(table_name = 'time_slot_capacities' AND column_name IN ('end_time', 'source'))
						OR (table_name = 'reservations' AND column_name = 'end_time')
					)
				""")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt("added_columns")).isZero();
		}
	}

	private void insertTimeSlot(String databaseUrl, String startTime) throws Exception {
		try (Connection connection = connection(databaseUrl);
			var statement = connection.prepareStatement("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, total_capacity, round_arena_capacity,
					class_capacity_json, is_closed
				) VALUES ('2026-08-01', ?, 8, 4, ?, FALSE)
				""")) {
			statement.setString(1, startTime);
			statement.setString(2, VALID_CLASS_CAPACITY_JSON);
			statement.executeUpdate();
		}
	}

	private void insertPendingPayment(
		String databaseUrl,
		String authSubject,
		String startTime
	) throws Exception {
		try (Connection connection = connection(databaseUrl);
			var memberStatement = connection.prepareStatement("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES (?, '이관 회원', '010-0000-0000')
				ON DUPLICATE KEY UPDATE auth_subject = VALUES(auth_subject)
				""");
			var reservationStatement = connection.prepareStatement("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status, payment_source,
					payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-01', ?, 'pending_payment',
					'single_payment', '2026-07-31 12:00:00', '2026-07-31 10:00:00'
				FROM members
				WHERE auth_subject = ?
				""")) {
			memberStatement.setString(1, authSubject);
			memberStatement.executeUpdate();
			reservationStatement.setString(1, startTime);
			reservationStatement.setString(2, authSubject);
			reservationStatement.executeUpdate();
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
