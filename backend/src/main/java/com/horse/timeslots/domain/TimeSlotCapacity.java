package com.horse.timeslots.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.timeslots.domain.exception.TimeSlotException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "time_slot_capacities")
public class TimeSlotCapacity {

	private static final int MAX_TOTAL_CAPACITY = 8;
	private static final int MAX_ROUND_ARENA_CAPACITY = 4;
	private static final int LESSON_DURATION_MINUTES = 45;
	private static final Set<String> REQUIRED_CLASS_NAMES = Set.of(
		"FIRST_RIDE",
		"ROUND_BEGINNER",
		"ROUND_TROT",
		"LARGE_ARENA_BEGINNER",
		"LARGE_ARENA_TROT",
		"DRESSAGE",
		"JUMPING");
	private static final Set<RidingClass> ROUND_ARENA_CLASSES = Set.of(
		RidingClass.FIRST_RIDE,
		RidingClass.ROUND_BEGINNER,
		RidingClass.ROUND_TROT);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "lesson_date", nullable = false)
	private LocalDate lessonDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false)
	private TimeSlotSource source;

	@Column(name = "total_capacity", nullable = false)
	private byte totalCapacity;

	@Column(name = "round_arena_capacity", nullable = false)
	private byte roundArenaCapacity;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "class_capacity_json", nullable = false, columnDefinition = "json")
	private Map<String, Integer> classCapacities;

	@Column(name = "is_closed", nullable = false)
	private boolean closed;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected TimeSlotCapacity() {
	}

	private TimeSlotCapacity(
		LocalDate lessonDate,
		LocalTime startTime,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities
	) {
		this.lessonDate = requireLessonDate(lessonDate);
		this.startTime = requireStartTime(startTime);
		this.endTime = calculateEndTime(this.startTime);
		this.source = TimeSlotSource.MANUAL;
		initializeCapacity(totalCapacity, roundArenaCapacity, classCapacities);
	}

	public static TimeSlotCapacity create(
		LocalDate lessonDate,
		LocalTime startTime,
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities
	) {
		return new TimeSlotCapacity(
			lessonDate,
			startTime,
			totalCapacity,
			roundArenaCapacity,
			classCapacities);
	}

	private void initializeCapacity(
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities
	) {
		validateTotalCapacity(totalCapacity);
		validateRoundArenaCapacity(roundArenaCapacity, totalCapacity);
		validateClassCapacities(classCapacities);
		this.totalCapacity = totalCapacity.byteValue();
		this.roundArenaCapacity = roundArenaCapacity.byteValue();
		this.classCapacities = Map.copyOf(classCapacities);
	}

	public void changeCapacity(
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities,
		int totalOccupied,
		int roundArenaOccupied,
		Map<RidingClass, Integer> classOccupied
	) {
		validateTotalCapacity(totalCapacity);
		validateRoundArenaCapacity(roundArenaCapacity, totalCapacity);
		validateClassCapacities(classCapacities);
		validateOccupancyFloor(
			totalCapacity,
			roundArenaCapacity,
			classCapacities,
			totalOccupied,
			roundArenaOccupied,
			classOccupied);
		this.totalCapacity = totalCapacity.byteValue();
		this.roundArenaCapacity = roundArenaCapacity.byteValue();
		this.classCapacities = Map.copyOf(classCapacities);
	}

	public void changeClosedStatus(Boolean closed) {
		if (closed == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLOSED_STATUS);
		}
		this.closed = closed;
	}

	public void ensureCanReserve(
		RidingClass ridingClass,
		int totalOccupied,
		int roundArenaOccupied,
		int classOccupied
	) {
		if (closed) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CLOSED);
		}
		final int classCapacity = classCapacities.get(ridingClass.name());
		if (totalOccupied >= totalCapacity
			|| (usesRoundArena(ridingClass) && roundArenaOccupied >= roundArenaCapacity)
			|| classOccupied >= classCapacity) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CAPACITY_EXCEEDED);
		}
	}

	public static boolean usesRoundArena(RidingClass ridingClass) {
		return ROUND_ARENA_CLASSES.contains(ridingClass);
	}

	private static LocalDate requireLessonDate(LocalDate lessonDate) {
		if (lessonDate == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_LESSON_DATE);
		}
		return lessonDate;
	}

	private static LocalTime requireStartTime(LocalTime startTime) {
		if (startTime == null) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_START_TIME);
		}
		return startTime;
	}

	private static LocalTime calculateEndTime(LocalTime startTime) {
		final LocalTime endTime = startTime.plusMinutes(LESSON_DURATION_MINUTES);
		if (!endTime.isAfter(startTime)) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_LESSON_INTERVAL);
		}
		return endTime;
	}

	private static void validateTotalCapacity(Integer totalCapacity) {
		if (totalCapacity == null || totalCapacity < 0 || totalCapacity > MAX_TOTAL_CAPACITY) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_TOTAL_CAPACITY);
		}
	}

	private static void validateRoundArenaCapacity(Integer roundArenaCapacity, Integer totalCapacity) {
		if (roundArenaCapacity == null
			|| roundArenaCapacity < 0
			|| roundArenaCapacity > MAX_ROUND_ARENA_CAPACITY
			|| roundArenaCapacity > totalCapacity) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_ROUND_ARENA_CAPACITY);
		}
	}

	private static void validateClassCapacities(Map<String, Integer> classCapacities) {
		if (classCapacities == null
			|| !classCapacities.keySet().equals(REQUIRED_CLASS_NAMES)
			|| classCapacities.values().stream()
				.anyMatch(capacity -> capacity == null || capacity < 0 || capacity > MAX_TOTAL_CAPACITY)) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_INVALID_CLASS_CAPACITIES);
		}
	}

	private static void validateOccupancyFloor(
		Integer totalCapacity,
		Integer roundArenaCapacity,
		Map<String, Integer> classCapacities,
		int totalOccupied,
		int roundArenaOccupied,
		Map<RidingClass, Integer> classOccupied
	) {
		final boolean classCapacityBelowOccupancy = classOccupied.entrySet().stream()
			.anyMatch(entry -> classCapacities.get(entry.getKey().name()) < entry.getValue());
		if (totalCapacity < totalOccupied
			|| roundArenaCapacity < roundArenaOccupied
			|| classCapacityBelowOccupancy) {
			throw new TimeSlotException(ExceptionCode.TIMESLOT_CAPACITY_BELOW_OCCUPANCY);
		}
	}

	public Long getId() {
		return id;
	}

	public LocalDate getLessonDate() {
		return lessonDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public TimeSlotSource getSource() {
		return source;
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

	public boolean isClosed() {
		return closed;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
