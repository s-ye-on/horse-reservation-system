package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.CancellationResponsibility;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CancellationResponsibilityConverter
	implements AttributeConverter<CancellationResponsibility, String> {

	@Override
	public String convertToDatabaseColumn(CancellationResponsibility responsibility) {
		return responsibility == null ? null : responsibility.databaseValue();
	}

	@Override
	public CancellationResponsibility convertToEntityAttribute(String databaseValue) {
		return databaseValue == null ? null : CancellationResponsibility.fromDatabaseValue(databaseValue);
	}
}
