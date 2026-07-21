package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class ActiveReservationUniquenessMigrationIntegrationTest {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 활성_예약은_같은_회원과_날짜와_시각에_하나만_저장한다() {
		final Long memberId = insertMember("active-unique-member");
		insertPendingPayment(memberId, "pending_payment");

		assertThatThrownBy(() -> insertPendingPayment(memberId, "confirmed"))
			.isInstanceOf(DataAccessException.class);

		assertThat(activeReservationCount(memberId)).isEqualTo(1);
	}

	@Test
	void 기존_예약이_종료되면_같은_슬롯에_다시_예약할_수_있다() {
		final Long memberId = insertMember("inactive-history-member");
		insertPendingPayment(memberId, "pending_payment");
		jdbcTemplate.update("""
			UPDATE reservations
			SET status = 'payment_expired'
			WHERE member_id = ?
			""", memberId);

		insertPendingPayment(memberId, "pending_payment");

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM reservations WHERE member_id = ?",
			Integer.class,
			memberId)).isEqualTo(2);
		assertThat(activeReservationCount(memberId)).isEqualTo(1);
	}

	@Test
	void generated_marker와_활성_중복_UNIQUE를_생성한다() {
		final Integer generatedColumnCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservations'
			  AND column_name = 'active_slot_guard'
			  AND extra LIKE '%STORED GENERATED%'
			""", Integer.class);
		final Integer uniqueColumnCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservations'
			  AND index_name = 'uk_reservations_active_member_slot'
			  AND non_unique = 0
			""", Integer.class);

		assertThat(generatedColumnCount).isEqualTo(1);
		assertThat(uniqueColumnCount).isEqualTo(4);
	}

	@Test
	void 기존_활성_중복이_있으면_migration을_중단한다() throws Exception {
		final String databaseName = "horse_active_duplicate_migration";
		final String databaseUrl = createDatabase(databaseName);
		final Flyway previousFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("14"));
		previousFlyway.migrate();
		insertDuplicateActiveReservations(databaseUrl);

		final Flyway latestFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("15"));

		assertThatThrownBy(latestFlyway::migrate)
			.isInstanceOf(FlywayException.class);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '중복 검증 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertPendingPayment(Long memberId, String status) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status, payment_source,
				payment_due_at, approval_requested_at, admin_confirmed_at
			) VALUES (
				?, 'FIRST_RIDE', '2026-08-01', '09:00:00', ?, 'single_payment',
				'2026-07-31 12:00:00', '2026-07-31 10:00:00',
				IF(? = 'confirmed', '2026-07-31 10:10:00', NULL)
			)
			""", memberId, status, status);
	}

	private int activeReservationCount(Long memberId) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM reservations
			WHERE member_id = ? AND active_slot_guard = 1
			""", Integer.class, memberId);
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

	private void insertDuplicateActiveReservations(String databaseUrl) throws Exception {
		try (Connection connection = DriverManager.getConnection(
			databaseUrl, "root", mysqlContainer.getPassword());
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('migration-duplicate-member', '중복 회원', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status, payment_source,
					payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-01', '09:00:00', 'pending_payment',
					'single_payment', '2026-07-31 12:00:00', '2026-07-31 10:00:00'
				FROM members WHERE auth_subject = 'migration-duplicate-member'
				""");
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status, payment_source,
					payment_due_at, approval_requested_at
				)
				SELECT id, 'FIRST_RIDE', '2026-08-01', '09:00:00', 'pending_payment',
					'single_payment', '2026-07-31 12:00:00', '2026-07-31 10:01:00'
				FROM members WHERE auth_subject = 'migration-duplicate-member'
				""");
		}
	}

	private Flyway flyway(String databaseUrl, MigrationVersion target) {
		return Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration")
			.target(target)
			.load();
	}

	private String rootJdbcUrl(String databaseName) {
		return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul"
			.formatted(mysqlContainer.getHost(), mysqlContainer.getMappedPort(3306), databaseName);
	}

}
