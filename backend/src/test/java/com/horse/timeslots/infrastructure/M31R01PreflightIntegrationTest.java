package com.horse.timeslots.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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
class M31R01PreflightIntegrationTest {

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
	void 사전_점검은_문제_유형과_충돌_ID와_건수를_출력하고_데이터를_변경하지_않는다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r01_preflight");
		flyway(databaseUrl).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("ALTER TABLE time_slot_capacities MODIFY start_time TIME NULL");
			statement.execute("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, total_capacity, round_arena_capacity,
					class_capacity_json, is_closed
				) VALUES
					('2026-08-01', NULL, 8, 4, '%s', FALSE),
					('2026-08-02', '23:15:00', 8, 4, '%s', FALSE),
					('2026-08-03', '25:00:00', 8, 4, '%s', FALSE)
				""".formatted(
					VALID_CLASS_CAPACITY_JSON,
					VALID_CLASS_CAPACITY_JSON,
					VALID_CLASS_CAPACITY_JSON));
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('preflight-overlap-member', '사전 점검 회원', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status, payment_source,
					payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-01', '09:00:00', 'pending_payment',
					'single_payment', '2026-07-31 12:00:00', '2026-07-31 10:00:00'
				FROM members WHERE auth_subject = 'preflight-overlap-member'
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status, payment_source,
					payment_due_at, approval_requested_at, admin_confirmed_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-01', '09:30:00', 'confirmed',
					'single_payment', '2026-07-31 12:00:00', '2026-07-31 10:10:00',
					'2026-07-31 10:20:00'
				FROM members WHERE auth_subject = 'preflight-overlap-member'
				""");
		}

		final int timeSlotCountBefore = count(databaseUrl, "time_slot_capacities");
		final int reservationCountBefore = count(databaseUrl, "reservations");
		final List<PreflightIssue> issues = executePreflight(databaseUrl);

		assertThat(issues)
			.extracting(PreflightIssue::issueType)
			.contains(
				"NULL_START_TIME",
				"INVALID_START_TIME",
				"LESSON_INTERVAL_REACHES_OR_CROSSES_MIDNIGHT",
				"OVERLAPPING_ACTIVE_RESERVATIONS");
		assertThat(issues)
			.filteredOn(issue -> issue.issueType().equals("OVERLAPPING_ACTIVE_RESERVATIONS"))
			.singleElement()
			.satisfies(issue -> {
				assertThat(issue.tableName()).isEqualTo("reservations");
				assertThat(issue.conflictingReservationIds()).contains(",");
				assertThat(issue.issueTypeCount()).isEqualTo(1);
				assertThat(issue.totalIssueCount()).isEqualTo(4);
			});
		assertThat(count(databaseUrl, "time_slot_capacities")).isEqualTo(timeSlotCountBefore);
		assertThat(count(databaseUrl, "reservations")).isEqualTo(reservationCountBefore);
	}

	@Test
	void 정상_데이터의_사전_점검은_문제를_출력하지_않는다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_r01_preflight_clean");
		flyway(databaseUrl).migrate();

		assertThat(executePreflight(databaseUrl)).isEmpty();
	}

	private List<PreflightIssue> executePreflight(String databaseUrl) throws Exception {
		final String sql = Files.readString(resolvePreflightSqlPath()).strip();
		final List<PreflightIssue> issues = new ArrayList<>();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(sql)) {
			while (resultSet.next()) {
				issues.add(new PreflightIssue(
					resultSet.getString("issue_type"),
					resultSet.getString("table_name"),
					resultSet.getString("conflicting_reservation_ids"),
					resultSet.getInt("issue_type_count"),
					resultSet.getInt("total_issue_count")));
			}
		}
		return issues;
	}

	private Path resolvePreflightSqlPath() {
		final Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		final Path workspacePath = workingDirectory.resolve("scripts/sql/m31-r01-preflight.sql");
		if (Files.exists(workspacePath)) {
			return workspacePath;
		}
		return workingDirectory.getParent().resolve("scripts/sql/m31-r01-preflight.sql");
	}

	private int count(String databaseUrl, String tableName) throws Exception {
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getInt(1);
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

	private Flyway flyway(String databaseUrl) {
		return Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion("15"))
			.load();
	}

	private Connection connection(String databaseUrl) throws Exception {
		return DriverManager.getConnection(databaseUrl, "root", mysqlContainer.getPassword());
	}

	private String rootJdbcUrl(String databaseName) {
		return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul"
			.formatted(mysqlContainer.getHost(), mysqlContainer.getMappedPort(3306), databaseName);
	}

	private record PreflightIssue(
		String issueType,
		String tableName,
		String conflictingReservationIds,
		int issueTypeCount,
		int totalIssueCount
	) {
	}
}
