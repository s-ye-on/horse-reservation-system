package com.horse.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ApiDateTimeTest {

	@Test
	void 서울_현지_시각을_양수_오프셋이_있는_절대_시점으로_변환한다() {
		final LocalDateTime localDateTime = LocalDateTime.of(2026, 7, 29, 13, 30);

		assertThat(ApiDateTime.toSeoulOffset(localDateTime))
			.isEqualTo(localDateTime.atOffset(ZoneOffset.ofHours(9)));
	}

	@Test
	void 서울_운영_시각은_계절과_무관하게_같은_오프셋을_사용한다() {
		assertThat(ApiDateTime.toSeoulOffset(LocalDateTime.of(2026, 1, 15, 9, 0)).getOffset())
			.isEqualTo(ZoneOffset.ofHours(9));
		assertThat(ApiDateTime.toSeoulOffset(LocalDateTime.of(2026, 7, 15, 9, 0)).getOffset())
			.isEqualTo(ZoneOffset.ofHours(9));
	}

	@Test
	void null_시각은_null로_유지한다() {
		assertThat(ApiDateTime.toSeoulOffset(null)).isNull();
	}
}
