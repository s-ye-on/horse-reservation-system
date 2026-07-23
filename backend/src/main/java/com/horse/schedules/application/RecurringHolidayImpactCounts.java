package com.horse.schedules.application;

public record RecurringHolidayImpactCounts(
	long affectedDateCount,
	long templateTimeSlotCount,
	long activeReservationCount
) {

	public static RecurringHolidayImpactCounts zero() {
		return new RecurringHolidayImpactCounts(0, 0, 0);
	}
}
