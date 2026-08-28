package com.horse.reservations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.horse.members.domain.RidingClass;
import com.horse.reservations.domain.exception.ReservationException;

class MonthlyRideTypeTest {

	@Test
	void 기존_RidingClass를_일반_마장마술_장애물로_중복_없이_분류한다() {
		final Set<RidingClass> general = MonthlyRideType.GENERAL.ridingClasses();
		final Set<RidingClass> dressage = MonthlyRideType.DRESSAGE.ridingClasses();
		final Set<RidingClass> jumping = MonthlyRideType.JUMPING.ridingClasses();

		assertThat(general).allMatch(RidingClass::isGeneral);
		assertThat(dressage).containsExactly(RidingClass.DRESSAGE);
		assertThat(jumping).containsExactly(RidingClass.JUMPING);
		assertThat(general).doesNotContainAnyElementsOf(dressage).doesNotContainAnyElementsOf(jumping);
		assertThat(MonthlyRideType.ALL.ridingClasses())
			.containsExactlyInAnyOrderElementsOf(Arrays.stream(RidingClass.values()).collect(Collectors.toSet()));
	}

	@Test
	void 조회_종류는_대소문자를_구분하지_않고_빈값은_ALL이며_알_수_없는_값은_거부한다() {
		assertThat(MonthlyRideType.from(null)).isEqualTo(MonthlyRideType.ALL);
		assertThat(MonthlyRideType.from(" ")).isEqualTo(MonthlyRideType.ALL);
		assertThat(MonthlyRideType.from(" dressage ")).isEqualTo(MonthlyRideType.DRESSAGE);
		assertThatThrownBy(() -> MonthlyRideType.from("ENDURANCE"))
			.isInstanceOfSatisfying(ReservationException.class, exception ->
				assertThat(exception.code()).isEqualTo("RESERVATION_INVALID_MONTHLY_RIDE_TYPE"));
	}
}
