package com.horse.members.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.horse.members.domain.exception.MemberException;

class GeneralRidingGradeTest {

	@ParameterizedTest
	@CsvSource({
		"0, FIRST_RIDE",
		"1, ROUND_BEGINNER",
		"5, ROUND_BEGINNER",
		"6, ROUND_TROT",
		"20, ROUND_TROT",
		"21, LARGE_ARENA_BEGINNER",
		"25, LARGE_ARENA_BEGINNER",
		"26, LARGE_ARENA_TROT",
		"69, LARGE_ARENA_TROT",
		"70, CANTER_BEGINNER",
		"99, CANTER_BEGINNER",
		"100, CANTER"
	})
	void 일반_기승_횟수의_경계에_따라_등급을_판정한다(int rideCount, GeneralRidingGrade expected) {
		assertThat(GeneralRidingGrade.fromRideCount(rideCount)).isEqualTo(expected);
	}

	@Test
	void 음수_일반_기승_횟수는_거부한다() {
		assertThatThrownBy(() -> GeneralRidingGrade.fromRideCount(-1))
			.isInstanceOf(MemberException.class)
			.hasMessage("일반 기승 횟수는 음수일 수 없습니다.");
	}

	@Test
	void 등급은_예약할_수_있는_최상위_일반_클래스를_가진다() {
		assertThat(GeneralRidingGrade.LARGE_ARENA_BEGINNER.ridingClass())
			.isEqualTo(RidingClass.LARGE_ARENA_BEGINNER);
		assertThat(GeneralRidingGrade.CANTER.minimumRideCount()).isEqualTo(100);
	}
}
