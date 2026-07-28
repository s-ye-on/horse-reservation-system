package com.horse.reservations.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.horse.coupons.application.CouponHoldService;
import com.horse.members.domain.Member;
import com.horse.members.domain.RidingClass;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.PaymentSource;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationTimeSlotProjection;
import com.horse.timeslots.application.TimeSlotClosureCommandLockService;

class ReservationCompletionLockOrderTest {

	private static final Long RESERVATION_ID = 11L;
	private static final Long MEMBER_ID = 22L;
	private static final Long COUPON_ID = 33L;
	private static final LocalDate LESSON_DATE = LocalDate.of(2026, 8, 1);
	private static final LocalTime START_TIME = LocalTime.of(9, 0);

	@Test
	void 쿠폰_기승_완료는_예약_쿠폰_회원_순으로_잠근다() {
		final ReservationRepository reservationRepository = mock(ReservationRepository.class);
		final MemberRepository memberRepository = mock(MemberRepository.class);
		final CouponHoldService couponHoldService = mock(CouponHoldService.class);
		final ReservationScheduleDateLockService scheduleDateLockService =
			mock(ReservationScheduleDateLockService.class);
		final TimeSlotClosureCommandLockService closureLockService =
			mock(TimeSlotClosureCommandLockService.class);
		final ReservationTimeSlotProjection snapshot = mock(ReservationTimeSlotProjection.class);
		final Reservation reservation = mock(Reservation.class);
		final Member member = mock(Member.class);
		final Clock clock = Clock.fixed(
			Instant.parse("2026-08-01T01:00:00Z"),
			ZoneId.of("Asia/Seoul"));

		when(snapshot.getLessonDate()).thenReturn(LESSON_DATE);
		when(snapshot.getStartTime()).thenReturn(START_TIME);
		when(reservationRepository.findTimeSlotById(RESERVATION_ID)).thenReturn(Optional.of(snapshot));
		when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
		when(reservation.completeRide(any())).thenReturn(true);
		when(reservation.getId()).thenReturn(RESERVATION_ID);
		when(reservation.getMemberId()).thenReturn(MEMBER_ID);
		when(reservation.getCouponId()).thenReturn(COUPON_ID);
		when(reservation.getLessonDate()).thenReturn(LESSON_DATE);
		when(reservation.getRidingClass()).thenReturn(RidingClass.FIRST_RIDE);
		when(reservation.getPaymentSource()).thenReturn(PaymentSource.COUPON);
		when(reservation.getStatus()).thenReturn(ReservationStatus.COMPLETED);
		when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

		final ReservationCompletionService service = new ReservationCompletionService(
			clock,
			reservationRepository,
			memberRepository,
			couponHoldService,
			scheduleDateLockService,
			closureLockService);

		service.complete(RESERVATION_ID);

		final InOrder lockOrder = inOrder(
			reservationRepository,
			couponHoldService,
			memberRepository);
		lockOrder.verify(reservationRepository).findByIdForUpdate(RESERVATION_ID);
		lockOrder.verify(couponHoldService).use(
			any(),
			any(),
			any(),
			any(),
			any());
		lockOrder.verify(memberRepository).findByIdForUpdate(MEMBER_ID);
		verify(member).increaseGeneralRideCount();
	}
}
