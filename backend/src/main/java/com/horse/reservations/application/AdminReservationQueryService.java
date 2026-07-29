package com.horse.reservations.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class AdminReservationQueryService {

	private static final Duration WARNING_AFTER = Duration.ofHours(2);
	private static final Duration CRITICAL_AFTER = Duration.ofHours(24);
	private static final Duration CRITICAL_BEFORE_LESSON = Duration.ofHours(24);

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final MemberRepository memberRepository;
	private final CouponRepository couponRepository;

	public AdminReservationQueryService(
		Clock clock,
		ReservationRepository reservationRepository,
		MemberRepository memberRepository,
		CouponRepository couponRepository
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.memberRepository = memberRepository;
		this.couponRepository = couponRepository;
	}

	@Transactional(readOnly = true)
	public AdminReservationPageResult getReservations(
		String status,
		LocalDate lessonDateFrom,
		LocalDate lessonDateTo,
		String classType,
		String keyword,
		String sort,
		Integer page,
		Integer size
	) {
		final AdminReservationQueryCriteria criteria = AdminReservationQueryCriteria.create(
			status,
			lessonDateFrom,
			lessonDateTo,
			classType,
			keyword,
			sort,
			page,
			size,
			LocalDate.now(clock));
		final Page<Reservation> reservations = reservationRepository.findAdminReservations(
			criteria.status(),
			criteria.lessonDateFrom(),
			criteria.lessonDateTo(),
			criteria.ridingClass(),
			criteria.keyword(),
			PageRequest.of(criteria.page(), criteria.size(), toSort(criteria.sort())));
		final Map<Long, Member> members = getMembers(reservations);
		final Map<Long, Coupon> coupons = getCoupons(reservations);
		final LocalDateTime now = LocalDateTime.now(clock);
		return new AdminReservationPageResult(
			reservations.getContent().stream()
				.map(reservation -> toResult(reservation, members, coupons, now))
				.toList(),
			reservations.getNumber(),
			reservations.getSize(),
			reservations.getTotalElements(),
			reservations.getTotalPages(),
			reservations.hasNext());
	}

	private Sort toSort(AdminReservationSort sort) {
		if (sort == AdminReservationSort.CREATED_AT_DESC) {
			return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
		}
		return Sort.by(
			Sort.Order.asc("lessonDate"),
			Sort.Order.asc("startTime"),
			Sort.Order.asc("id"));
	}

	@Transactional(readOnly = true)
	public AdminReservationResult getReservation(Long reservationId) {
		final Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_NOT_FOUND));
		final Member member = memberRepository.findById(reservation.getMemberId())
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
		final Coupon coupon = reservation.getCouponId() == null
			? null
			: couponRepository.findById(reservation.getCouponId())
				.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
		final LocalDateTime now = LocalDateTime.now(clock);
		return AdminReservationResult.from(
			reservation,
			member,
			coupon,
			getApprovalWarning(reservation, now),
			now);
	}

	private Map<Long, Member> getMembers(Page<Reservation> reservations) {
		return memberRepository.findAllById(reservations.getContent().stream()
			.map(Reservation::getMemberId)
			.toList()).stream()
			.collect(Collectors.toMap(Member::getId, Function.identity()));
	}

	private Map<Long, Coupon> getCoupons(Page<Reservation> reservations) {
		final List<Long> couponIds = reservations.getContent().stream()
			.map(Reservation::getCouponId)
			.filter(java.util.Objects::nonNull)
			.toList();
		if (couponIds.isEmpty()) {
			return Collections.emptyMap();
		}
		return couponRepository.findAllById(couponIds).stream()
			.collect(Collectors.toMap(Coupon::getId, Function.identity()));
	}

	private AdminReservationResult toResult(
		Reservation reservation,
		Map<Long, Member> members,
		Map<Long, Coupon> coupons,
		LocalDateTime now
	) {
		final Member member = members.get(reservation.getMemberId());
		if (member == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE);
		}
		final Coupon coupon = reservation.getCouponId() == null
			? null
			: coupons.get(reservation.getCouponId());
		if (reservation.getCouponId() != null && coupon == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE);
		}
		return AdminReservationResult.from(
			reservation,
			member,
			coupon,
			getApprovalWarning(reservation, now),
			now);
	}

	private ReservationApprovalWarningLevel getApprovalWarning(
		Reservation reservation,
		LocalDateTime now
	) {
		if (reservation.getStatus() != ReservationStatus.PENDING_ADMIN_APPROVAL) {
			return null;
		}
		final LocalDateTime lessonStart = LocalDateTime.of(
			reservation.getLessonDate(), reservation.getStartTime());
		if (now.isAfter(reservation.getApprovalRequestedAt().plus(CRITICAL_AFTER))
			|| !lessonStart.isAfter(now.plus(CRITICAL_BEFORE_LESSON))) {
			return ReservationApprovalWarningLevel.CRITICAL;
		}
		if (now.isAfter(reservation.getApprovalRequestedAt().plus(WARNING_AFTER))) {
			return ReservationApprovalWarningLevel.WARNING;
		}
		return ReservationApprovalWarningLevel.NORMAL;
	}
}
