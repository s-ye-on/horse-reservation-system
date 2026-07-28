package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M31CommandLockPlanIntegrationTest {

	private static final String DATABASE_NAME = "horse_m31_06_command_lock_plan";
	private static final String LESSON_DATE = "2099-06-15";
	private static final String TARGET_MEMBER_SUBJECT = "m31-06-target-member";
	private static final int PLAN_NOISE_RESERVATION_COUNT = 128;
	private static final String CLASS_CAPACITIES_JSON = """
		{
			"FIRST_RIDE": 8,
			"ROUND_BEGINNER": 8,
			"ROUND_TROT": 8,
			"LARGE_ARENA_BEGINNER": 8,
			"LARGE_ARENA_TROT": 8,
			"DRESSAGE": 8,
			"JUMPING": 8
		}
		""";

	@Autowired
	MySQLContainer mysqlContainer;

	private String databaseUrl;
	private long targetMemberId;

	@BeforeAll
	void 실행_계획_검증용_V32_데이터베이스를_준비한다() throws Exception {
		databaseUrl = createDatabase();
		Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration")
			.load()
			.migrate();
		seedFixture();
	}

	@AfterAll
	void 실행_계획_검증용_데이터베이스를_정리한다() throws Exception {
		try (Connection connection = rootConnection("mysql");
			Statement statement = connection.createStatement()) {
			statement.execute("DROP DATABASE IF EXISTS " + DATABASE_NAME);
		}
	}

	@Test
	void 핵심_잠금_조회는_실제_MySQL_실행_계획과_후보_인덱스를_고정한다() throws Exception {
		try (Connection connection = connection();
			Statement statement = connection.createStatement()) {
			assertThat(indexColumns(statement, "schedule_dates", "uk_schedule_dates_date"))
				.containsExactly("schedule_date");
			assertThat(indexColumns(
				statement,
				"time_slot_capacities",
				"idx_time_slot_capacities_lesson_date_id"))
				.containsExactly("lesson_date", "id");
			assertThat(indexColumns(
				statement,
				"reservations",
				"idx_reservations_member_date_active_interval"))
				.containsExactly(
					"member_id",
					"lesson_date",
					"active_slot_guard",
					"start_time",
					"id",
					"end_time");
			assertThat(indexColumns(statement, "reservations", "idx_reservations_occupancy"))
				.containsExactly("lesson_date", "start_time", "status", "class_type");
			assertThat(indexColumns(statement, "coupons", "idx_coupons_member_status_expiry"))
				.containsExactly("member_id", "status", "expires_at");

			assertThat(explainJson(statement, scheduleDateRangeSql()))
				.contains("\"access_type\": \"range\"")
				.contains("\"key\": \"uk_schedule_dates_date\"")
				.contains("\"using_filesort\": false");
			assertThat(explainJson(statement, timeSlotDateSql()))
				.contains("\"access_type\": \"ref\"")
				.contains("\"key\": \"idx_time_slot_capacities_lesson_date_id\"")
				.contains("\"using_filesort\": false");
			assertThat(explainJson(statement, overlapSql()))
				.contains("\"access_type\": \"range\"")
				.contains("\"key\": \"idx_reservations_member_date_active_interval\"")
				.contains("\"member_id\"")
				.contains("\"lesson_date\"")
				.contains("\"active_slot_guard\"")
				.contains("\"start_time\"");
			assertThat(explainAnalyze(statement, overlapSql()))
				.contains("Index range scan")
				.contains("idx_reservations_member_date_active_interval");
			assertThat(explainJson(statement, occupancySql()))
				.contains("\"access_type\": \"index\"")
				.contains("\"idx_reservations_occupancy\"")
				.contains("\"key\": \"PRIMARY\"")
				.contains("\"using_filesort\": false");
			assertThat(explainJson(statement, reservationHistorySql()))
				.contains("\"access_type\": \"ref\"")
				.contains("\"key\": \"idx_reservations_occupancy\"");
			assertThat(explainJson(statement, couponSelectionSql()))
				.contains("\"key\": \"idx_coupons_member_status_expiry\"")
				.contains("\"using_filesort\": true");
		}
	}

	@Test
	void REPEATABLE_READ_범위_조회는_겹침_구간의_gap_삽입을_차단한다() throws Exception {
		try (Connection lockConnection = connection();
			Connection insertConnection = connection()) {
			beginRepeatableRead(lockConnection);
			beginRepeatableRead(insertConnection);
			try (Statement lockStatement = lockConnection.createStatement();
				Statement insertStatement = insertConnection.createStatement()) {
				lockStatement.executeQuery(overlapSql()).close();
				insertStatement.execute("SET SESSION innodb_lock_wait_timeout = 1");

				assertThatThrownBy(() -> insertStatement.executeUpdate("""
					INSERT INTO reservations (
						member_id, class_type, lesson_date, start_time, end_time, status,
						payment_source, payment_due_at, approval_requested_at
					) VALUES (
						%d, 'FIRST_RIDE', '%s', '10:45:00', '11:30:00',
						'pending_payment', 'single_payment',
						'2099-06-14 12:00:00', '2099-06-14 10:00:00'
					)
					""".formatted(targetMemberId, LESSON_DATE)))
					.isInstanceOfSatisfying(SQLException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(1205));
			}
			finally {
				insertConnection.rollback();
				lockConnection.rollback();
			}
		}
	}

	@Test
	void 단일행_FOR_UPDATE는_동일_레코드_갱신을_차단한다() throws Exception {
		try (Connection lockConnection = connection();
			Connection updateConnection = connection()) {
			beginRepeatableRead(lockConnection);
			beginRepeatableRead(updateConnection);
			try (Statement lockStatement = lockConnection.createStatement();
				Statement updateStatement = updateConnection.createStatement()) {
				lockStatement.executeQuery(
					"SELECT * FROM members WHERE id = " + targetMemberId + " FOR UPDATE").close();
				updateStatement.execute("SET SESSION innodb_lock_wait_timeout = 1");

				assertThatThrownBy(() -> updateStatement.executeUpdate(
					"UPDATE members SET phone = '010-9999-9999' WHERE id = " + targetMemberId))
					.isInstanceOfSatisfying(SQLException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(1205));
			}
			finally {
				updateConnection.rollback();
				lockConnection.rollback();
			}
		}
	}

	private void seedFixture() throws Exception {
		try (Connection connection = connection();
			Statement statement = connection.createStatement()) {
			statement.executeUpdate("""
				INSERT INTO schedule_dates (
					schedule_date, status, applied_config_version
				) VALUES ('%s', 'NORMAL', 1)
				""".formatted(LESSON_DATE));
			statement.executeUpdate("""
				INSERT INTO time_slot_capacities (
					lesson_date, start_time, end_time, source,
					total_capacity, round_arena_capacity, class_capacity_json
				) VALUES
					('%s', '09:00:00', '09:45:00', 'MANUAL', 8, 4, '%s'),
					('%s', '10:00:00', '10:45:00', 'MANUAL', 8, 4, '%s'),
					('%s', '11:00:00', '11:45:00', 'MANUAL', 8, 4, '%s')
				""".formatted(
					LESSON_DATE,
					escapedClassCapacities(),
					LESSON_DATE,
					escapedClassCapacities(),
					LESSON_DATE,
					escapedClassCapacities()));
			for (int index = 0; index < 8; index++) {
				statement.executeUpdate("""
					INSERT INTO members (auth_subject, name, phone)
					VALUES ('m31-06-member-%d', '잠금 회원 %d', '010-0000-%04d')
					""".formatted(index, index, index));
			}
			statement.executeUpdate("""
				UPDATE members
				SET auth_subject = '%s'
				WHERE auth_subject = 'm31-06-member-0'
				""".formatted(TARGET_MEMBER_SUBJECT));
			targetMemberId = memberId(statement, TARGET_MEMBER_SUBJECT);
			insertReservations(statement);
			insertPlanNoise(statement);
			insertCoupons(statement);
			insertClosure(statement);
		}
	}

	private void insertReservations(Statement statement) throws Exception {
		for (int index = 1; index < 7; index++) {
			final long memberId = memberId(statement, "m31-06-member-" + index);
			statement.executeUpdate(activeReservationSql(memberId, "09:00:00", "09:45:00"));
		}
		statement.executeUpdate(activeReservationSql(targetMemberId, "10:00:00", "10:45:00"));
	}

	private void insertPlanNoise(Statement statement) throws Exception {
		for (int index = 0; index < PLAN_NOISE_RESERVATION_COUNT; index++) {
			statement.addBatch("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, end_time, status,
					payment_source, payment_due_at, approval_requested_at, admin_confirmed_at
				) VALUES (
					%d, 'FIRST_RIDE', '2099-07-01', '09:00:00', '09:45:00',
					'completed', 'single_payment',
					'2099-06-30 12:00:00', '2099-06-30 10:00:00',
					'2099-06-30 11:00:00'
				)
				""".formatted(targetMemberId));
		}
		statement.executeBatch();
	}

	private void insertCoupons(Statement statement) throws Exception {
		for (int index = 0; index < 8; index++) {
			statement.executeUpdate("""
				INSERT INTO coupons (
					member_id, coupon_type, total_count, remaining_count, held_count,
					first_used_at, expires_at, status, created_by, created_at
				) VALUES (
					%d, 'general', 10, 10, 0,
					'2099-01-01 00:00:00', '2099-09-%02d 00:00:00',
					'active', 'm31-06', '2099-01-%02d 00:00:00'
				)
				""".formatted(targetMemberId, index + 1, index + 1));
		}
	}

	private void insertClosure(Statement statement) throws Exception {
		final long timeSlotId = statementQueryLong(statement, """
			SELECT id
			FROM time_slot_capacities
			WHERE lesson_date = '%s' AND start_time = '09:00:00'
			""".formatted(LESSON_DATE));
		statement.executeUpdate("""
			INSERT INTO time_slot_closures (
				time_slot_id, status, reason, started_by, started_at
			) VALUES (
				%d, 'IN_PROGRESS', 'M31-06 잠금 계획', 'm31-06', '2099-06-14 10:00:00'
			)
			""".formatted(timeSlotId));
	}

	private String activeReservationSql(long memberId, String startTime, String endTime) {
		return """
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, end_time, status,
				payment_source, payment_due_at, approval_requested_at
			) VALUES (
				%d, 'FIRST_RIDE', '%s', '%s', '%s',
				'pending_payment', 'single_payment',
				'2099-06-14 12:00:00', '2099-06-14 10:00:00'
			)
			""".formatted(memberId, LESSON_DATE, startTime, endTime);
	}

	private long memberId(Statement statement, String authSubject) throws Exception {
		return statementQueryLong(statement, """
			SELECT id FROM members WHERE auth_subject = '%s'
			""".formatted(authSubject));
	}

	private long statementQueryLong(Statement statement, String sql) throws Exception {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private List<String> indexColumns(
		Statement statement,
		String tableName,
		String indexName
	) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT column_name
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = '%s'
			  AND index_name = '%s'
			ORDER BY seq_in_index
			""".formatted(tableName, indexName))) {
			final List<String> columns = new ArrayList<>();
			while (resultSet.next()) {
				columns.add(resultSet.getString("column_name"));
			}
			return columns;
		}
	}

	private String explainJson(Statement statement, String sql) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("EXPLAIN FORMAT=JSON " + sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String explainAnalyze(Statement statement, String sql) throws Exception {
		try (ResultSet resultSet = statement.executeQuery("EXPLAIN ANALYZE " + sql)) {
			final StringBuilder plan = new StringBuilder();
			while (resultSet.next()) {
				plan.append(resultSet.getString(1));
			}
			return plan.toString();
		}
	}

	private String scheduleDateRangeSql() {
		return """
			SELECT *
			FROM schedule_dates
			FORCE INDEX (uk_schedule_dates_date)
			WHERE schedule_date BETWEEN '2099-06-15' AND '2099-06-16'
			ORDER BY schedule_date
			FOR UPDATE
			""";
	}

	private String timeSlotDateSql() {
		return """
			SELECT *
			FROM time_slot_capacities
			FORCE INDEX (idx_time_slot_capacities_lesson_date_id)
			WHERE lesson_date = '2099-06-15'
			ORDER BY id
			FOR UPDATE
			""";
	}

	private String overlapSql() {
		return """
			SELECT reservation.*
			FROM reservations reservation
			FORCE INDEX (idx_reservations_member_date_active_interval)
			WHERE reservation.member_id = %d
			  AND reservation.lesson_date = '%s'
			  AND reservation.active_slot_guard = 1
			  AND reservation.start_time < '11:30:00'
			  AND reservation.end_time > '10:45:00'
			ORDER BY reservation.start_time, reservation.id
			FOR UPDATE
			""".formatted(targetMemberId, LESSON_DATE);
	}

	private String occupancySql() {
		return """
			SELECT reservation.*
			FROM reservations reservation
			WHERE reservation.lesson_date = '%s'
			  AND reservation.start_time = '09:00:00'
			  AND reservation.status IN (
				'pending_admin_approval', 'pending_payment', 'confirmed'
			  )
			ORDER BY reservation.id
			FOR UPDATE
			""".formatted(LESSON_DATE);
	}

	private String reservationHistorySql() {
		return """
			SELECT reservation.id
			FROM reservations reservation
			FORCE INDEX (idx_reservations_occupancy)
			WHERE reservation.lesson_date = '%s'
			  AND reservation.start_time = '09:00:00'
			ORDER BY reservation.id
			FOR UPDATE
			""".formatted(LESSON_DATE);
	}

	private String couponSelectionSql() {
		return """
			SELECT coupon.*
			FROM coupons coupon
			WHERE coupon.member_id = %d
			  AND coupon.coupon_type = 'general'
			  AND coupon.status = 'active'
			  AND coupon.remaining_count > coupon.held_count
			  AND (
				coupon.expires_at IS NULL
				OR DATE(coupon.expires_at) >= '%s'
			  )
			ORDER BY (coupon.expires_at IS NULL) ASC,
				coupon.expires_at ASC,
				coupon.created_at ASC,
				coupon.id ASC
			LIMIT 1
			FOR UPDATE
			""".formatted(targetMemberId, LESSON_DATE);
	}

	private void beginRepeatableRead(Connection connection) throws Exception {
		connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
		connection.setAutoCommit(false);
		try (Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT @@transaction_isolation")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString(1)).isEqualTo("REPEATABLE-READ");
		}
	}

	private String escapedClassCapacities() {
		return CLASS_CAPACITIES_JSON.replace("'", "''");
	}

	private String createDatabase() throws Exception {
		try (Connection connection = rootConnection("mysql");
			Statement statement = connection.createStatement()) {
			statement.execute("DROP DATABASE IF EXISTS " + DATABASE_NAME);
			statement.execute("CREATE DATABASE " + DATABASE_NAME);
		}
		return rootJdbcUrl(DATABASE_NAME);
	}

	private Connection connection() throws Exception {
		return DriverManager.getConnection(databaseUrl, "root", mysqlContainer.getPassword());
	}

	private Connection rootConnection(String databaseName) throws Exception {
		return DriverManager.getConnection(
			rootJdbcUrl(databaseName),
			"root",
			mysqlContainer.getPassword());
	}

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(
			mysqlContainer.getDatabaseName(),
			databaseName);
	}
}
