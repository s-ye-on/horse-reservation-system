package com.horse.schedules.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.ReservationMemberDayGuard;
import com.horse.schedules.domain.ReservationMemberDayGuardId;
import com.horse.schedules.domain.exception.ScheduleException;

import jakarta.persistence.EntityManager;

@Repository
public class ReservationMemberDayGuardRepository {

	private static final String UPSERT_SQL = """
		INSERT INTO reservation_member_day_guards (member_id, lesson_date)
		VALUES (:memberId, :lessonDate)
		ON DUPLICATE KEY UPDATE created_at = created_at
		""";
	private static final String FIND_FOR_UPDATE_SQL = """
		SELECT *
		FROM reservation_member_day_guards
		WHERE member_id = :memberId
		  AND lesson_date = :lessonDate
		FOR UPDATE
		""";

	private final EntityManager entityManager;

	public ReservationMemberDayGuardRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public ReservationMemberDayGuard acquire(Long memberId, LocalDate lessonDate) {
		validateKey(memberId, lessonDate);
		entityManager.createNativeQuery(UPSERT_SQL)
			.setParameter("memberId", memberId)
			.setParameter("lessonDate", lessonDate)
			.executeUpdate();
		return (ReservationMemberDayGuard) entityManager.createNativeQuery(
			FIND_FOR_UPDATE_SQL,
			ReservationMemberDayGuard.class)
			.setParameter("memberId", memberId)
			.setParameter("lessonDate", lessonDate)
			.getSingleResult();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<ReservationMemberDayGuard> acquireAllOrdered(
		Collection<ReservationMemberDayGuardId> keys
	) {
		if (keys == null || keys.stream().anyMatch(key ->
			key == null
				|| key.getMemberId() == null
				|| key.getMemberId() < 1
				|| key.getLessonDate() == null)) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_MEMBER_DAY_GUARD);
		}
		return keys.stream()
			.sorted()
			.map(key -> acquire(key.getMemberId(), key.getLessonDate()))
			.toList();
	}

	private static void validateKey(Long memberId, LocalDate lessonDate) {
		if (memberId == null || memberId < 1 || lessonDate == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_MEMBER_DAY_GUARD);
		}
	}
}
