package com.horse.coupons.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
class FamilyCouponMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "36";
	private static final String CURRENT_VERSION = "37";

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V36_사용_이력은_V37에서_원소유자_snapshot으로_보존된다() throws Exception {
		final String databaseUrl = createDatabase("horse_m32_03_family_coupon_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('existing-coupon-owner', '기존 소유자', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO coupons (
					member_id, coupon_type, total_count, remaining_count, created_by
				) VALUES (LAST_INSERT_ID(), 'general', 10, 10, 'migration-admin')
				""");
			final long couponId = lastInsertId(statement);
			final long memberId = queryLong(
				statement,
				"SELECT member_id FROM coupons WHERE id = " + couponId);
			statement.execute("""
				INSERT INTO reservations (
					member_id, class_type, lesson_date, start_time, status,
					payment_source, coupon_id, approval_requested_at
				) VALUES (
					%s, 'FIRST_RIDE', '2026-08-20', '09:00:00',
					'pending_admin_approval', 'coupon', %s, '2026-08-14 10:00:00'
				)
				""".formatted(memberId, couponId));
			final long reservationId = lastInsertId(statement);
			statement.execute("""
				INSERT INTO coupon_usage_logs (
					coupon_id, reservation_id, member_id, action, count_delta,
					occurred_at, actor_type
				) VALUES (%s, %s, %s, 'held', 1, '2026-08-14 10:00:00', 'member')
				""".formatted(couponId, reservationId, memberId));
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(queryLong(statement, """
				SELECT COUNT(*)
				FROM coupon_usage_logs
				WHERE coupon_owner_member_id = member_id
				  AND family_group_id IS NULL
				""")).isOne();
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	@Test
	void 가족_Coupon_예약과_snapshot은_예약자와_원소유자를_분리한다() {
		final long reservationMemberId = insertMember("migration-family-user");
		final long ownerId = insertMember("migration-family-owner");
		final long groupId = insertGroup();
		insertMembership(groupId, reservationMemberId);
		insertMembership(groupId, ownerId);
		final long couponId = insertCoupon(ownerId);
		final long reservationId = insertReservation(reservationMemberId, couponId);

		jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				family_group_id, action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, ?, 'held', 1, '2026-08-14 10:00:00', 'member')
			""", couponId, reservationId, reservationMemberId, ownerId, groupId);

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM coupon_usage_logs
			WHERE reservation_id = ?
			  AND member_id = ?
			  AND coupon_owner_member_id = ?
			  AND family_group_id = ?
			""", Integer.class, reservationId, reservationMemberId, ownerId, groupId)).isOne();
	}

	@Test
	void 가족_Coupon_snapshot은_공유_사용과_자기_사용_shape를_강제한다() {
		final long reservationMemberId = insertMember("migration-shape-user");
		final long ownerId = insertMember("migration-shape-owner");
		final long groupId = insertGroup();
		insertMembership(groupId, reservationMemberId);
		insertMembership(groupId, ownerId);
		final long couponId = insertCoupon(ownerId);
		final long reservationId = insertReservation(reservationMemberId, couponId);

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				family_group_id, action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, NULL, 'held', 1, '2026-08-14 10:00:00', 'member')
			""", couponId, reservationId, reservationMemberId, ownerId))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_coupon_usage_logs_family_snapshot");
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO coupon_usage_logs (
				coupon_id, reservation_id, member_id, coupon_owner_member_id,
				family_group_id, action, count_delta, occurred_at, actor_type
			) VALUES (?, ?, ?, ?, ?, 'held', 1, '2026-08-14 10:00:00', 'member')
			""", couponId, reservationId, ownerId, ownerId, groupId))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_coupon_usage_logs_family_snapshot");
	}

	@Test
	void 가족_Coupon_선택용_NULL_rank와_복합_index를_생성한다() {
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = 'coupons'
			  AND column_name = 'expiry_null_rank'
			  AND extra LIKE '%STORED GENERATED%'
			""", Integer.class)).isOne();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'coupons'
			  AND index_name = 'idx_coupons_family_selection'
			""", Integer.class)).isEqualTo(6);
	}

	private long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '가족 Coupon 회원', '010-0000-0000')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertGroup() {
		jdbcTemplate.update("INSERT INTO family_groups (name) VALUES ('Migration 가족')");
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertMembership(long groupId, long memberId) {
		jdbcTemplate.update("""
			INSERT INTO family_memberships (family_group_id, member_id)
			VALUES (?, ?)
			""", groupId, memberId);
	}

	private long insertCoupon(long ownerId) {
		jdbcTemplate.update("""
			INSERT INTO coupons (
				member_id, coupon_type, total_count, remaining_count, created_by
			) VALUES (?, 'general', 10, 10, 'migration-admin')
			""", ownerId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertReservation(long reservationMemberId, long couponId) {
		jdbcTemplate.update("""
			INSERT INTO reservations (
				member_id, class_type, lesson_date, start_time, status,
				payment_source, coupon_id, approval_requested_at
			) VALUES (?, 'FIRST_RIDE', '2026-08-20', '09:00:00',
				'pending_admin_approval', 'coupon', ?, '2026-08-14 10:00:00')
			""", reservationMemberId, couponId);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private String createDatabase(String databaseName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			rootJdbcUrl("mysql"),
			"root",
			mysqlContainer.getPassword());
			Statement statement = connection.createStatement()) {
			statement.execute("DROP DATABASE IF EXISTS " + databaseName);
			statement.execute("CREATE DATABASE " + databaseName);
		}
		return rootJdbcUrl(databaseName);
	}

	private Connection connection(String databaseUrl) throws SQLException {
		return DriverManager.getConnection(databaseUrl, "root", mysqlContainer.getPassword());
	}

	private Flyway flyway(String databaseUrl, String targetVersion) {
		return Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion(targetVersion))
			.load();
	}

	private long lastInsertId(Statement statement) throws SQLException {
		return queryLong(statement, "SELECT LAST_INSERT_ID()");
	}

	private long queryLong(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private String currentMigrationVersion(Statement statement) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT version
			FROM flyway_schema_history
			WHERE success = TRUE
			ORDER BY installed_rank DESC
			LIMIT 1
			""")) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}
}
