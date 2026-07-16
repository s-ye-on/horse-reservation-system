package com.horse.reservations.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.domain.exception.ReservationException;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.reservations.infrastructure.ReservationSummaryProjection;

@Service
public class AdminReservationSummaryService {

	private final Clock clock;
	private final ReservationRepository reservationRepository;

	public AdminReservationSummaryService(Clock clock, ReservationRepository reservationRepository) {
		this.clock = clock;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(readOnly = true)
	public AdminReservationSummaryResult getSummary(LocalDate lessonDateFrom, LocalDate lessonDateTo) {
		final DateRange dateRange = resolveDateRange(lessonDateFrom, lessonDateTo);
		final List<ReservationSummaryProjection> projections = reservationRepository.findReservationSummary(
			dateRange.lessonDateFrom(),
			dateRange.lessonDateTo());
		final EnumMap<ReservationStatus, Long> totalStatusCounts = emptyStatusCounts();
		final Map<LocalDate, EnumMap<ReservationStatus, Long>> dailyStatusCounts = new TreeMap<>();

		projections.forEach(projection -> {
			totalStatusCounts.merge(projection.getStatus(), projection.getReservationCount(), Long::sum);
			dailyStatusCounts.computeIfAbsent(projection.getLessonDate(), ignored -> emptyStatusCounts())
				.merge(projection.getStatus(), projection.getReservationCount(), Long::sum);
		});

		final List<AdminReservationDailySummaryResult> dailyCounts = dailyStatusCounts.entrySet().stream()
			.map(entry -> new AdminReservationDailySummaryResult(
				entry.getKey(),
				totalCount(entry.getValue()),
				toStatusCounts(entry.getValue())))
			.toList();
		return new AdminReservationSummaryResult(
			dateRange.lessonDateFrom(),
			dateRange.lessonDateTo(),
			totalCount(totalStatusCounts),
			toStatusCounts(totalStatusCounts),
			dailyCounts);
	}

	private DateRange resolveDateRange(LocalDate lessonDateFrom, LocalDate lessonDateTo) {
		final LocalDate today = LocalDate.now(clock);
		final LocalDate resolvedFrom = lessonDateFrom == null
			? (lessonDateTo == null ? today : lessonDateTo)
			: lessonDateFrom;
		final LocalDate resolvedTo = lessonDateTo == null ? resolvedFrom : lessonDateTo;
		if (resolvedFrom.isAfter(resolvedTo)) {
			throw new ReservationException(ExceptionCode.RESERVATION_INVALID_QUERY_DATE_RANGE);
		}
		return new DateRange(resolvedFrom, resolvedTo);
	}

	private EnumMap<ReservationStatus, Long> emptyStatusCounts() {
		final EnumMap<ReservationStatus, Long> counts = new EnumMap<>(ReservationStatus.class);
		Arrays.stream(ReservationStatus.values()).forEach(status -> counts.put(status, 0L));
		return counts;
	}

	private List<ReservationStatusCountResult> toStatusCounts(Map<ReservationStatus, Long> counts) {
		return Arrays.stream(ReservationStatus.values())
			.map(status -> new ReservationStatusCountResult(status, counts.get(status)))
			.toList();
	}

	private long totalCount(Map<ReservationStatus, Long> counts) {
		return counts.values().stream().mapToLong(Long::longValue).sum();
	}

	private record DateRange(LocalDate lessonDateFrom, LocalDate lessonDateTo) {
	}
}
