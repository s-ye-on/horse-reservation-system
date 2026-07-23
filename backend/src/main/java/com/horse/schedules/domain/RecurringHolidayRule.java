package com.horse.schedules.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.horse.global.exception.ExceptionCode;
import com.horse.schedules.domain.exception.ScheduleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "recurring_holiday_rules")
public class RecurringHolidayRule {

	private static final int MAX_REASON_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	@Column(name = "reason", nullable = false)
	private String reason;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_by", nullable = false)
	private String createdBy;

	@Column(name = "updated_by", nullable = false)
	private String updatedBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected RecurringHolidayRule() {
	}

	private RecurringHolidayRule(
		DayOfWeek dayOfWeek,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		String reason,
		String actorAuthSubject
	) {
		this.dayOfWeek = requireDayOfWeek(dayOfWeek);
		this.effectiveFrom = requireEffectiveFrom(effectiveFrom);
		this.effectiveTo = effectiveTo;
		validateDateRange(this.effectiveFrom, this.effectiveTo);
		this.reason = requireReason(reason);
		this.active = true;
		this.createdBy = requireActor(actorAuthSubject);
		this.updatedBy = this.createdBy;
	}

	public static RecurringHolidayRule create(
		DayOfWeek dayOfWeek,
		LocalDate effectiveFrom,
		LocalDate effectiveTo,
		String reason,
		String actorAuthSubject
	) {
		return new RecurringHolidayRule(
			dayOfWeek,
			effectiveFrom,
			effectiveTo,
			reason,
			actorAuthSubject);
	}

	private static DayOfWeek requireDayOfWeek(DayOfWeek dayOfWeek) {
		if (dayOfWeek == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_DAY_OF_WEEK);
		}
		return dayOfWeek;
	}

	private static LocalDate requireEffectiveFrom(LocalDate effectiveFrom) {
		if (effectiveFrom == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_EFFECTIVE_DATE);
		}
		return effectiveFrom;
	}

	private static void validateDateRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
		if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_EFFECTIVE_DATE);
		}
	}

	private static String requireReason(String reason) {
		if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_REASON);
		}
		return reason;
	}

	private static String requireActor(String actorAuthSubject) {
		if (actorAuthSubject == null || actorAuthSubject.isBlank()) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_ACTOR);
		}
		return actorAuthSubject;
	}

	public Long getId() {
		return id;
	}

	public DayOfWeek getDayOfWeek() {
		return dayOfWeek;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public LocalDate getEffectiveTo() {
		return effectiveTo;
	}

	public String getReason() {
		return reason;
	}

	public boolean isActive() {
		return active;
	}

	public long getVersion() {
		return version;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
