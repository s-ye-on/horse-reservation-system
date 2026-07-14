package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.ReservationChangeType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationChangeTypeConverter implements AttributeConverter<ReservationChangeType, String> {

	@Override
	public String convertToDatabaseColumn(ReservationChangeType changeType) {
		return changeType == null ? null : changeType.databaseValue();
	}

	@Override
	public ReservationChangeType convertToEntityAttribute(String databaseValue) {
		return databaseValue == null ? null : ReservationChangeType.fromDatabaseValue(databaseValue);
	}
}
