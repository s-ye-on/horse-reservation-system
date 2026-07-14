package com.horse.reservations.application;

public enum ReservationApprovalWarningLevel {
	NORMAL("normal"),
	WARNING("warning"),
	CRITICAL("critical");

	private final String apiValue;

	ReservationApprovalWarningLevel(String apiValue) {
		this.apiValue = apiValue;
	}

	public String apiValue() {
		return apiValue;
	}
}
