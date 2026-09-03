package com.horse.reservations.application;

public record AdminMonthlyRideLeaderResult(
	Long memberId,
	String memberName,
	long completedRideCount
) {
}
