package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.PaymentSource;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentSourceConverter implements AttributeConverter<PaymentSource, String> {

	@Override
	public String convertToDatabaseColumn(PaymentSource source) {
		return source == null ? null : source.databaseValue();
	}

	@Override
	public PaymentSource convertToEntityAttribute(String databaseValue) {
		return databaseValue == null ? null : PaymentSource.fromDatabaseValue(databaseValue);
	}
}
