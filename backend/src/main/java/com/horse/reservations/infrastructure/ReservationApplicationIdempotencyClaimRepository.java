package com.horse.reservations.infrastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.reservations.domain.ReservationApplicationOperation;

@Repository
public class ReservationApplicationIdempotencyClaimRepository {

	private static final int DUPLICATE_KEY_ERROR_CODE = 1062;
	private static final String CLAIM_SQL = """
		INSERT IGNORE INTO reservation_application_idempotencies (
			auth_subject,
			operation,
			idempotency_key,
			request_fingerprint,
			status
		) VALUES (?, ?, ?, ?, 'processing')
		""";

	private final JdbcTemplate jdbcTemplate;

	public ReservationApplicationIdempotencyClaimRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public boolean claim(
		String authSubject,
		ReservationApplicationOperation operation,
		String idempotencyKey,
		String requestFingerprint
	) {
		return jdbcTemplate.execute((ConnectionCallback<Boolean>) connection ->
			insertClaim(
				connection,
				authSubject,
				operation,
				idempotencyKey,
				requestFingerprint));
	}

	private boolean insertClaim(
		Connection connection,
		String authSubject,
		ReservationApplicationOperation operation,
		String idempotencyKey,
		String requestFingerprint
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(CLAIM_SQL)) {
			statement.setString(1, authSubject);
			statement.setString(2, operation.databaseValue());
			statement.setString(3, idempotencyKey);
			statement.setString(4, requestFingerprint);
			final int insertedCount = statement.executeUpdate();
			assertOnlyDuplicateWarnings(statement.getWarnings());
			return insertedCount == 1;
		}
	}

	private void assertOnlyDuplicateWarnings(SQLWarning warning) throws SQLException {
		SQLWarning current = warning;
		while (current != null) {
			if (current.getErrorCode() != DUPLICATE_KEY_ERROR_CODE) {
				throw new SQLException(
					"Reservation idempotency claim warning: " + current.getMessage(),
					current.getSQLState(),
					current.getErrorCode());
			}
			current = current.getNextWarning();
		}
	}
}
