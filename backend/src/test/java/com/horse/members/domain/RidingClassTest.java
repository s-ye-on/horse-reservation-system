package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class RidingClassTest {

	@Test
	void 일반_클래스와_특수_클래스를_구분한다() {
		assertThat(EnumSet.allOf(RidingClass.class))
			.filteredOn(RidingClass::isGeneral)
			.containsExactly(
				RidingClass.FIRST_RIDE,
				RidingClass.ROUND_BEGINNER,
				RidingClass.ROUND_TROT,
				RidingClass.LARGE_ARENA_BEGINNER,
				RidingClass.LARGE_ARENA_TROT);
		assertThat(EnumSet.allOf(RidingClass.class))
			.filteredOn(ridingClass -> !ridingClass.isGeneral())
			.containsExactly(RidingClass.DRESSAGE, RidingClass.JUMPING);
	}
}
