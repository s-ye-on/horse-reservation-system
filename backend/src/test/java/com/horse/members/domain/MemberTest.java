package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void 특수_클래스_승인을_변경하면_대마장_이용_가능_여부도_바뀐다() {
		final Member member = Member.create("member-1", "홍길동", "010-1234-5678");

		member.changeDressageApproval(true);

		assertThat(member.isDressageApproved()).isTrue();
		assertThat(member.canUseLargeArena()).isTrue();

		member.changeDressageApproval(false);
		member.changeJumpingApproval(true);

		assertThat(member.isDressageApproved()).isFalse();
		assertThat(member.isJumpingApproved()).isTrue();
		assertThat(member.canUseLargeArena()).isTrue();

		member.changeJumpingApproval(false);

		assertThat(member.canUseLargeArena()).isFalse();
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
