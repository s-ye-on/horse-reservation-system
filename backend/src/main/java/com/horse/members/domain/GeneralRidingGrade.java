package com.horse.members.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

public enum GeneralRidingGrade {
	FIRST_RIDE(0, RidingClass.FIRST_RIDE),
	ROUND_BEGINNER(1, RidingClass.ROUND_BEGINNER),
	ROUND_TROT(6, RidingClass.ROUND_TROT),
	LARGE_ARENA_BEGINNER(21, RidingClass.LARGE_ARENA_BEGINNER),
	LARGE_ARENA_TROT(26, RidingClass.LARGE_ARENA_TROT),
	CANTER_BEGINNER(70, RidingClass.CANTER_BEGINNER),
	CANTER(100, RidingClass.CANTER);

	private final int minimumRideCount;
	private final RidingClass ridingClass;

	GeneralRidingGrade(int minimumRideCount, RidingClass ridingClass) {
		this.minimumRideCount = minimumRideCount;
		this.ridingClass = ridingClass;
	}

	public static GeneralRidingGrade fromRideCount(int rideCount) {
		if (rideCount < 0) {
			throw new MemberException(ExceptionCode.MEMBER_INVALID_GENERAL_RIDE_COUNT);
		}

		return Arrays.stream(values())
			.filter(grade -> rideCount >= grade.minimumRideCount)
			.max(Comparator.comparingInt(GeneralRidingGrade::minimumRideCount))
			.orElse(FIRST_RIDE);
	}

	public RidingClass ridingClass() {
		return ridingClass;
	}

	public int minimumRideCount() {
		return minimumRideCount;
	}

	public boolean isHigherThan(GeneralRidingGrade other) {
		return minimumRideCount > other.minimumRideCount;
	}

	public static GeneralRidingGrade lowerOf(
		GeneralRidingGrade first,
		GeneralRidingGrade second
	) {
		return first.minimumRideCount <= second.minimumRideCount ? first : second;
	}

	public List<RidingClass> availableGeneralRidingClasses() {
		return Arrays.stream(values())
			.filter(grade -> grade.minimumRideCount <= minimumRideCount)
			.sorted(Comparator.comparingInt(GeneralRidingGrade::minimumRideCount))
			.map(GeneralRidingGrade::ridingClass)
			.toList();
	}
}
