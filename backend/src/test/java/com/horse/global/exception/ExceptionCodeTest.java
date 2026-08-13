package com.horse.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExceptionCodeTest {

	private static final String DOMAIN_PREFIX_PATTERN =
		"^(MEMBER|FAMILY|AUTH|RESERVATION|COUPON|TIMESLOT|SCHEDULE|HORSE|COMMON)_[A-Z0-9_]+$";

	@Test
	void 예외_코드는_도메인_접두사를_사용하고_중복되지_않는다() {
		assertThat(ExceptionCode.values())
			.extracting(ExceptionCode::code)
			.doesNotHaveDuplicates()
			.allMatch(code -> code.matches(DOMAIN_PREFIX_PATTERN));
	}

}
