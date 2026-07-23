package com.horse.schedules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

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
class M31R03ScheduleDateHorizonMigrationIntegrationTest {

	private static final String CLASS_CAPACITIES_JSON = """
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
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 기존_일정_날짜와_양끝을_포함한_최초_horizon을_이관한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r03_horizon");
		flyway(databaseUrl, "24").migrate();
		final LocalDate today = LocalDate.now(SEOUL_ZONE);
		final LocalDate oldTimeSlotDate = today.minusYears(2);
		final LocalDate oldReservationDate = today.minusYears(1);
		insertExistingScheduleData(databaseUrl, oldTimeSlotDate, oldReservationDate);
		updateActiveVersion(databaseUrl, 7L);

		flyway(databaseUrl, "25").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			final long horizonDayCount = ChronoUnit.DAYS.between(today, today.plusMonths(3)) + 1;
			assertThat(countScheduleDates(statement)).isEqualTo(horizonDayCount + 2);
			assertScheduleDate(statement, oldTimeSlotDate, 7L);
			assertScheduleDate(statement, oldReservationDate, 7L);
			assertScheduleDate(statement, today, 7L);
			assertScheduleDate(statement, today.plusMonths(3), 7L);
			assertThat(countTimeSlots(statement)).isOne();
		}
	}

	private void insertExistingScheduleData(
		String databaseUrl,
		LocalDate timeSlotDate,
		LocalDate reservationDate
	) throws Exception {
		try (Connection connection = connection(databaseUrl);
			var timeSlot = connection.prepareStatement("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, total_capacity,
					round_arena_capacity, class_capacity_json
				) VALUES (?, '09:00:00', 8, 4, ?)
				""");
			var reservation = connection.prepareStatement("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status,
					payment_source, payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', ?, '10:00:00', 'pending_payment',
					'single_payment', '2026-07-31 12:00:00', '2026-07-31 10:00:00'
				FROM members
				WHERE auth_subject = 'm31-r03-member'
				""")) {
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("""
					INSERT INTO members (auth_subject, name, phone)
					VALUES ('m31-r03-member', '이관 회원', '010-0000-0000')
					""");
			}
			timeSlot.setDate(1, java.sql.Date.valueOf(timeSlotDate));
			timeSlot.setString(2, CLASS_CAPACITIES_JSON);
			timeSlot.executeUpdate();
			reservation.setDate(1, java.sql.Date.valueOf(reservationDate));
			reservation.executeUpdate();
		}
	}

	private void updateActiveVersion(String databaseUrl, long activeVersion) throws Exception {
		try (Connection connection = connection(databaseUrl);
			var statement = connection.prepareStatement("""
				UPDATE schedule_config_guard
				SET active_version = ?
				WHERE id = 1
				""")) {
			statement.setLong(1, activeVersion);
			statement.executeUpdate();
		}
	}

	private void assertScheduleDate(Statement statement, LocalDate scheduleDate, long appliedVersion)
		throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT status, applied_config_version
			FROM schedule_dates
			WHERE schedule_date = '%s'
			""".formatted(scheduleDate))) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("status")).isEqualTo("NORMAL");
			assertThat(resultSet.getLong("applied_config_version")).isEqualTo(appliedVersion);
		}
	}

	private long countScheduleDates(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM schedule_dates")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private long countTimeSlots(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM time_slot_capacities")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
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
