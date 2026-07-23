package com.horse.schedules.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.horse.schedules.domain.RecurringHolidayRule;

public interface RecurringHolidayRuleRepository extends JpaRepository<RecurringHolidayRule, Long> {
}
