package com.horse.coupons.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class CouponRegistrationMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "39";
	private static final String CURRENT_VERSION = "40";

	private final MySQLContainer mysqlContainer;

	@Autowired
	CouponRegistrationMigrationIntegrationTest(MySQLContainer mysqlContainer) {
		this.mysqlContainer = mysqlContainer;
	}

	@Test
	void V39_쿠폰_횟수와_사용일은_V40_INT_컬럼으로_그대로_보존된다() throws Exception {
		final String databaseUrl = createDatabase("horse_coupon_registration_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('coupon-migration-member', '기존 회원', '010-0000-0000')
				""");
			statement.execute("""
				INSERT INTO coupons (
					member_id, coupon_type, total_count, remaining_count, held_count,
					first_used_at, expires_at, created_by
				) VALUES (
					LAST_INSERT_ID(), 'general', 200, 197, 0,
					'2026-07-03 00:00:00', '2026-10-03 00:00:00', 'migration-admin'
				)
				""");
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(queryLong(statement, "SELECT total_count FROM coupons")).isEqualTo(200);
			assertThat(queryLong(statement, "SELECT remaining_count FROM coupons")).isEqualTo(197);
			assertThat(queryString(statement, """
				SELECT column_type
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = 'coupons'
				  AND column_name = 'total_count'
				""")).isEqualTo("int unsigned");
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	private String createDatabase(String databaseName) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			rootJdbcUrl("mysql"), "root", mysqlContainer.getPassword());
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

	private long queryLong(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private String queryString(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String currentMigrationVersion(Statement statement) throws SQLException {
		return queryString(statement, """
			SELECT version
			FROM flyway_schema_history
			WHERE success = TRUE
			ORDER BY installed_rank DESC
			LIMIT 1
			""");
	}

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}
}
