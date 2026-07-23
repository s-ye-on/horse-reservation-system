package db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public final class V17__harden_r01_lesson_intervals extends BaseJavaMigration {

	private static final String PREFLIGHT_RESOURCE = "db/preflight/m31-r02-preflight.sql";

	@Override
	public boolean canExecuteInTransaction() {
		return false;
	}

	@Override
	public void migrate(Context context) throws Exception {
		final Connection connection = context.getConnection();
		final List<PreflightIssue> issues = findPreflightIssues(connection);
		if (!issues.isEmpty()) {
			throw new FlywayException(formatPreflightFailure(issues));
		}

		strengthenTimeSlotInterval(connection);
		strengthenReservationInterval(connection);
	}

	private List<PreflightIssue> findPreflightIssues(Connection connection) throws SQLException {
		final List<PreflightIssue> issues = new ArrayList<>();
		try (Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery(loadPreflightSql())) {
			while (resultSet.next()) {
				issues.add(new PreflightIssue(
					resultSet.getString("issue_type"),
					resultSet.getString("table_name"),
					resultSet.getLong("record_id"),
					resultSet.getString("lesson_date"),
					resultSet.getString("start_time"),
					resultSet.getString("end_time"),
					resultSet.getString("details"),
					resultSet.getLong("issue_type_count"),
					resultSet.getLong("total_issue_count")));
			}
		}
		return issues;
	}

	private String loadPreflightSql() {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(PREFLIGHT_RESOURCE)) {
			if (input == null) {
				throw new FlywayException("M31-R02 preflight resource is missing: " + PREFLIGHT_RESOURCE);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new FlywayException("M31-R02 preflight resource cannot be read", exception);
		}
	}

	private String formatPreflightFailure(List<PreflightIssue> issues) {
		final StringBuilder message = new StringBuilder()
			.append("M31-R02 preflight failed before DDL; no data was changed.\n")
			.append("issue_type|table|id|lesson_date|start_time|end_time|details")
			.append("|issue_type_count|total_issue_count\n");
		for (PreflightIssue issue : issues) {
			message.append(issue.issueType()).append('|')
				.append(issue.tableName()).append('|')
				.append(issue.recordId()).append('|')
				.append(issue.lessonDate()).append('|')
				.append(issue.startTime()).append('|')
				.append(issue.endTime()).append('|')
				.append(issue.details()).append('|')
				.append(issue.issueTypeCount()).append('|')
				.append(issue.totalIssueCount()).append('\n');
		}
		return message.toString();
	}

	private void strengthenTimeSlotInterval(Connection connection) throws SQLException {
		execute(connection, """
			ALTER TABLE time_slot_capacities
				MODIFY COLUMN end_time TIME NOT NULL
					DEFAULT (ADDTIME(start_time, '00:45:00')),
				DROP CHECK chk_time_slot_capacities_lesson_interval,
				ADD CONSTRAINT chk_time_slot_capacities_lesson_interval CHECK (
					start_time < end_time
					AND TIME_TO_SEC(end_time) - TIME_TO_SEC(start_time) = 2700
					AND TIME_TO_SEC(start_time) >= 0
					AND TIME_TO_SEC(end_time) < 86400
				)
			""");
	}

	private void strengthenReservationInterval(Connection connection) throws SQLException {
		execute(connection, """
			ALTER TABLE reservations
				MODIFY COLUMN end_time TIME NOT NULL
					DEFAULT (ADDTIME(start_time, '00:45:00')),
				DROP CHECK chk_reservations_lesson_interval,
				ADD CONSTRAINT chk_reservations_lesson_interval CHECK (
					start_time < end_time
					AND TIME_TO_SEC(end_time) - TIME_TO_SEC(start_time) = 2700
					AND TIME_TO_SEC(start_time) >= 0
					AND TIME_TO_SEC(end_time) < 86400
				)
			""");
	}

	private void execute(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private record PreflightIssue(
		String issueType,
		String tableName,
		long recordId,
		String lessonDate,
		String startTime,
		String endTime,
		String details,
		long issueTypeCount,
		long totalIssueCount
	) {
	}
}
