package com.horse.reservations.domain;

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

class ActiveReservationUniquenessPolicyTest {

	private static final Long MEMBER_ID = 1L;

	@Test
	void 같은_회원의_활성_예약이_있으면_중복을_거부한다() {
		final Reservation activeReservation = Reservation.createSinglePaymentPending(
			MEMBER_ID,
			RidingClass.FIRST_RIDE,
			LocalDate.of(2026, 8, 1),
			LocalTime.of(9, 0),
			LocalDateTime.of(2026, 8, 1, 8, 0),
			LocalDateTime.of(2026, 8, 1, 7, 0));

		assertThatThrownBy(() -> ActiveReservationUniquenessPolicy.ensureNoDuplicate(
			MEMBER_ID,
			List.of(activeReservation)))
			.isInstanceOfSatisfying(ReservationException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
					.isEqualTo(ExceptionCode.RESERVATION_DUPLICATE_ACTIVE_TIME_SLOT.code()));
	}

	@Test
	void 다른_회원의_활성_예약만_있으면_허용한다() {
		final Reservation anotherMemberReservation = Reservation.createSinglePaymentPending(
			2L,
			RidingClass.FIRST_RIDE,
			LocalDate.of(2026, 8, 1),
			LocalTime.of(9, 0),
			LocalDateTime.of(2026, 8, 1, 8, 0),
			LocalDateTime.of(2026, 8, 1, 7, 0));

		assertThatCode(() -> ActiveReservationUniquenessPolicy.ensureNoDuplicate(
			MEMBER_ID,
			List.of(anotherMemberReservation)))
			.doesNotThrowAnyException();
	}

}
