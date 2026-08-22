package com.horse.schedules.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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
class M31R06TimeSlotLockIndexMigrationIntegrationTest {

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

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 기존_시간대를_보존하며_날짜_ID_잠금_인덱스를_추가한다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r06_lock_index");
		flyway(databaseUrl, "26").migrate();
		insertExistingTimeSlots(databaseUrl);

		flyway(databaseUrl, "27").migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(countTimeSlots(statement)).isEqualTo(2);
			assertThat(indexColumns(statement))
				.containsExactly("lesson_date", "id");
			assertThat(explain(statement))
				.contains("\"key\": \"idx_time_slot_capacities_lesson_date_id\"");
		}
	}

	private void insertExistingTimeSlots(String databaseUrl) throws Exception {
		try (Connection connection = connection(databaseUrl);
			var statement = connection.prepareStatement("""
				INSERT INTO time_slot_capacities (
					lesson_date,
					start_time,
					end_time,
					source,
					total_capacity,
					round_arena_capacity,
					class_capacity_json
				) VALUES ('2026-08-05', ?, ?, 'MANUAL', 8, 4, ?)
				""")) {
			insertTimeSlot(statement, "09:00:00", "09:45:00");
			insertTimeSlot(statement, "10:00:00", "10:45:00");
		}
	}

	private void insertTimeSlot(
		java.sql.PreparedStatement statement,
		String startTime,
		String endTime
	) throws Exception {
		statement.setString(1, startTime);
		statement.setString(2, endTime);
		statement.setString(3, CLASS_CAPACITIES_JSON);
		statement.executeUpdate();
	}

	private long countTimeSlots(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery(
			"SELECT COUNT(*) FROM time_slot_capacities")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private java.util.List<String> indexColumns(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT column_name
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'time_slot_capacities'
			  AND index_name = 'idx_time_slot_capacities_lesson_date_id'
			ORDER BY seq_in_index
			""")) {
			final java.util.ArrayList<String> columns = new java.util.ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}
			return columns;
		}
	}

	private String explain(Statement statement) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			EXPLAIN FORMAT=JSON
			SELECT *
			FROM time_slot_capacities
			FORCE INDEX (idx_time_slot_capacities_lesson_date_id)
			WHERE lesson_date = '2026-08-05'
			ORDER BY id
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
		return mysqlContainer.getJdbcUrl().replace(
			mysqlContainer.getDatabaseName(),
			databaseName);
	}
}
