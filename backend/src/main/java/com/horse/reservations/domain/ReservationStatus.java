package com.horse.reservations.domain;

import java.util.Arrays;
import java.util.Set;

import com.horse.global.exception.ExceptionCode;
import com.horse.reservations.domain.exception.ReservationException;

public enum ReservationStatus {
	PENDING_ADMIN_APPROVAL("pending_admin_approval", true),
	PENDING_PAYMENT("pending_payment", true),
	PAYMENT_EXPIRED("payment_expired", false),
	CONFIRMED("confirmed", true),
	COMPLETED("completed", false),
	REJECTED("rejected", false),
	CANCELLED("cancelled", false),
	NO_SHOW("no_show", false);

	private static final Set<ReservationStatus> OCCUPYING_STATUSES = Set.of(
		PENDING_ADMIN_APPROVAL,
		PENDING_PAYMENT,
		CONFIRMED);

	private final String databaseValue;
	private final boolean occupyingCapacity;

	ReservationStatus(String databaseValue, boolean occupyingCapacity) {
		this.databaseValue = databaseValue;
		this.occupyingCapacity = occupyingCapacity;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public boolean occupiesCapacity() {
		return occupyingCapacity;
	}

	public static Set<ReservationStatus> occupyingStatuses() {
		return OCCUPYING_STATUSES;
	}

	public static ReservationStatus fromDatabaseValue(String databaseValue) {
		return Arrays.stream(values())
			.filter(status -> status.databaseValue.equals(databaseValue))
			.findFirst()
			.orElseThrow(() -> new ReservationException(ExceptionCode.RESERVATION_INVALID_PERSISTED_VALUE));
	}
}
