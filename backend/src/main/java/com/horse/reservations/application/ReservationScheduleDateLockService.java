package com.horse.reservations.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.ScheduleConfigGuard;
import com.horse.schedules.domain.ScheduleDate;
import com.horse.schedules.domain.exception.ScheduleException;
import com.horse.schedules.infrastructure.ReservationMemberDayGuardRepository;
import com.horse.schedules.infrastructure.ScheduleConfigGuardRepository;
import com.horse.schedules.infrastructure.ScheduleDateRepository;

@Service
public class ReservationScheduleDateLockService {

	private final ScheduleConfigGuardRepository configGuardRepository;
	private final ScheduleDateRepository scheduleDateRepository;
	private final ReservationMemberDayGuardRepository memberDayGuardRepository;

	public ReservationScheduleDateLockService(
		ScheduleConfigGuardRepository configGuardRepository,
		ScheduleDateRepository scheduleDateRepository,
		ReservationMemberDayGuardRepository memberDayGuardRepository
	) {
		this.configGuardRepository = configGuardRepository;
		this.scheduleDateRepository = scheduleDateRepository;
		this.memberDayGuardRepository = memberDayGuardRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForActiveTransition(LocalDate lessonDate) {
		findDateForUpdate(lessonDate).ensureReservationInflowAllowed();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForMemberCancellation(LocalDate lessonDate, Long memberId) {
		findDateForUpdate(lessonDate).ensureMemberCancellationAllowed();
		memberDayGuardRepository.acquire(memberId, lessonDate);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForSafeExit(LocalDate lessonDate, Long memberId) {
		findDateForUpdate(lessonDate);
		memberDayGuardRepository.acquire(memberId, lessonDate);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForReentry(LocalDate lessonDate, Long memberId) {
		final ScheduleConfigGuard configGuard = configGuardRepository.findSingletonForShare();
		configGuard.ensureActive();
		final ScheduleDate scheduleDate = findDateForUpdate(lessonDate);
		scheduleDate.ensureAppliedConfigVersion(configGuard.getActiveVersion());
		scheduleDate.ensureReservationInflowAllowed();
		memberDayGuardRepository.acquire(memberId, lessonDate);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForChange(
		Collection<LocalDate> lessonDates,
		LocalDate targetLessonDate,
		Long memberId
	) {
		final ScheduleConfigGuard configGuard = configGuardRepository.findSingletonForShare();
		configGuard.ensureActive();
		final List<LocalDate> sortedLessonDates = distinctSorted(lessonDates);
		final List<ScheduleDate> scheduleDates = scheduleDateRepository
			.findAllByScheduleDateInForUpdate(sortedLessonDates);
		if (scheduleDates.size() != sortedLessonDates.size()) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_OCCURRENCE_SYNC_INCOMPLETE);
		}
		for (ScheduleDate scheduleDate : scheduleDates) {
			scheduleDate.ensureAppliedConfigVersion(configGuard.getActiveVersion());
			if (scheduleDate.getScheduleDate().equals(targetLessonDate)) {
				scheduleDate.ensureReservationInflowAllowed();
			}
		}
		for (LocalDate lessonDate : sortedLessonDates) {
			memberDayGuardRepository.acquire(memberId, lessonDate);
		}
	}

	private ScheduleDate findDateForUpdate(LocalDate lessonDate) {
		return scheduleDateRepository.findByScheduleDateForUpdate(lessonDate)
			.orElseThrow(() -> new ScheduleException(ExceptionCode.SCHEDULE_INVALID_SCHEDULE_DATE));
	}

	private List<LocalDate> distinctSorted(Collection<LocalDate> lessonDates) {
		return lessonDates.stream()
			.distinct()
			.sorted(Comparator.naturalOrder())
			.toList();
	}
}
