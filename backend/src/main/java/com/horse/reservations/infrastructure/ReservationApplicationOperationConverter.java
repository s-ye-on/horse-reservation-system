package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.ReservationApplicationOperation;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationApplicationOperationConverter
	implements AttributeConverter<ReservationApplicationOperation, String> {

	@Override
	public String convertToDatabaseColumn(ReservationApplicationOperation operation) {
		return operation == null ? null : operation.databaseValue();
	}

	@Override
	public ReservationApplicationOperation convertToEntityAttribute(String databaseValue) {
		return databaseValue == null
			? null
			: ReservationApplicationOperation.fromDatabaseValue(databaseValue);
	}
}
