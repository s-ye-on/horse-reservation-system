package com.horse.members.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum RidingClass {
	FIRST_RIDE(true),
	ROUND_BEGINNER(true),
	ROUND_TROT(true),
	LARGE_ARENA_BEGINNER(true),
	LARGE_ARENA_TROT(true),
	CANTER_BEGINNER(true),
	CANTER(true),
	DRESSAGE(false),
	JUMPING(false);

	private final boolean general;

	RidingClass(boolean general) {
		this.general = general;
	}

	public boolean isGeneral() {
		return general;
	}

	public static Set<String> catalogNames() {
		return Arrays.stream(values())
			.map(Enum::name)
			.collect(Collectors.toUnmodifiableSet());
	}
}
