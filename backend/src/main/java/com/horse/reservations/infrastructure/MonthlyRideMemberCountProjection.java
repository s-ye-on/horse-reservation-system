package com.horse.reservations.infrastructure;

public interface MonthlyRideMemberCountProjection {

	Long getMemberId();

	String getMemberName();

	long getCompletedRideCount();
}
