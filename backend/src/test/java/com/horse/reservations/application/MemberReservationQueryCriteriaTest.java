package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.horse.reservations.domain.ReservationDisplayGroup;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;

class MemberReservationQueryCriteriaTest {

	@Test
	void 표시_그룹과_상태를_대소문자와_공백에_관계없이_해석한다() {
		final MemberReservationQueryCriteria criteria = MemberReservationQueryCriteria.create(
			" upcoming ",
			" CONFIRMED ",
			2,
			30);

		assertThat(criteria.displayGroup()).isEqualTo(ReservationDisplayGroup.UPCOMING);
		assertThat(criteria.status()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(criteria.page()).isEqualTo(2);
		assertThat(criteria.size()).isEqualTo(30);
	}

	@Test
	void 필터와_페이지를_생략하면_기존_기본값을_사용한다() {
		final MemberReservationQueryCriteria criteria = MemberReservationQueryCriteria.create(
			null,
			null,
			null,
			null);

		assertThat(criteria.displayGroup()).isNull();
		assertThat(criteria.status()).isNull();
		assertThat(criteria.page()).isZero();
		assertThat(criteria.size()).isEqualTo(20);
	}

	@Test
	void 잘못된_표시_그룹과_상태를_거부한다() {
		assertThatThrownBy(() ->
			MemberReservationQueryCriteria.create("FUTURE", null, 0, 20))
			.isInstanceOf(ReservationException.class)
			.extracting(exception -> ((ReservationException)exception).code())
			.isEqualTo("RESERVATION_INVALID_QUERY_DISPLAY_GROUP");
		assertThatThrownBy(() ->
			MemberReservationQueryCriteria.create(null, "waiting", 0, 20))
			.isInstanceOf(ReservationException.class)
			.extracting(exception -> ((ReservationException)exception).code())
			.isEqualTo("RESERVATION_INVALID_QUERY_STATUS");
	}
}
