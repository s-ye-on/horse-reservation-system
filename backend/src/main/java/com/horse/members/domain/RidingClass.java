package com.horse.members.domain;

public enum RidingClass {
	FIRST_RIDE(true),
	ROUND_BEGINNER(true),
	ROUND_TROT(true),
	LARGE_ARENA_BEGINNER(true),
	LARGE_ARENA_TROT(true),
	DRESSAGE(false),
	JUMPING(false);

	private final boolean general;

	RidingClass(boolean general) {
		this.general = general;
	}

	public boolean isGeneral() {
		return general;
	}
}
