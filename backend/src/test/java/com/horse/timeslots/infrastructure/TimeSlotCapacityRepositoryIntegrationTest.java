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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class TimeSlotCapacityRepositoryIntegrationTest {

	private static final String CLASS_CAPACITIES = """
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
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 빈_DB에_시간대_정원_스키마를_적용한다() {
		final Integer migrationCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM flyway_schema_history
			WHERE success = TRUE AND version IN ('1', '2', '3')
			""", Integer.class);

		insertTimeSlot("2026-08-01", "09:00:00", 8, 4, CLASS_CAPACITIES);

		assertThat(migrationCount).isEqualTo(3);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT is_closed FROM time_slot_capacities WHERE lesson_date = '2026-08-01'",
			Boolean.class)).isFalse();
	}

	@Test
	void 기존_마이그레이션이_적용된_DB에_시간대_정원_스키마를_추가한다() throws Exception {
		final String databaseName = "horse_existing_migrations";
		final String databaseUrl = createDatabase(databaseName);
		final Flyway existingFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("2"));

		existingFlyway.migrate();

		assertThat(existingFlyway.info().current().getVersion().getVersion()).isEqualTo("2");

		final Flyway latestFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("3"));
		latestFlyway.migrate();

		assertThat(latestFlyway.info().current().getVersion().getVersion()).isEqualTo("3");
		try (Connection connection = DriverManager.getConnection(
			databaseUrl, "root", mysqlContainer.getPassword());
			Statement statement = connection.createStatement()) {
			assertThat(statement.executeQuery("SELECT COUNT(*) FROM time_slot_capacities").next()).isTrue();
		}
	}

	@Test
	void 같은_날짜와_시작_시각의_시간대를_중복_저장할_수_없다() {
		insertTimeSlot("2026-08-02", "10:00:00", 8, 4, CLASS_CAPACITIES);

		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-02", "10:00:00", 8, 4, CLASS_CAPACITIES))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 물리_제한을_넘는_정원은_저장할_수_없다() {
		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-03", "09:00:00", 9, 4, CLASS_CAPACITIES))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-03", "10:00:00", 8, 5, CLASS_CAPACITIES))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-03", "11:00:00", 3, 4, CLASS_CAPACITIES))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-03", "12:00:00", -1, 0, CLASS_CAPACITIES))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 유효하지_않은_클래스_정원_JSON은_저장할_수_없다() {
		final String missingClass = """
			{
			  "FIRST_RIDE": 2,
			  "ROUND_BEGINNER": 2,
			  "ROUND_TROT": 2,
			  "LARGE_ARENA_BEGINNER": 3,
			  "LARGE_ARENA_TROT": 3,
			  "DRESSAGE": 1
			}
			""";
		final String negativeClassCapacity = CLASS_CAPACITIES.replace("\"JUMPING\": 1", "\"JUMPING\": -1");

		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-04", "09:00:00", 8, 4, missingClass))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-04", "10:00:00", 8, 4, negativeClassCapacity))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertTimeSlot(
			"2026-08-04", "11:00:00", 8, 4, "not-json"))
			.isInstanceOf(DataAccessException.class);
	}

	private void insertTimeSlot(
		String lessonDate,
		String startTime,
		int totalCapacity,
		int roundArenaCapacity,
		String classCapacities
	) {
		jdbcTemplate.update("""
			INSERT INTO time_slot_capacities (
				lesson_date,
				start_time,
				total_capacity,
				round_arena_capacity,
				class_capacity_json
			) VALUES (?, ?, ?, ?, ?)
			""", lessonDate, startTime, totalCapacity, roundArenaCapacity, classCapacities);
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

	private Flyway flyway(String databaseUrl, MigrationVersion target) {
		var configuration = Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

	private String rootJdbcUrl(String databaseName) {
		return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul"
			.formatted(mysqlContainer.getHost(), mysqlContainer.getMappedPort(3306), databaseName);
	}

}
