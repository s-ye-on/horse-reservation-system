package com.horse.reservations.application;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.exception.ReservationException;

public enum MonthlyRideType {
	ALL,
	GENERAL,
	DRESSAGE,
	JUMPING;

	public Set<RidingClass> ridingClasses() {
		return switch (this) {
			case ALL -> Set.copyOf(EnumSet.allOf(RidingClass.class));
			case GENERAL -> Arrays.stream(RidingClass.values())
				.filter(RidingClass::isGeneral)
				.collect(Collectors.toUnmodifiableSet());
			case DRESSAGE -> Set.of(RidingClass.DRESSAGE);
			case JUMPING -> Set.of(RidingClass.JUMPING);
		};
	}

	public static MonthlyRideType from(String value) {
		if (value == null || value.isBlank()) {
			return ALL;
		}
		return Arrays.stream(values())
			.filter(type -> type.name().equalsIgnoreCase(value.strip()))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_MONTHLY_RIDE_TYPE));
	}
}
