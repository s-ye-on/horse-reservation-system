package com.horse.members.domain;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;

public enum GeneralRidingGrade {
	FIRST_RIDE(0, RidingClass.FIRST_RIDE),
	ROUND_BEGINNER(1, RidingClass.ROUND_BEGINNER),
	ROUND_TROT(6, RidingClass.ROUND_TROT),
	LARGE_ARENA_BEGINNER(21, RidingClass.LARGE_ARENA_BEGINNER),
	LARGE_ARENA_TROT(26, RidingClass.LARGE_ARENA_TROT);

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

		GeneralRidingGrade matchedGrade = FIRST_RIDE;
		for (GeneralRidingGrade grade : values()) {
			if (rideCount >= grade.minimumRideCount) {
				matchedGrade = grade;
			}
		}
		return matchedGrade;
	}

	public RidingClass ridingClass() {
		return ridingClass;
	}
}
