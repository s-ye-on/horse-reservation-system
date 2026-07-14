package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.infrastructure.MemberRepository;
import com.horse.reservations.domain.Reservation;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class AdminPendingPaymentQueryService {

	private static final Set<ReservationStatus> OPERATION_STATUSES = Set.of(
		ReservationStatus.PENDING_PAYMENT,
		ReservationStatus.PAYMENT_EXPIRED);

	private final Clock clock;
	private final ReservationRepository reservationRepository;
	private final MemberRepository memberRepository;

	public AdminPendingPaymentQueryService(
		Clock clock,
		ReservationRepository reservationRepository,
		MemberRepository memberRepository
	) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
		this.memberRepository = memberRepository;
	}

	@Transactional(readOnly = true)
	public List<AdminPendingPaymentResult> getPendingPayments() {
		final List<Reservation> reservations = reservationRepository
			.findPendingPaymentOperations(OPERATION_STATUSES);
		final Map<Long, Member> members = memberRepository.findAllById(reservations.stream()
			.map(Reservation::getMemberId)
			.toList()).stream()
			.collect(Collectors.toMap(Member::getId, Function.identity()));
		final LocalDateTime now = LocalDateTime.now(clock);
		return reservations.stream()
			.map(reservation -> AdminPendingPaymentResult.from(
				reservation,
				getMember(members, reservation.getMemberId()),
				now))
			.toList();
	}

	private Member getMember(Map<Long, Member> members, Long memberId) {
		final Member member = members.get(memberId);
		if (member == null) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE);
		}
		return member;
	}
}
