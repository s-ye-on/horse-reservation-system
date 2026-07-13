package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.ReservationStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationStatusConverter implements AttributeConverter<ReservationStatus, String> {

	@Override
	public String convertToDatabaseColumn(ReservationStatus status) {
		return status == null ? null : status.databaseValue();
	}

	@Override
	public ReservationStatus convertToEntityAttribute(String databaseValue) {
		return databaseValue == null ? null : ReservationStatus.fromDatabaseValue(databaseValue);
	}
}
