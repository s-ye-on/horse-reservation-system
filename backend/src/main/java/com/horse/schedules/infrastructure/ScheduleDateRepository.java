package com.horse.schedules.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.schedules.domain.ScheduleDate;

public interface ScheduleDateRepository extends JpaRepository<ScheduleDate, Long> {

	Optional<ScheduleDate> findByScheduleDate(LocalDate scheduleDate);

	List<ScheduleDate> findAllByScheduleDateBetweenOrderByScheduleDateAsc(
		LocalDate startDate,
		LocalDate endDate
	);

	long countByScheduleDateBetween(LocalDate startDate, LocalDate endDate);

	long countByScheduleDateBetweenAndAppliedConfigVersion(
		LocalDate startDate,
		LocalDate endDate,
		long appliedConfigVersion
	);

	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
		SELECT *
		FROM schedule_dates
		FORCE INDEX (uk_schedule_dates_date)
		WHERE schedule_date BETWEEN :startDate AND :endDate
		ORDER BY schedule_date
		FOR UPDATE
		""", nativeQuery = true)
	List<ScheduleDate> findAllByScheduleDateBetweenForUpdate(
		@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate
	);

	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
		SELECT *
		FROM schedule_dates
		WHERE schedule_date = :scheduleDate
		FOR UPDATE
		""", nativeQuery = true)
	Optional<ScheduleDate> findByScheduleDateForUpdate(@Param("scheduleDate") LocalDate scheduleDate);

	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
		SELECT *
		FROM schedule_dates
		FORCE INDEX (uk_schedule_dates_date)
		WHERE schedule_date IN (:scheduleDates)
		ORDER BY schedule_date
		FOR UPDATE
		""", nativeQuery = true)
	List<ScheduleDate> findAllByScheduleDateInForUpdate(
		@Param("scheduleDates") Collection<LocalDate> scheduleDates
	);
}
