package com.horse.schedules.domain;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "regular_schedule_templates")
public class RegularScheduleTemplate {

	private static final int LESSON_DURATION_SECONDS = 45 * 60;
	private static final int MAX_TOTAL_CAPACITY = 8;
	private static final int MAX_ROUND_ARENA_CAPACITY = 4;
	private static final Set<String> REQUIRED_CLASS_NAMES = Set.of(
		"FIRST_RIDE",
		"ROUND_BEGINNER",
		"ROUND_TROT",
		"LARGE_ARENA_BEGINNER",
		"LARGE_ARENA_TROT",
		"DRESSAGE",
		"JUMPING");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "total_capacity", nullable = false)
	private byte totalCapacity;

	@Column(name = "round_arena_capacity", nullable = false)
	private byte roundArenaCapacity;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "class_capacity_json", nullable = false, columnDefinition = "json")
	private Map<String, Integer> classCapacities;

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

	protected RegularScheduleTemplate() {
	}

	private RegularScheduleTemplate(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities,
		String actorAuthSubject
	) {
		this.dayOfWeek = requireDayOfWeek(dayOfWeek);
		this.startTime = requireStartTime(startTime);
		this.endTime = requireEndTime(endTime);
		validateLessonInterval(this.startTime, this.endTime);
		validateCapacity(totalCapacity, roundArenaCapacity, classCapacities);
		this.totalCapacity = totalCapacity.byteValue();
		this.roundArenaCapacity = roundArenaCapacity.byteValue();
		this.classCapacities = Map.copyOf(classCapacities);
		this.active = true;
		this.createdBy = requireActor(actorAuthSubject);
		this.updatedBy = this.createdBy;
	}

	public static RegularScheduleTemplate create(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities,
		String actorAuthSubject
	) {
		return new RegularScheduleTemplate(
			dayOfWeek,
			startTime,
			endTime,
			totalCapacity,
			roundArenaCapacity,
			classCapacities,
			actorAuthSubject);
	}

	public void change(
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		LocalTime endTime,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities,
		String actorAuthSubject
	) {
		final DayOfWeek changedDayOfWeek = requireDayOfWeek(dayOfWeek);
		final LocalTime changedStartTime = requireStartTime(startTime);
		final LocalTime changedEndTime = requireEndTime(endTime);
		validateLessonInterval(changedStartTime, changedEndTime);
		validateCapacity(totalCapacity, roundArenaCapacity, classCapacities);
		this.dayOfWeek = changedDayOfWeek;
		this.startTime = changedStartTime;
		this.endTime = changedEndTime;
		this.totalCapacity = totalCapacity.byteValue();
		this.roundArenaCapacity = roundArenaCapacity.byteValue();
		this.classCapacities = Map.copyOf(classCapacities);
		this.updatedBy = requireActor(actorAuthSubject);
	}

	public void activate(String actorAuthSubject) {
		this.active = true;
		this.updatedBy = requireActor(actorAuthSubject);
	}

	public void deactivate(String actorAuthSubject) {
		this.active = false;
		this.updatedBy = requireActor(actorAuthSubject);
	}

	private static DayOfWeek requireDayOfWeek(DayOfWeek dayOfWeek) {
		if (dayOfWeek == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_DAY_OF_WEEK);
		}
		return dayOfWeek;
	}

	private static LocalTime requireStartTime(LocalTime startTime) {
		if (startTime == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_START_TIME);
		}
		return startTime;
	}

	private static LocalTime requireEndTime(LocalTime endTime) {
		if (endTime == null) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_END_TIME);
		}
		return endTime;
	}

	private static void validateLessonInterval(LocalTime startTime, LocalTime endTime) {
		if (!endTime.isAfter(startTime)
			|| endTime.toSecondOfDay() - startTime.toSecondOfDay() != LESSON_DURATION_SECONDS) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_LESSON_INTERVAL);
		}
	}

	private static void validateCapacity(
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities
	) {
		if (totalCapacity == null || totalCapacity < 0 || totalCapacity > MAX_TOTAL_CAPACITY) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_TOTAL_CAPACITY);
		}
		if (roundArenaCapacity == null
			|| roundArenaCapacity < 0
			|| roundArenaCapacity > MAX_ROUND_ARENA_CAPACITY
			|| roundArenaCapacity > totalCapacity) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_ROUND_ARENA_CAPACITY);
		}
		if (classCapacities == null
			|| !classCapacities.keySet().equals(REQUIRED_CLASS_NAMES)
			|| classCapacities.values().stream()
				.anyMatch(capacity -> capacity == null || capacity < 0 || capacity > MAX_TOTAL_CAPACITY)) {
			throw new ScheduleException(ExceptionCode.SCHEDULE_INVALID_CLASS_CAPACITIES);
		}
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

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public int getTotalCapacity() {
		return totalCapacity;
	}

	public int getRoundArenaCapacity() {
		return roundArenaCapacity;
	}

	public Map<String, Integer> getClassCapacities() {
		return Map.copyOf(classCapacities);
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
