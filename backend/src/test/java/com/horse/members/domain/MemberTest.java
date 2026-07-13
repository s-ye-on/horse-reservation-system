package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

class MemberTest {

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
