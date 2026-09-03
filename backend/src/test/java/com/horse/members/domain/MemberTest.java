package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

class MemberTest {

	@Test
	void 일반_기승_완료_횟수를_증가시킨다() {
		final Member member = Member.create("member-1", "홍길동", "010-1234-5678");

		member.increaseGeneralRideCount();

		assertThat(member.getGeneralRideCount()).isEqualTo(1);
	}

	@Test
	void 마장마술과_장애물_완료_횟수를_각각_증가시킨다() {
		final Member member = Member.create("member-1", "홍길동", "010-1234-5678");

		member.increaseDressageRideCount();
		member.increaseJumpingRideCount();

		assertThat(member.getGeneralRideCount()).isZero();
		assertThat(member.getDressageRideCount()).isEqualTo(1);
		assertThat(member.getJumpingRideCount()).isEqualTo(1);
	}

	@Test
	void 특수_클래스_승인을_해제해도_인정_progression과_대마장_이용_자격을_유지한다() {
		final Member member = Member.createManaged(
			"member-1",
			"홍길동",
			"010-1234-5678",
			LocalDateTime.of(2026, 8, 15, 9, 0));

		final int recognizedCredit = member.changeSpecialApprovals(true, false);

		assertThat(recognizedCredit).isEqualTo(26);
		assertThat(member.isDressageApproved()).isTrue();
		assertThat(member.getSpecialApprovalProgressionCredit()).isEqualTo(26);
		assertThat(member.progressionValue()).isEqualTo(26);
		assertThat(member.canUseLargeArena()).isTrue();

		final int additionalCredit = member.changeSpecialApprovals(false, true);

		assertThat(additionalCredit).isZero();
		assertThat(member.isDressageApproved()).isFalse();
		assertThat(member.isJumpingApproved()).isTrue();
		assertThat(member.getSpecialApprovalProgressionCredit()).isEqualTo(26);
		assertThat(member.canUseLargeArena()).isTrue();

		member.changeSpecialApprovals(false, false);

		assertThat(member.isDressageApproved()).isFalse();
		assertThat(member.isJumpingApproved()).isFalse();
		assertThat(member.getSpecialApprovalProgressionCredit()).isEqualTo(26);
		assertThat(member.progressionValue()).isEqualTo(26);
		assertThat(member.canUseLargeArena()).isTrue();
	}

	@ParameterizedTest
	@MethodSource("invalidMemberValues")
	void 유효하지_않은_회원_정보는_회원_예외를_발생시킨다(
		String authSubject,
		String name,
		String phone,
		ExceptionCode expectedCode
	) {
		assertThatThrownBy(() -> Member.create(authSubject, name, phone))
			.isInstanceOfSatisfying(MemberException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
					.isEqualTo(expectedCode.code()));
	}

	private static Stream<Arguments> invalidMemberValues() {
		return Stream.of(
			Arguments.of(" ", "홍길동", "010-1234-5678", ExceptionCode.MEMBER_INVALID_AUTH_SUBJECT),
			Arguments.of("member-1", " ", "010-1234-5678", ExceptionCode.MEMBER_INVALID_NAME),
			Arguments.of("member-1", "홍길동", null, ExceptionCode.MEMBER_INVALID_PHONE)
		);
	}

}
