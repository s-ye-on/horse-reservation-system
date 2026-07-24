package com.horse.reservations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.exception.ReservationException;

class ReservationIntervalOverlapPolicyTest {

	@Test
	void 완전히_같거나_앞뒤와_포함_구간이_겹치면_참을_반환한다() {
		final LocalTime existingStart = LocalTime.of(10, 0);
		final LocalTime existingEnd = LocalTime.of(10, 45);

		assertThat(ReservationIntervalOverlapPolicy.overlaps(
			existingStart,
			existingEnd,
			LocalTime.of(10, 0),
			LocalTime.of(10, 45))).isTrue();
		assertThat(ReservationIntervalOverlapPolicy.overlaps(
			existingStart,
			existingEnd,
			LocalTime.of(9, 30),
			LocalTime.of(10, 15))).isTrue();
		assertThat(ReservationIntervalOverlapPolicy.overlaps(
			existingStart,
			existingEnd,
			LocalTime.of(10, 30),
			LocalTime.of(11, 15))).isTrue();
		assertThat(ReservationIntervalOverlapPolicy.overlaps(
			existingStart,
			existingEnd,
			LocalTime.of(9, 30),
			LocalTime.of(11, 15))).isTrue();
		assertThat(ReservationIntervalOverlapPolicy.overlaps(
			existingStart,
			existingEnd,
			LocalTime.of(10, 15),
			LocalTime.of(10, 30))).isTrue();
	}

	@Test
	void 종료와_시작_경계가_맞닿은_반개방_구간은_겹치지_않는다() {
		assertThat(ReservationIntervalOverlapPolicy.overlaps(
			LocalTime.of(10, 0),
			LocalTime.of(10, 45),
			LocalTime.of(10, 45),
			LocalTime.of(11, 30))).isFalse();
		assertThat(ReservationIntervalOverlapPolicy.overlaps(
			LocalTime.of(10, 0),
			LocalTime.of(10, 45),
			LocalTime.of(9, 15),
			LocalTime.of(10, 0))).isFalse();
	}

	@Test
	void 겹치는_활성_예약이_있으면_통합_업무_예외를_반환한다() {
		final Reservation reservation = Reservation.createSinglePaymentPending(
			1L,
			RidingClass.FIRST_RIDE,
			LocalDate.of(2026, 8, 1),
			LocalTime.of(10, 0),
			LocalDateTime.of(2026, 8, 1, 9, 0),
			LocalDateTime.of(2026, 8, 1, 8, 0));

		assertThatThrownBy(() -> ReservationIntervalOverlapPolicy.ensureNoOverlap(List.of(reservation)))
			.isInstanceOfSatisfying(
				ReservationException.class,
				exception -> assertThat(exception.code())
					.isEqualTo(ExceptionCode.RESERVATION_OVERLAPPING_ACTIVE_RESERVATION.code()));
		assertThatCode(() -> ReservationIntervalOverlapPolicy.ensureNoOverlap(List.of()))
			.doesNotThrowAnyException();
	}
}
