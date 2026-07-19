package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class MemberReservationQueryService {

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final ReservationRepository reservationRepository;
	private final CouponRepository couponRepository;

	public MemberReservationQueryService(
		Clock clock,
		MemberRepository memberRepository,
		ReservationRepository reservationRepository,
		CouponRepository couponRepository
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.reservationRepository = reservationRepository;
		this.couponRepository = couponRepository;
	}

	@Transactional(readOnly = true)
	public List<MemberReservationResult> getMyReservations(String authSubject) {
		final Member member = memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		final LocalDateTime now = LocalDateTime.now(clock);
		final LocalDate today = now.toLocalDate();
		final List<Reservation> reservations = new ArrayList<>();
		reservations.addAll(
			reservationRepository
				.findAllByMemberIdAndLessonDateGreaterThanEqualOrderByLessonDateAscStartTimeAscIdAsc(
					member.getId(), today));
		reservations.addAll(
			reservationRepository
				.findAllByMemberIdAndLessonDateLessThanOrderByLessonDateDescStartTimeDescIdDesc(
					member.getId(), today));
		final Map<Long, Coupon> coupons = getCoupons(reservations);
		final List<MemberReservationResult> results = reservations.stream()
			.map(reservation -> toResult(reservation, coupons, now))
			.toList();
		return orderByDisplayGroup(results);
	}

	private Map<Long, Coupon> getCoupons(List<Reservation> reservations) {
		final List<Long> couponIds = reservations.stream()
			.map(Reservation::getCouponId)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.toList();
		if (couponIds.isEmpty()) {
			return Collections.emptyMap();
		}
		return couponRepository.findAllById(couponIds).stream()
			.collect(Collectors.toMap(Coupon::getId, Function.identity()));
	}

	private MemberReservationResult toResult(
		Reservation reservation,
		Map<Long, Coupon> coupons,
		LocalDateTime now
	) {
		final Coupon coupon = reservation.getCouponId() == null
			? null
			: coupons.get(reservation.getCouponId());
		if (reservation.getCouponId() != null && coupon == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE);
		}
		return MemberReservationResult.from(reservation, coupon, now);
	}

	private List<MemberReservationResult> orderByDisplayGroup(List<MemberReservationResult> results) {
		final java.util.Comparator<MemberReservationResult> ascending = java.util.Comparator
			.comparing(MemberReservationResult::lessonDate)
			.thenComparing(MemberReservationResult::startTime)
			.thenComparing(MemberReservationResult::reservationId);
		return Stream.concat(
			results.stream()
				.filter(result -> "UPCOMING".equals(result.displayGroup()))
				.sorted(ascending),
			results.stream()
				.filter(result -> "PAST".equals(result.displayGroup()))
				.sorted(ascending.reversed()))
			.toList();
	}
}
