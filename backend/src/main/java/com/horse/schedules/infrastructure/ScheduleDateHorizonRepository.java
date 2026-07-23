package com.horse.schedules.infrastructure;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ScheduleDateHorizonRepository {

	private static final int DUPLICATE_KEY_ERROR_CODE = 1062;
	private static final String INSERT_SCHEDULE_DATE_SQL = """
		INSERT IGNORE INTO schedule_dates (
			schedule_date,
			status,
			applied_config_version
		) VALUES (?, 'NORMAL', ?)
		""";

	private final JdbcTemplate jdbcTemplate;

	public ScheduleDateHorizonRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public int insertMissingRange(LocalDate startDate, LocalDate endDate, long appliedConfigVersion) {
		final List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1)).toList();
		return jdbcTemplate.execute((ConnectionCallback<Integer>) connection ->
			insertMissingDates(connection, dates, appliedConfigVersion));
	}

	private int insertMissingDates(
		Connection connection,
		List<LocalDate> dates,
		long appliedConfigVersion
	) throws SQLException {
		int insertedCount = 0;
		try (PreparedStatement statement = connection.prepareStatement(INSERT_SCHEDULE_DATE_SQL)) {
			for (LocalDate scheduleDate : dates) {
				statement.setDate(1, Date.valueOf(scheduleDate));
				statement.setLong(2, appliedConfigVersion);
				insertedCount += statement.executeUpdate();
				assertOnlyDuplicateWarnings(statement.getWarnings());
			}
		}
		return insertedCount;
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
