package com.horse.reservations.infrastructure;

import com.horse.reservations.domain.CouponAction;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponActionConverter implements AttributeConverter<CouponAction, String> {

	@Override
	public String convertToDatabaseColumn(CouponAction action) {
		return action == null ? null : action.databaseValue();
	}

	@Override
	public CouponAction convertToEntityAttribute(String databaseValue) {
		return databaseValue == null ? null : CouponAction.fromDatabaseValue(databaseValue);
	}
}
