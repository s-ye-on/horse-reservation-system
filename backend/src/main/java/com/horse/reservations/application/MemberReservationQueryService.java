package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

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
	public MemberReservationPageResult getMyReservations(
		String authSubject,
		Integer page,
		Integer size
	) {
		final Member member = findMember(authSubject);
		final LocalDateTime now = LocalDateTime.now(clock);
		final Page<Reservation> reservations = reservationRepository.findMemberReservationsForDisplay(
			member.getId(),
			now.toLocalDate(),
			now.toLocalTime(),
			PageRequest.of(
				page == null ? DEFAULT_PAGE : page,
				size == null ? DEFAULT_SIZE : size));
		final Map<Long, Coupon> coupons = getCoupons(reservations.getContent());
		return new MemberReservationPageResult(
			reservations.getContent().stream()
				.map(reservation -> toResult(reservation, coupons, now))
				.toList(),
			reservations.getNumber(),
			reservations.getSize(),
			reservations.getTotalElements(),
			reservations.getTotalPages(),
			reservations.hasNext());
	}

	@Transactional(readOnly = true)
	public MemberReservationResult getMyReservation(String authSubject, Long reservationId) {
		final Member member = findMember(authSubject);
		final Reservation reservation = reservationRepository
			.findByIdAndMemberId(reservationId, member.getId())
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_MEMBER_MISMATCH));
		final Map<Long, Coupon> coupons = getCoupons(List.of(reservation));
		return toResult(reservation, coupons, LocalDateTime.now(clock));
	}

	private Member findMember(String authSubject) {
		return memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
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

}
