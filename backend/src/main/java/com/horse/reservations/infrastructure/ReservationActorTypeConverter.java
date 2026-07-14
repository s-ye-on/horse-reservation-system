package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.ReservationActorType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationActorTypeConverter implements AttributeConverter<ReservationActorType, String> {

	@Override
	public String convertToDatabaseColumn(ReservationActorType actorType) {
		return actorType == null ? null : actorType.databaseValue();
	}

	@Override
	public ReservationActorType convertToEntityAttribute(String databaseValue) {
		return databaseValue == null ? null : ReservationActorType.fromDatabaseValue(databaseValue);
	}
}
