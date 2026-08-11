package com.horse.auth.infrastructure;

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
import org.testcontainers.mysql.MySQLContainer;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class AuthMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "34";
	private static final String CURRENT_VERSION = "35";

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V34에서_V35로_올리면_기존_회원은_보존하고_가짜_자격_증명을_생성하지_않는다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_16a_auth_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('existing-member', '기존 회원', '010-0000-0000')
				""");
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(count(statement, "SELECT COUNT(*) FROM members")).isOne();
			assertThat(count(statement, "SELECT COUNT(*) FROM auth_accounts")).isZero();
			assertThat(count(statement, "SELECT COUNT(*) FROM refresh_token_sessions")).isZero();
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	@Test
	void 빈_DB에_V35까지_적용하면_인증_제약과_Refresh_원장이_생성된다() throws Exception {
		final String databaseUrl = createDatabase("horse_m31_16a_auth_fresh");
		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(authTableCount(statement)).isEqualTo(2L);
			assertThat(indexColumns(statement, "auth_accounts", "uk_auth_accounts_normalized_email"))
				.isEqualTo("normalized_email");
			assertThat(indexColumns(statement, "auth_accounts", "uk_auth_accounts_member"))
				.isEqualTo("member_id");
			assertThat(indexColumns(statement, "refresh_token_sessions", "uk_refresh_token_sessions_hash"))
				.isEqualTo("token_hash");
			assertThat(indexColumns(statement, "refresh_token_sessions", "uk_refresh_token_sessions_parent"))
				.isEqualTo("parent_session_id");
			assertThat(indexColumns(
				statement,
				"refresh_token_sessions",
				"idx_refresh_token_sessions_account_status_expiry"
			)).isEqualTo("auth_account_id,status,expires_at,id");
			assertThat(indexColumns(statement, "refresh_token_sessions", "idx_refresh_token_sessions_family"))
				.isEqualTo("family_id,id");
			assertThat(indexColumns(statement, "refresh_token_sessions", "idx_refresh_token_sessions_expiry"))
				.isEqualTo("expires_at,id");
			assertThat(columnCollation(statement, "auth_accounts", "normalized_email"))
				.isEqualTo("utf8mb4_bin");
			assertThat(columnCollation(statement, "refresh_token_sessions", "token_hash"))
				.isEqualTo("ascii_bin");
			insertAdmin(statement, "admin-one@example.com", "INITIAL_ADMIN");
			assertThatThrownBy(() -> insertAdmin(statement, "admin-two@example.com", "INITIAL_ADMIN"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("uk_auth_accounts_bootstrap_key");
			assertThatThrownBy(() -> insertAdmin(statement, "admin-one@example.com", null))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("uk_auth_accounts_normalized_email");
			assertThatThrownBy(() -> insertAdmin(statement, "invalid-bootstrap@example.com", "OTHER"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("chk_auth_accounts_bootstrap");
			final long memberId = insertMember(statement, "auth-migration-member");
			insertMemberAccount(statement, memberId, "member-one@example.com");
			assertThatThrownBy(() -> insertMemberAccount(statement, memberId, "member-two@example.com"))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("uk_auth_accounts_member");
			final long invalidAdminMemberId = insertMember(statement, "invalid-admin-member");
			assertThatThrownBy(() -> insertAdminWithMember(statement, invalidAdminMemberId))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("chk_auth_accounts_member_role");
			final long parentSessionId = insertRefreshSession(statement, "a".repeat(64), null);
			assertThatThrownBy(() -> insertRefreshSession(statement, "a".repeat(64)))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("uk_refresh_token_sessions_hash");
			insertRefreshSession(statement, "b".repeat(64), parentSessionId);
			assertThatThrownBy(() -> insertRefreshSession(statement, "c".repeat(64), parentSessionId))
				.isInstanceOf(SQLException.class)
				.hasMessageContaining("uk_refresh_token_sessions_parent");
		}
	}

	private void insertAdmin(Statement statement, String email, String bootstrapKey) throws SQLException {
		final String bootstrapValue = bootstrapKey == null ? "NULL" : "'" + bootstrapKey + "'";
		statement.execute("""
			INSERT INTO auth_accounts (
				auth_subject, normalized_email, password_hash, role, status, bootstrap_key
			) VALUES (
				UUID(), '%s', '{bcrypt}test-only-hash', 'ADMIN', 'ACTIVE', %s
			)
			""".formatted(email, bootstrapValue));
	}

	private long insertMember(Statement statement, String authSubject) throws SQLException {
		statement.execute("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES ('%s', 'Migration Member', '010-9999-9999')
			""".formatted(authSubject));
		return singleLong(statement, "SELECT id FROM members WHERE auth_subject = '" + authSubject + "'");
	}

	private void insertMemberAccount(Statement statement, long memberId, String email) throws SQLException {
		statement.execute("""
			INSERT INTO auth_accounts (
				member_id, auth_subject, normalized_email, password_hash, role, status
			) VALUES (
				%d, UUID(), '%s', '{bcrypt}test-only-hash', 'MEMBER', 'ACTIVE'
			)
			""".formatted(memberId, email));
	}

	private void insertAdminWithMember(Statement statement, long memberId) throws SQLException {
		statement.execute("""
			INSERT INTO auth_accounts (
				member_id, auth_subject, normalized_email, password_hash, role, status
			) VALUES (
				%d, UUID(), 'invalid-admin-member@example.com', '{bcrypt}test-only-hash', 'ADMIN', 'ACTIVE'
			)
			""".formatted(memberId));
	}

	private long insertRefreshSession(Statement statement, String tokenHash) throws SQLException {
		return insertRefreshSession(statement, tokenHash, null);
	}

	private long insertRefreshSession(Statement statement, String tokenHash, Long parentSessionId)
		throws SQLException {
		final String parentValue = parentSessionId == null ? "NULL" : parentSessionId.toString();
		statement.execute("""
			INSERT INTO refresh_token_sessions (
				auth_account_id, token_hash, family_id, parent_session_id, status, expires_at, created_at
			) SELECT id, '%s', UUID(), %s, 'ACTIVE', DATE_ADD(NOW(6), INTERVAL 30 DAY), NOW(6)
			  FROM auth_accounts
			 LIMIT 1
			""".formatted(tokenHash, parentValue));
		return singleLong(statement, "SELECT id FROM refresh_token_sessions WHERE token_hash = '" + tokenHash + "'");
	}

	private long singleLong(Statement statement, String sql) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getLong(1);
		}
	}

	private long authTableCount(Statement statement) throws SQLException {
		return count(statement, """
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name IN ('auth_accounts', 'refresh_token_sessions')
			""");
	}

	private String indexColumns(Statement statement, String tableName, String indexName) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = '%s'
			  AND index_name = '%s'
			""".formatted(tableName, indexName))) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private String columnCollation(Statement statement, String tableName, String columnName) throws SQLException {
		try (ResultSet resultSet = statement.executeQuery("""
			SELECT collation_name
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = '%s'
			  AND column_name = '%s'
			""".formatted(tableName, columnName))) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getString(1);
		}
	}

	private long count(Statement statement, String sql) throws SQLException {
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
		return DriverManager.getConnection(
			databaseUrl,
			"root",
			mysqlContainer.getPassword()
		);
	}

	private Flyway flyway(String databaseUrl, String targetVersion) {
		return Flyway.configure()
			.dataSource(databaseUrl, "root", mysqlContainer.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion(targetVersion))
			.load();
	}

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}
}
