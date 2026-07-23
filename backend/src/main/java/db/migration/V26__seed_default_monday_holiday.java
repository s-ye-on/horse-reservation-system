package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public final class V26__seed_default_monday_holiday extends BaseJavaMigration {

	private static final LocalDate DEFAULT_EFFECTIVE_FROM = LocalDate.of(1970, 1, 1);
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int HORIZON_MONTHS = 3;
	private static final String ACTOR = "system:migration:v26";
	private static final String REASON = "기본 월요일 정기 휴일";

	@Override
	public void migrate(Context context) throws Exception {
		final Connection connection = context.getConnection();
		if (hasActiveMondayRule(connection)) {
			return;
		}
		final long activeVersion = lockActiveConfigVersion(connection);
		final long holidayId = insertDefaultHoliday(connection);
		final long pendingVersion = activeVersion + 1;
		final MigrationImpact impact = findImpact(connection);
		beginSynchronization(connection, activeVersion, pendingVersion);
		appendAudit(connection, holidayId, pendingVersion, impact);
	}

	private boolean hasActiveMondayRule(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("""
				SELECT 1
				FROM recurring_holiday_rules
				WHERE day_of_week = 'MONDAY'
				  AND active = TRUE
				LIMIT 1
				""")) {
			return resultSet.next();
		}
	}

	private long lockActiveConfigVersion(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("""
				SELECT status, active_version
				FROM schedule_config_guard
				WHERE id = 1
				FOR UPDATE
				""")) {
			if (!resultSet.next()) {
				throw new SQLException("ScheduleConfigGuard singleton is missing");
			}
			if (!"ACTIVE".equals(resultSet.getString("status"))) {
				throw new SQLException("ScheduleConfigGuard must be ACTIVE before V26");
			}
			return resultSet.getLong("active_version");
		}
	}

	private long insertDefaultHoliday(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			INSERT INTO recurring_holiday_rules (
				day_of_week,
				effective_from,
				effective_to,
				reason,
				active,
				created_by,
				updated_by
			) VALUES ('MONDAY', ?, NULL, ?, TRUE, ?, ?)
			""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setDate(1, java.sql.Date.valueOf(DEFAULT_EFFECTIVE_FROM));
			statement.setString(2, REASON);
			statement.setString(3, ACTOR);
			statement.setString(4, ACTOR);
			statement.executeUpdate();
			try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
				if (!generatedKeys.next()) {
					throw new SQLException("Default Monday holiday ID was not generated");
				}
				return generatedKeys.getLong(1);
			}
		}
	}

	private void beginSynchronization(
		Connection connection,
		long activeVersion,
		long pendingVersion
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			UPDATE schedule_config_guard
			SET status = 'SYNCING',
				pending_version = ?,
				sync_started_at = CURRENT_TIMESTAMP(6),
				sync_started_by = ?,
				last_failed_at = NULL,
				last_failure_code = NULL,
				last_failure_summary = NULL
			WHERE id = 1
			  AND status = 'ACTIVE'
			  AND active_version = ?
			""")) {
			statement.setLong(1, pendingVersion);
			statement.setString(2, ACTOR);
			statement.setLong(3, activeVersion);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("Default Monday holiday config transition failed");
			}
		}
	}

	private MigrationImpact findImpact(Connection connection) throws SQLException {
		final LocalDate horizonStart = LocalDate.now(SEOUL_ZONE);
		final LocalDate horizonEnd = horizonStart.plusMonths(HORIZON_MONTHS);
		final long affectedDateCount = countImpact(connection, """
			SELECT COUNT(*)
			FROM schedule_dates
			WHERE schedule_date BETWEEN ? AND ?
			  AND DAYOFWEEK(schedule_date) = 2
			""", horizonStart, horizonEnd);
		final long templateTimeSlotCount = countImpact(connection, """
			SELECT COUNT(*)
			FROM time_slot_capacities
			WHERE source = 'TEMPLATE'
			  AND lesson_date BETWEEN ? AND ?
			  AND DAYOFWEEK(lesson_date) = 2
			""", horizonStart, horizonEnd);
		final long activeReservationCount = countImpact(connection, """
			SELECT COUNT(*)
			FROM reservations reservation
			JOIN time_slot_capacities time_slot
			  ON time_slot.lesson_date = reservation.lesson_date
			 AND time_slot.start_time = reservation.start_time
			WHERE time_slot.source = 'TEMPLATE'
			  AND time_slot.lesson_date BETWEEN ? AND ?
			  AND DAYOFWEEK(time_slot.lesson_date) = 2
			  AND reservation.active_slot_guard = 1
			""", horizonStart, horizonEnd);
		return new MigrationImpact(
			affectedDateCount,
			templateTimeSlotCount,
			activeReservationCount);
	}

	private long countImpact(
		Connection connection,
		String sql,
		LocalDate horizonStart,
		LocalDate horizonEnd
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setDate(1, java.sql.Date.valueOf(horizonStart));
			statement.setDate(2, java.sql.Date.valueOf(horizonEnd));
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("Default Monday holiday impact query returned no row");
				}
				return resultSet.getLong(1);
			}
		}
	}

	private void appendAudit(
		Connection connection,
		long holidayId,
		long pendingVersion,
		MigrationImpact impact
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			INSERT INTO schedule_audit_logs (
				target_type,
				target_key,
				action,
				from_state,
				to_state,
				actor_auth_subject,
				reason,
				metadata_json
			) VALUES (
				'RECURRING_HOLIDAY',
				?,
				'DEFAULT_CREATED',
				NULL,
				JSON_OBJECT(
					'dayOfWeek', 'MONDAY',
					'effectiveFrom', ?,
					'reason', ?,
					'active', TRUE
				),
				?,
				?,
				JSON_OBJECT(
					'pendingConfigVersion', ?,
					'affectedDateCount', ?,
					'templateTimeSlotCount', ?,
					'activeReservationCount', ?
				)
			)
			""")) {
			statement.setString(1, "recurring-holiday:" + holidayId);
			statement.setString(2, DEFAULT_EFFECTIVE_FROM.toString());
			statement.setString(3, REASON);
			statement.setString(4, ACTOR);
			statement.setString(5, REASON);
			statement.setLong(6, pendingVersion);
			statement.setLong(7, impact.affectedDateCount());
			statement.setLong(8, impact.templateTimeSlotCount());
			statement.setLong(9, impact.activeReservationCount());
			statement.executeUpdate();
		}
	}

	private record MigrationImpact(
		long affectedDateCount,
		long templateTimeSlotCount,
		long activeReservationCount
	) {
	}
}
