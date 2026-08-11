package com.horse.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.horse.auth.UserRole;
import com.horse.auth.domain.exception.AuthException;

class AuthDomainTest {

	@Test
	void 이메일은_공백을_제거하고_소문자로_정규화한다() {
		assertThat(AuthCredentialPolicy.normalizeEmail("  MEMBER@Example.COM "))
			.isEqualTo("member@example.com");
	}

	@Test
	void 비밀번호는_8자부터_허용한다() {
		assertThat(AuthCredentialPolicy.isSupportedPassword("12345678")).isTrue();
		assertThat(AuthCredentialPolicy.isSupportedPassword("1234567")).isFalse();
	}

	@Test
	void 회원_계정은_회원_역할과_활성_상태로_생성한다() {
		final AuthAccount account = AuthAccount.createMember(
			1L,
			"member-subject",
			"member@example.com",
			"{bcrypt}encoded"
		);

		assertThat(account.getRole()).isEqualTo(UserRole.MEMBER);
		assertThat(account.getStatus()).isEqualTo(AuthAccountStatus.ACTIVE);
		assertThat(account.getMemberId()).isEqualTo(1L);
		assertThat(account.canLogin()).isTrue();
	}

	@Test
	void 비활성_차단_탈퇴_계정은_로그인할_수_없다() {
		final AuthAccount inactive = account("inactive");
		final AuthAccount blocked = account("blocked");
		final AuthAccount withdrawn = account("withdrawn");

		inactive.deactivate();
		blocked.block();
		withdrawn.withdraw();

		assertThat(inactive.canLogin()).isFalse();
		assertThat(blocked.canLogin()).isFalse();
		assertThat(withdrawn.canLogin()).isFalse();
	}

	@Test
	void Refresh_Session은_회전하면_다시_사용할_수_없다() {
		final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 9, 0);
		final RefreshTokenSession session = RefreshTokenSession.create(
			1L,
			"a".repeat(64),
			"11111111-1111-1111-1111-111111111111",
			now,
			now.plusDays(30)
		);

		session.rotate(now.plusMinutes(1));

		assertThat(session.getStatus()).isEqualTo(RefreshTokenSessionStatus.ROTATED);
		assertThat(session.isUsableAt(now.plusMinutes(2))).isFalse();
		assertThatThrownBy(() -> session.rotate(now.plusMinutes(2)))
			.isInstanceOf(AuthException.class);
	}

	private AuthAccount account(String subject) {
		return AuthAccount.createMember(
			1L,
			subject,
			subject + "@example.com",
			"{bcrypt}encoded"
		);
	}
}
