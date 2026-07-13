package com.horse.coupons.infrastructure;

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
class CouponRepositoryIntegrationTest {

	private static final List<String> COUPON_TYPES = List.of("general", "dressage", "jumping");
	private static final List<String> COUPON_STATUSES = List.of("active", "expired", "depleted");
	private static final List<String> USAGE_ACTIONS = List.of(
		"held", "confirmed", "used", "released", "deducted", "expired", "free_change_used");
	private static final List<String> ACTOR_TYPES = List.of("system", "member", "admin");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void 빈_DB에_쿠폰과_사용_로그_스키마를_적용한다() {
		final Long memberId = insertMember("coupon-schema-member");

		final Long couponId = insertCoupon(memberId, "general", 10, 10, 0, null, null, "active");
		insertUsageLog(couponId, null, memberId, "held", 1, "member", "예약 신청");

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM flyway_schema_history
			WHERE success = TRUE AND version IN ('1', '2', '3', '4')
			""", Integer.class)).isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT free_change_used FROM coupons WHERE id = ?", Boolean.class, couponId)).isFalse();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT memo FROM coupon_usage_logs WHERE coupon_id = ?", String.class, couponId))
			.isEqualTo("예약 신청");
	}

	@Test
	void 기존_마이그레이션에_쿠폰과_사용_로그_스키마를_추가한다() throws Exception {
		final String databaseName = "horse_coupon_migration";
		final String databaseUrl = createDatabase(databaseName);
		final Flyway previousFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("3"));

		previousFlyway.migrate();

		final Flyway latestFlyway = flyway(databaseUrl, MigrationVersion.fromVersion("4"));
		latestFlyway.migrate();

		assertThat(latestFlyway.info().current().getVersion().getVersion()).isEqualTo("4");
		try (Connection connection = DriverManager.getConnection(
			databaseUrl, "root", mysqlContainer.getPassword());
			Statement statement = connection.createStatement()) {
			assertThat(statement.executeQuery("SELECT COUNT(*) FROM coupons").next()).isTrue();
			assertThat(statement.executeQuery("SELECT COUNT(*) FROM coupon_usage_logs").next()).isTrue();
		}
	}

	@Test
	void 쿠폰_횟수와_사용일_제약을_검증한다() {
		final Long memberId = insertMember("coupon-count-member");

		assertThatThrownBy(() -> insertCoupon(memberId, "general", 10, 11, 0, null, null, "active"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertCoupon(memberId, "general", 10, 5, 6, null, null, "active"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertCoupon(memberId, "general", 0, 0, 0, null, null, "depleted"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertCoupon(
			memberId, "general", 10, 10, 0, "2026-07-01 09:00:00", null, "active"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertCoupon(
			memberId,
			"general",
			10,
			10,
			0,
			"2026-07-02 09:00:00",
			"2026-07-01 09:00:00",
			"active"))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 허용된_쿠폰_종류와_상태만_저장한다() {
		final Long memberId = insertMember("coupon-enum-member");

		COUPON_TYPES.forEach(type -> insertCoupon(memberId, type, 10, 10, 0, null, null, "active"));
		COUPON_STATUSES.forEach(status -> insertCoupon(memberId, "general", 10, 10, 0, null, null, status));

		assertThatThrownBy(() -> insertCoupon(memberId, "invalid", 10, 10, 0, null, null, "active"))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertCoupon(memberId, "general", 10, 10, 0, null, null, "invalid"))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void 사용_로그는_예약_없이도_기록하고_참조와_열거값을_검증한다() {
		final Long memberId = insertMember("coupon-log-member");
		final Long anotherMemberId = insertMember("coupon-log-another-member");
		final Long couponId = insertCoupon(memberId, "general", 10, 10, 0, null, null, "active");

		USAGE_ACTIONS.forEach(action ->
			insertUsageLog(couponId, null, memberId, action, 0, "system", null));
		ACTOR_TYPES.forEach(actorType ->
			insertUsageLog(couponId, null, memberId, "held", 1, actorType, null));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM coupon_usage_logs WHERE reservation_id IS NULL",
			Integer.class)).isEqualTo(10);
		assertThatThrownBy(() ->
			insertUsageLog(couponId, null, anotherMemberId, "held", 1, "member", null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() ->
			insertUsageLog(couponId, null, memberId, "invalid", 1, "member", null))
			.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() ->
			insertUsageLog(couponId, null, memberId, "held", 1, "invalid", null))
			.isInstanceOf(DataAccessException.class);
	}

	private Long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '쿠폰 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM members WHERE auth_subject = ?", Long.class, authSubject);
	}

	private Long insertCoupon(
		Long memberId,
		String type,
		int totalCount,
		int remainingCount,
		int heldCount,
		String firstUsedAt,
		String expiresAt,
		String status
	) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id,
				coupon_type,
				total_count,
				remaining_count,
				held_count,
				first_used_at,
				expires_at,
				status
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			""", memberId, type, totalCount, remainingCount, heldCount, firstUsedAt, expiresAt, status);
		return jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM coupons WHERE member_id = ?", Long.class, memberId);
	}

	private void insertUsageLog(
		Long couponId,
		Long reservationId,
		Long memberId,
		String action,
		int countDelta,
		String actorType,
		String memo
	) {
		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id,
				reservation_id,
				member_id,
				action,
				count_delta,
				occurred_at,
				actor_type,
				memo
			) VALUES (?, ?, ?, ?, ?, '2026-07-14 10:00:00', ?, ?)
			""", couponId, reservationId, memberId, action, countDelta, actorType, memo);
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
