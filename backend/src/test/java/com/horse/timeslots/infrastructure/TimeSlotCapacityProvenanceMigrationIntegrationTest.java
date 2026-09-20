package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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
class TimeSlotCapacityProvenanceMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "41";
	private static final String CURRENT_VERSION = "42";
	private static final String CLASS_CAPACITIES = """
		{"FIRST_RIDE":2,"ROUND_BEGINNER":2,"ROUND_TROT":2,
		"LARGE_ARENA_BEGINNER":3,"LARGE_ARENA_TROT":3,
		"CANTER_BEGINNER":3,"CANTER":3,"DRESSAGE":1,"JUMPING":1}
		""";

	private final MySQLContainer mysqlContainer;

	@Autowired
	TimeSlotCapacityProvenanceMigrationIntegrationTest(MySQLContainer mysqlContainer) {
		this.mysqlContainer = mysqlContainer;
	}

	@Test
	void V41_기존_정원을_보존하며_TEMPLATE은_false_MANUAL은_true로_이관한다() throws Exception {
		final String databaseUrl = createDatabase("horse_time_slot_provenance_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		final CapacitySnapshot templateBefore;
		final CapacitySnapshot manualBefore;
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO regular_schedule_templates (
					day_of_week, start_time, end_time, total_capacity, round_arena_capacity,
					class_capacity_json, created_by, updated_by
				) VALUES ('MONDAY', '09:00:00', '09:45:00', 8, 4, '%s', 'migration', 'migration')
				""".formatted(CLASS_CAPACITIES));
			statement.execute("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, end_time, source, template_id,
					total_capacity, round_arena_capacity, class_capacity_json
				)
				SELECT '2026-10-05', '09:00:00', '09:45:00', 'TEMPLATE', id, 6, 3, '%s'
				FROM regular_schedule_templates WHERE day_of_week = 'MONDAY'
				""".formatted(CLASS_CAPACITIES));
			statement.execute("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, end_time, source,
					total_capacity, round_arena_capacity, class_capacity_json
				) VALUES ('2026-10-05', '10:00:00', '10:45:00', 'MANUAL', 4, 2, '%s')
				""".formatted(CLASS_CAPACITIES));
			templateBefore = capacitySnapshot(statement, "TEMPLATE");
			manualBefore = capacitySnapshot(statement, "MANUAL");
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(capacitySnapshot(statement, "TEMPLATE")).isEqualTo(templateBefore);
			assertThat(capacitySnapshot(statement, "MANUAL")).isEqualTo(manualBefore);
			assertThat(capacityOverridden(statement, "TEMPLATE")).isFalse();
			assertThat(capacityOverridden(statement, "MANUAL")).isTrue();
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	private String createDatabase(String databaseName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			rootJdbcUrl("mysql"), "root", mysqlContainer.getPassword());
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

	private CapacitySnapshot capacitySnapshot(Statement statement, String source) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT total_capacity, round_arena_capacity, class_capacity_json
			FROM time_slot_capacities
			WHERE source = '%s'
			""".formatted(source))) {
			assertThat(resultSet.next()).isTrue();
			return new CapacitySnapshot(resultSet.getInt(1), resultSet.getInt(2), resultSet.getString(3));
		}
	}

	private boolean capacityOverridden(Statement statement, String source) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT capacity_overridden
			FROM time_slot_capacities
			WHERE source = '%s'
			""".formatted(source))) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getBoolean(1);
		}
	}

	private String currentMigrationVersion(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT version FROM flyway_schema_history
			WHERE success = TRUE
			ORDER BY installed_rank DESC
			LIMIT 1
			""")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}

	private record CapacitySnapshot(int totalCapacity, int roundArenaCapacity, String classCapacities) {
	}
}
