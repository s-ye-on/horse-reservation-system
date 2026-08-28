package com.horse.reservations.application;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.MonthlyRideMemberCountProjection;
import com.horse.reservations.infrastructure.ReservationRepository;

@Service
public class AdminMonthlyRideStatisticsService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;

	public AdminMonthlyRideStatisticsService(Clock clock, ReservationRepository reservationRepository) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(readOnly = true)
	public AdminMonthlyRideStatisticsResult getStatistics(YearMonth month, String rideType) {
		final YearMonth resolvedMonth = month == null ? YearMonth.now(clock) : month;
		final MonthlyRideType resolvedRideType = MonthlyRideType.from(rideType);
		final List<MonthlyRideMemberCountProjection> memberCounts =
			reservationRepository.findMonthlyCompletedRideCounts(
				resolvedMonth.atDay(1),
				resolvedMonth.plusMonths(1).atDay(1),
				ReservationStatus.COMPLETED,
				resolvedRideType.ridingClasses());
		final long totalCompletedRideCount = memberCounts.stream()
			.mapToLong(MonthlyRideMemberCountProjection::getCompletedRideCount)
			.sum();
		final long topCompletedRideCount = memberCounts.isEmpty()
			? 0L
			: memberCounts.getFirst().getCompletedRideCount();
		final List<AdminMonthlyRideLeaderResult> leaders = memberCounts.stream()
			.takeWhile(count -> count.getCompletedRideCount() == topCompletedRideCount)
			.map(count -> new AdminMonthlyRideLeaderResult(
				count.getMemberId(),
				count.getMemberName(),
				count.getCompletedRideCount()))
			.toList();

		return new AdminMonthlyRideStatisticsResult(
			resolvedMonth,
			resolvedRideType,
			totalCompletedRideCount,
			topCompletedRideCount,
			leaders);
	}
}
