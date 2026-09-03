package com.horse.families.infrastructure;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@DataJpaTest
class FamilyGroupMigrationIntegrationTest {

	private static final String PREVIOUS_VERSION = "35";
	private static final String CURRENT_VERSION = "36";

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MySQLContainer mysqlContainer;

	@Test
	void V35에서_V36으로_올리면_기존_회원은_보존되고_가족_원장은_비어_있다() throws Exception {
		final String databaseUrl = createDatabase("horse_m32_01_family_upgrade");
		flyway(databaseUrl, PREVIOUS_VERSION).migrate();
		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			statement.execute("""
				INSERT INTO members (auth_subject, name, phone)
				VALUES ('existing-family-member', '기존 회원', '010-0000-0000')
				""");
		}

		flyway(databaseUrl, CURRENT_VERSION).migrate();

		try (Connection connection = connection(databaseUrl);
			Statement statement = connection.createStatement()) {
			assertThat(count(statement, "SELECT COUNT(*) FROM members")).isOne();
			assertThat(count(statement, "SELECT COUNT(*) FROM family_groups")).isZero();
			assertThat(count(statement, "SELECT COUNT(*) FROM family_memberships")).isZero();
			assertThat(count(statement, "SELECT COUNT(*) FROM family_group_audit_logs")).isZero();
			assertThat(currentMigrationVersion(statement)).isEqualTo(CURRENT_VERSION);
		}
	}

	@Test
	void 빈_ACTIVE_그룹과_중복된_그룹_이름을_허용한다() {
		final long firstGroupId = insertGroup("행복 가족");
		final long secondGroupId = insertGroup("행복 가족");

		assertThat(firstGroupId).isNotEqualTo(secondGroupId);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM family_groups WHERE status = 'ACTIVE'",
			Integer.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM family_memberships",
			Integer.class)).isZero();
		assertThatThrownBy(() -> insertGroup("   "))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_family_groups_name");
	}

	@Test
	void 그룹_해제는_DISSOLVED_상태와_해제_시각을_함께_기록한다() {
		final long groupId = insertGroup("해제 가족");

		assertThatThrownBy(() -> jdbcTemplate.update(
			"UPDATE family_groups SET status = 'DISSOLVED' WHERE id = ?",
			groupId
		)).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_family_groups_dissolution");

		jdbcTemplate.update("""
			UPDATE family_groups
			SET status = 'DISSOLVED', dissolved_at = CURRENT_TIMESTAMP(6)
			WHERE id = ?
			""", groupId);

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM family_groups WHERE id = ? AND status = 'DISSOLVED' AND dissolved_at IS NOT NULL",
			Integer.class,
			groupId)).isOne();
	}

	@Test
	void 회원은_한_개의_ACTIVE_membership만_가질_수_있고_종료_후_다른_그룹에_가입할_수_있다() {
		final long firstGroupId = insertGroup("첫 번째 가족");
		final long secondGroupId = insertGroup("두 번째 가족");
		final long memberId = insertMember("family-membership-member");
		insertMembership(firstGroupId, memberId);

		assertThatThrownBy(() -> insertMembership(secondGroupId, memberId))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("uk_family_memberships_active_member");

		jdbcTemplate.update("""
			UPDATE family_memberships
			SET ended_at = CURRENT_TIMESTAMP(6)
			WHERE family_group_id = ? AND member_id = ? AND ended_at IS NULL
			""", firstGroupId, memberId);
		insertMembership(secondGroupId, memberId);

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM family_memberships WHERE member_id = ?",
			Integer.class,
			memberId)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM family_memberships WHERE member_id = ? AND ended_at IS NULL",
			Integer.class,
			memberId)).isOne();
	}

	@Test
	void 가족_감사는_필수_사유와_전후_상태를_요구한다() {
		final long groupId = insertGroup("감사 가족");
		insertAudit(groupId, "GROUP_CREATED", null, null, "{\"status\":\"ACTIVE\"}", "최초 생성");

		assertThatThrownBy(() -> insertAudit(
			groupId,
			"GROUP_CREATED",
			null,
			null,
			"{\"status\":\"ACTIVE\"}",
			"   "
		)).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_family_group_audit_logs_required_text");
		assertThatThrownBy(() -> insertAudit(
			groupId,
			"MEMBER_ADDED",
			null,
			"{\"active\":false}",
			"{\"active\":true}",
			"구성원 추가"
		)).isInstanceOf(DataAccessException.class)
			.hasMessageContaining("chk_family_group_audit_logs_shape");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM family_group_audit_logs WHERE family_group_id = ?",
			Integer.class,
			groupId)).isOne();
	}

	@Test
	void ACTIVE_membership_generated_guard와_불변_감사_스키마를_생성한다() {
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = 'family_memberships'
			  AND column_name = 'active_member_guard'
			  AND extra LIKE '%STORED GENERATED%'
			""", Integer.class)).isOne();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'family_memberships'
			  AND index_name = 'uk_family_memberships_active_member'
			  AND non_unique = 0
			""", Integer.class)).isOne();
		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = 'family_group_audit_logs'
			  AND column_name = 'updated_at'
			""", Integer.class)).isZero();
	}

	private long insertGroup(String name) {
		jdbcTemplate.update("INSERT INTO family_groups (name) VALUES (?)", name);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long insertMember(String authSubject) {
		jdbcTemplate.update("""
			INSERT INTO members (auth_subject, name, phone)
			VALUES (?, '가족 회원', '010-9999-9999')
			""", authSubject);
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void insertMembership(long groupId, long memberId) {
		jdbcTemplate.update("""
			INSERT INTO family_memberships (family_group_id, member_id)
			VALUES (?, ?)
			""", groupId, memberId);
	}

	private void insertAudit(
		long groupId,
		String action,
		Long memberId,
		String fromState,
		String toState,
		String reason
	) {
		jdbcTemplate.update("""
			INSERT INTO family_group_audit_logs (
				family_group_id, member_id, action, from_state, to_state, actor_auth_subject, reason
			) VALUES (?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), 'admin-subject', ?)
			""", groupId, memberId, action, fromState, toState, reason);
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

	private String rootJdbcUrl(String databaseName) {
		return mysqlContainer.getJdbcUrl().replace(mysqlContainer.getDatabaseName(), databaseName);
	}
}
