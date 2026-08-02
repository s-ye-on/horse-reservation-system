package com.horse.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.horse.TestcontainersConfiguration;
import com.horse.auth.domain.AuthAccount;
import com.horse.auth.domain.RefreshTokenSession;
import com.horse.auth.domain.RefreshTokenSessionStatus;
import com.horse.auth.domain.exception.AuthException;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.auth.infrastructure.RefreshTokenSessionRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenFamilyRevocationIntegrationTest {

	private static final String EMAIL = "family-revocation@example.com";
	private static final String PASSWORD = "family-revocation-password";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	AuthRegistrationService registrationService;

	@Autowired
	AuthSessionService sessionService;

	@Autowired
	AuthAccountRepository authAccountRepository;

	@Autowired
	RefreshTokenSessionRepository refreshTokenSessionRepository;

	@Autowired
	RefreshTokenCodec refreshTokenCodec;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void Refresh_Token_family_테스트_데이터를_초기화한다() {
		final List<Long> memberIds = jdbcTemplate.queryForList(
			"SELECT member_id FROM auth_accounts WHERE member_id IS NOT NULL",
			Long.class
		);
		jdbcTemplate.update("UPDATE refresh_token_sessions SET parent_session_id = NULL");
		jdbcTemplate.update("DELETE FROM refresh_token_sessions");
		jdbcTemplate.update("DELETE FROM auth_accounts");
		memberIds.forEach(memberId -> jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId));
	}

	@Test
	void 정상_회전은_같은_family와_parent_chain을_유지하며_C를_발급한다() {
		registerMember();
		final String firstToken = login();

		final String secondToken = sessionService.refresh(firstToken).refreshToken();
		final String thirdToken = sessionService.refresh(secondToken).refreshToken();

		final List<RefreshTokenSession> sessions = sessionsById();
		assertThat(sessions).hasSize(3);
		assertThat(sessions).extracting(RefreshTokenSession::getFamilyId).containsOnly(sessions.getFirst().getFamilyId());
		assertThat(sessions.getFirst().getParentSessionId()).isNull();
		assertThat(sessions.get(1).getParentSessionId()).isEqualTo(sessions.getFirst().getId());
		assertThat(sessions.get(2).getParentSessionId()).isEqualTo(sessions.get(1).getId());
		assertThat(sessions).extracting(RefreshTokenSession::getStatus).containsExactly(
			RefreshTokenSessionStatus.ROTATED,
			RefreshTokenSessionStatus.ROTATED,
			RefreshTokenSessionStatus.ACTIVE
		);
		assertThat(thirdToken).isNotBlank();
	}

	@Test
	void ROTATED_토큰_재사용은_같은_family의_모든_ACTIVE_Session을_폐기한다() {
		registerMember();
		final String firstToken = login();
		final String successorToken = sessionService.refresh(firstToken).refreshToken();
		final RefreshTokenSession firstSession = sessionFor(firstToken);
		final LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
		refreshTokenSessionRepository.saveAndFlush(RefreshTokenSession.create(
			firstSession.getAuthAccountId(),
			refreshTokenCodec.hash("defensive-active-session"),
			firstSession.getFamilyId(),
			now,
			now.plusDays(1)
		));

		assertInvalidRefresh(firstToken);
		assertInvalidRefresh(firstToken);
		assertInvalidRefresh(successorToken);

		assertThat(refreshTokenSessionRepository.findAll())
			.filteredOn(session -> session.getFamilyId().equals(firstSession.getFamilyId()))
			.noneMatch(session -> session.getStatus() == RefreshTokenSessionStatus.ACTIVE);
	}

	@Test
	void 재사용_폐기는_다른_로그인_family에_영향을_주지_않는다() {
		registerMember();
		final String compromisedToken = login();
		final String otherLoginToken = login();
		final String compromisedSuccessor = sessionService.refresh(compromisedToken).refreshToken();
		final String compromisedFamilyId = sessionFor(compromisedToken).getFamilyId();
		final String otherFamilyId = sessionFor(otherLoginToken).getFamilyId();

		assertInvalidRefresh(compromisedToken);
		final String otherSuccessor = sessionService.refresh(otherLoginToken).refreshToken();

		assertThat(compromisedFamilyId).isNotEqualTo(otherFamilyId);
		assertThat(sessionFor(compromisedSuccessor).getStatus()).isEqualTo(RefreshTokenSessionStatus.REVOKED);
		assertThat(sessionFor(otherSuccessor).getStatus()).isEqualTo(RefreshTokenSessionStatus.ACTIVE);
	}

	@Test
	void 알_수_없거나_만료된_토큰은_다른_family를_폐기하지_않는다() {
		registerMember();
		final String expiredToken = login();
		final String healthyToken = login();
		jdbcTemplate.update(
			"UPDATE refresh_token_sessions SET expires_at = DATE_SUB(NOW(6), INTERVAL 1 SECOND) WHERE id = ?",
			sessionFor(expiredToken).getId()
		);

		assertInvalidRefresh("unknown-refresh-token");
		assertInvalidRefresh(expiredToken);
		final String healthySuccessor = sessionService.refresh(healthyToken).refreshToken();

		assertThat(sessionFor(expiredToken).getStatus()).isEqualTo(RefreshTokenSessionStatus.ACTIVE);
		assertThat(sessionFor(healthySuccessor).getStatus()).isEqualTo(RefreshTokenSessionStatus.ACTIVE);
	}

	@Test
	void 일반_logout은_현재_Session만_멱등_폐기하고_다른_family를_유지한다() {
		registerMember();
		final String logoutToken = login();
		final String otherLoginToken = login();
		final RefreshTokenSession logoutSession = sessionFor(logoutToken);
		final LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
		final RefreshTokenSession sameFamilySession = refreshTokenSessionRepository.saveAndFlush(
			RefreshTokenSession.create(
				logoutSession.getAuthAccountId(),
				refreshTokenCodec.hash("logout-family-active-session"),
				logoutSession.getFamilyId(),
				now,
				now.plusDays(1)
			)
		);

		sessionService.logout(logoutToken);
		sessionService.logout(logoutToken);
		assertInvalidRefresh(logoutToken);
		assertThat(refreshTokenSessionRepository.findById(sameFamilySession.getId()).orElseThrow().getStatus())
			.isEqualTo(RefreshTokenSessionStatus.ACTIVE);
		final String otherSuccessor = sessionService.refresh(otherLoginToken).refreshToken();

		assertThat(sessionFor(logoutToken).getStatus()).isEqualTo(RefreshTokenSessionStatus.REVOKED);
		assertThat(sessionFor(otherSuccessor).getStatus()).isEqualTo(RefreshTokenSessionStatus.ACTIVE);
	}

	@Test
	void 차단된_계정의_refresh는_거부하되_family를_폐기하지_않는다() {
		registerMember();
		final String refreshToken = login();
		final AuthAccount account = authAccountRepository.findByNormalizedEmail(EMAIL).orElseThrow();
		account.block();
		authAccountRepository.saveAndFlush(account);

		assertInvalidRefresh(refreshToken);

		assertThat(sessionFor(refreshToken).getStatus()).isEqualTo(RefreshTokenSessionStatus.ACTIVE);
	}

	private void registerMember() {
		registrationService.signup(EMAIL, PASSWORD, "family 회원", "010-1234-5678");
	}

	private String login() {
		return sessionService.login(EMAIL, PASSWORD).refreshToken();
	}

	private void assertInvalidRefresh(String rawToken) {
		assertThatThrownBy(() -> sessionService.refresh(rawToken))
			.isInstanceOfSatisfying(AuthException.class, exception ->
				assertThat(exception.code()).isEqualTo("AUTH_INVALID_REFRESH_TOKEN"));
	}

	private RefreshTokenSession sessionFor(String rawToken) {
		final String tokenHash = refreshTokenCodec.hash(rawToken);
		return refreshTokenSessionRepository.findAll().stream()
			.filter(session -> session.getTokenHash().equals(tokenHash))
			.findFirst()
			.orElseThrow();
	}

	private List<RefreshTokenSession> sessionsById() {
		return refreshTokenSessionRepository.findAll().stream()
			.sorted(Comparator.comparing(RefreshTokenSession::getId))
			.toList();
	}
}
