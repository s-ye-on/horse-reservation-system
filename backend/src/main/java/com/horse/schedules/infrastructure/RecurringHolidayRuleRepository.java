package com.horse.schedules.infrastructure;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.horse.schedules.domain.RecurringHolidayRule;

public interface RecurringHolidayRuleRepository extends JpaRepository<RecurringHolidayRule, Long> {

	@Query("""
		SELECT (COUNT(rule) > 0)
		FROM RecurringHolidayRule rule
		WHERE rule.active = true
		  AND rule.dayOfWeek = :dayOfWeek
		  AND rule.effectiveFrom <= :candidateEffectiveTo
		  AND COALESCE(rule.effectiveTo, :maximumDate) >= :candidateEffectiveFrom
		  AND (:excludedId IS NULL OR rule.id <> :excludedId)
		""")
	boolean existsActiveOverlap(
		@Param("dayOfWeek") DayOfWeek dayOfWeek,
		@Param("candidateEffectiveFrom") LocalDate candidateEffectiveFrom,
		@Param("candidateEffectiveTo") LocalDate candidateEffectiveTo,
		@Param("maximumDate") LocalDate maximumDate,
		@Param("excludedId") Long excludedId
	);
}
