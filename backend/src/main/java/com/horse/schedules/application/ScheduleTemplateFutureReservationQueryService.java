package com.horse.schedules.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.ReservationStatus;
import com.horse.reservations.infrastructure.ReservationRepository;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.RegularScheduleTemplateRepository;

@Service
public class ScheduleTemplateFutureReservationQueryService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<String> OCCUPYING_STATUS_VALUES = ReservationStatus.occupyingStatuses()
		.stream()
		.map(ReservationStatus::databaseValue)
		.toList();

	private final Clock clock;
	private final RegularScheduleTemplateRepository templateRepository;
	private final ReservationRepository reservationRepository;

	public ScheduleTemplateFutureReservationQueryService(
		Clock clock,
		RegularScheduleTemplateRepository templateRepository,
		ReservationRepository reservationRepository
	) {
		this.clock = clock;
		this.templateRepository = templateRepository;
		this.reservationRepository = reservationRepository;
	}

	@Transactional(readOnly = true)
	public ScheduleTemplateFutureReservationsResult find(long templateId) {
		templateRepository.findById(templateId)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_TEMPLATE_NOT_FOUND));
		final LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE);
		final List<ScheduleTemplateFutureReservationView> reservations = reservationRepository
			.findFutureOccupyingByTemplateId(
				templateId,
				now.toLocalDate(),
				now.toLocalTime(),
				OCCUPYING_STATUS_VALUES).stream()
			.map(ScheduleTemplateFutureReservationView::from)
			.toList();
		return new ScheduleTemplateFutureReservationsResult(
			templateId,
			reservations);
	}
}
