package db.migration;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLWarning;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public final class V25__initialize_schedule_date_horizon extends BaseJavaMigration {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HORIZON_MONTHS = 3;
	private static final int DUPLICATE_KEY_ERROR_CODE = 1062;
	private static final String INSERT_SCHEDULE_DATE_SQL = """
		INSERT IGNORE INTO schedule_dates (
			schedule_date,
			status,
			applied_config_version
		) VALUES (?, 'NORMAL', ?)
		""";

	@Override
	public void migrate(Context context) throws Exception {
		final Connection connection = context.getConnection();
		final long activeVersion = findActiveVersion(connection);
		final Set<LocalDate> scheduleDates = findExistingScheduleDates(connection);
		final LocalDate today = LocalDate.now(SEOUL_ZONE);
		today.datesUntil(today.plusMonths(HORIZON_MONTHS).plusDays(1))
			.forEach(scheduleDates::add);
		insertMissingScheduleDates(connection, scheduleDates, activeVersion);
	}

	private long findActiveVersion(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("""
				SELECT active_version
				FROM schedule_config_guard
				WHERE id = 1
				""")) {
			if (!resultSet.next()) {
				throw new SQLException("ScheduleConfigGuard singleton is missing");
			}
			return resultSet.getLong("active_version");
		}
	}

	private Set<LocalDate> findExistingScheduleDates(Connection connection) throws SQLException {
		final Set<LocalDate> scheduleDates = new TreeSet<>();
		try (Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("""
				SELECT lesson_date
				FROM time_slot_capacities
				UNION
				SELECT lesson_date
				FROM reservations
				""")) {
			while (resultSet.next()) {
				scheduleDates.add(resultSet.getDate("lesson_date").toLocalDate());
			}
		}
		return scheduleDates;
	}

	private void insertMissingScheduleDates(
		Connection connection,
		Set<LocalDate> scheduleDates,
		long activeVersion
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(INSERT_SCHEDULE_DATE_SQL)) {
			for (LocalDate scheduleDate : scheduleDates) {
				statement.setDate(1, Date.valueOf(scheduleDate));
				statement.setLong(2, activeVersion);
				statement.executeUpdate();
				assertOnlyDuplicateWarnings(statement.getWarnings());
			}
		}
	}

	private void assertOnlyDuplicateWarnings(SQLWarning warning) throws SQLException {
		SQLWarning current = warning;
		while (current != null) {
			if (current.getErrorCode() != DUPLICATE_KEY_ERROR_CODE) {
				throw new SQLException(
					"ScheduleDate horizon insert warning: " + current.getMessage(),
					current.getSQLState(),
					current.getErrorCode());
			}
			current = current.getNextWarning();
		}
	}
}
