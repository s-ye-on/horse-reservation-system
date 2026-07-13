package com.horse.reservations.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.flywaydb.core.Flyway;
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
class ReservationRepositoryIntegrationTest {

	private static final List<String> RESERVATION_STATUSES = List.of(
		"pending_admin_approval",
		"pending_payment",
		"payment_expired",
		"confirmed",
		"completed",
		"rejected",
		"cancelled",
		"no_show");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 빈_DB에_예약_스키마와_사용_로그_외래_키를_적용한다() {
		final Long memberId = insertMember("reservation-schema-member");
		final Long couponId = insertCoupon(memberId);

		final Long reservationId = insertReservation(
			memberId, couponId, "pending_admin_approval", "coupon", null, null, null, null);
		insertUsageLog(couponId, reservationId, memberId);

		assertThat(jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM flyway_schema_history
				WHERE success = TRUE AND version IN ('1', '2', '3', '4', '5')
				""", Integer.class)).isEqualTo(5);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT reservation_id FROM coupon_usage_logs WHERE reservation_id = ?",
			Long.class,
			reservationId)).isEqualTo(reservationId);
	}

	@Test
	void 기존_마이그레이션에_예약_스키마를_추가한다() throws Exception {
		final String databaseName = "horse_reservation_migration";
		final String databaseUrl = createDatabase(databaseName);
		final Flyway previousFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("4"));

		previousFlyway.migrate();

		final Flyway latestFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("5"));
		latestFlyway.migrate();

		assertThat(latestFlyway.info().current().getVersion().getVersion()).isEqualTo("5");
		try (Connection connection = DriverManager.getConnection(
			databaseUrl, "root", mysqlContainer.getPassword());
			Statement statement = connection.createStatement()) {
			assertThat(statement.executeQuery("SELECT COUNT(*) FROM reservations").next()).isTrue();
		}
	}

	@Test
	void 허용된_예약_상태를_서로_다른_값으로_저장한다() {
		final Long memberId = insertMember("reservation-status-member");
		final Long couponId = insertCoupon(memberId);

		RESERVATION_STATUSES.forEach(status -> insertValidReservation(memberId, couponId, status));

		assertThat(jdbcTemplate.queryForList(
			"SELECT status FROM reservations ORDER BY id", String.class))
			.containsExactlyElementsOf(RESERVATION_STATUSES);
		assertThatThrownBy(() -> insertReservation(
			memberId, couponId, "invalid", "coupon", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 결제_출처와_대기_상태의_필수값을_검증한다() {
		final Long memberId = insertMember("reservation-payment-member");
		final Long couponId = insertCoupon(memberId);

		assertThatThrownBy(() -> insertReservation(
			memberId, null, "pending_admin_approval", "coupon", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertReservation(
			memberId, couponId, "pending_admin_approval", "single_payment", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertReservation(
			memberId, null, "pending_payment", "single_payment", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertReservation(
			memberId, couponId, "pending_payment", "single_payment", "2026-07-14 12:00:00", null, null, null))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 확정_반려_취소_상태의_감사_필드를_검증한다() {
		final Long memberId = insertMember("reservation-audit-member");
		final Long couponId = insertCoupon(memberId);

		assertThatThrownBy(() -> insertReservation(
			memberId, couponId, "confirmed", "coupon", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertReservation(
			memberId, couponId, "rejected", "coupon", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertReservation(
			memberId, couponId, "cancelled", "coupon", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertReservation(
			memberId,
			couponId,
			"pending_admin_approval",
			"coupon",
			null,
			null,
			"admin-subject",
			"승인하지 않음"))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 회원과_쿠폰과_사용_로그의_참조를_검증한다() {
		final Long memberId = insertMember("reservation-reference-member");
		final Long anotherMemberId = insertMember("reservation-reference-another");
		final Long couponId = insertCoupon(memberId);
		final Long reservationId = insertReservation(
			memberId, couponId, "pending_admin_approval", "coupon", null, null, null, null);

		assertThatThrownBy(() -> insertReservation(
			anotherMemberId, couponId, "pending_admin_approval", "coupon", null, null, null, null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertUsageLog(couponId, reservationId + 1000, memberId))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 활성_점유_집계용_복합_인덱스를_생성한다() {
		final Integer columnCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'reservations'
			  AND index_name = 'idx_reservations_occupancy'
			  AND column_name IN ('lesson_date', 'start_time', 'status', 'class_type')
			""", Integer.class);

		assertThat(columnCount).isEqualTo(4);
	}

	private void insertValidReservation(Long memberId, Long couponId, String status) {
		switch (status) {
			case "pending_payment", "payment_expired" -> insertReservation(
				memberId,
				null,
				status,
				"single_payment",
				"2026-07-14 12:00:00",
				null,
				null,
				null);
			case "confirmed", "completed", "no_show" -> insertReservation(
				memberId, couponId, status, "coupon", null, "2026-07-14 10:30:00", null, null);
			case "rejected" -> insertReservation(
				memberId, couponId, status, "coupon", null, null, "admin-subject", "승인하지 않음");
			case "cancelled" -> insertCancelledReservation(memberId, couponId);
			default -> insertReservation(memberId, couponId, status, "coupon", null, null, null, null);
		}
	}

	private Long insertReservation(
		Long memberId,
		Long couponId,
		String status,
		String paymentSource,
		String paymentDueAt,
		String adminConfirmedAt,
		String rejectedBy,
		String rejectionReason
	) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id,
				class_type,
				lesson_date,
				start_time,
				status,
				payment_source,
				coupon_id,
				payment_due_at,
				approval_requested_at,
				admin_confirmed_at,
				rejected_at,
				rejected_by,
				rejection_reason
			) VALUES (
				?, 'ROUND_BEGINNER', '2026-07-20', '09:00:00', ?, ?, ?, ?,
				'2026-07-14 10:00:00', ?,
				IF(? IS NULL, NULL, '2026-07-14 10:10:00'), ?, ?
			)
			""",
			memberId,
			status,
			paymentSource,
			couponId,
			paymentDueAt,
			adminConfirmedAt,
			rejectedBy,
			rejectedBy,
			rejectionReason);
		return jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM reservations WHERE member_id = ?", Long.class, memberId);
	}

	private void insertCancelledReservation(Long memberId, Long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id,
				class_type,
				lesson_date,
				start_time,
				status,
				payment_source,
				coupon_id,
				approval_requested_at,
				cancelled_at,
				cancellation_responsibility
			) VALUES (
				?, 'ROUND_BEGINNER', '2026-07-20', '09:00:00', 'cancelled', 'coupon', ?,
				'2026-07-14 10:00:00', '2026-07-14 10:10:00', 'member'
			)
			""", memberId, couponId);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '예약 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

	private Long insertCoupon(Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (member_id, coupon_type, total_count, remaining_count)
			VALUES (?, 'general', 10, 10)
			""", memberId);
		return jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM coupons WHERE member_id = ?", Long.class, memberId);
	}

	private void insertUsageLog(Long couponId, Long reservationId, Long memberId) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id,
				reservation_id,
				member_id,
				action,
				count_delta,
				occurred_at,
				actor_type
			) VALUES (?, ?, ?, 'held', 1, '2026-07-14 10:00:00', 'system')
			""", couponId, reservationId, memberId);
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

	private Flyway flyway(String databaseUrl, MigrationVersion target) {
		var configuration = Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

	private String rootJdbcUrl(String databaseName) {
		return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul"
			.formatted(mysqlContainer.getHost(), mysqlContainer.getMappedPort(3306), databaseName);
	}

}
