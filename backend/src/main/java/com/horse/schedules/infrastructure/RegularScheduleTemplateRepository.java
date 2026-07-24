package com.horse.schedules.infrastructure;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.horse.schedules.domain.RegularScheduleTemplate;

public interface RegularScheduleTemplateRepository extends JpaRepository<RegularScheduleTemplate, Long> {

	List<RegularScheduleTemplate> findAllByDayOfWeekOrderByStartTimeAscIdAsc(
		DayOfWeek dayOfWeek
	);

	Optional<RegularScheduleTemplate> findByDayOfWeekAndStartTime(
		DayOfWeek dayOfWeek,
		LocalTime startTime
	);

	boolean existsByDayOfWeekAndStartTimeAndIdNot(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		Long id
	);
}
