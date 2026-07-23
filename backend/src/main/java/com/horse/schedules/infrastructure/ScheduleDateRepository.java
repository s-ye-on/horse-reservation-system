package com.horse.schedules.infrastructure;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.horse.schedules.domain.ScheduleDate;

public interface ScheduleDateRepository extends JpaRepository<ScheduleDate, Long> {

	Optional<ScheduleDate> findByScheduleDate(LocalDate scheduleDate);

	@Transactional(propagation = Propagation.MANDATORY)
	@Query(value = """
		SELECT *
		FROM schedule_dates
		WHERE schedule_date = :scheduleDate
		FOR UPDATE
		""", nativeQuery = true)
	Optional<ScheduleDate> findByScheduleDateForUpdate(@Param("scheduleDate") LocalDate scheduleDate);
}
