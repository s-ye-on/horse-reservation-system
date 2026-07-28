package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.ReservationApplicationIdempotencyStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationApplicationIdempotencyStatusConverter
	implements AttributeConverter<ReservationApplicationIdempotencyStatus, String> {

	@Override
	public String convertToDatabaseColumn(ReservationApplicationIdempotencyStatus status) {
		return status == null ? null : status.databaseValue();
	}

	@Override
	public ReservationApplicationIdempotencyStatus convertToEntityAttribute(String databaseValue) {
		return databaseValue == null
			? null
			: ReservationApplicationIdempotencyStatus.fromDatabaseValue(databaseValue);
	}
}
