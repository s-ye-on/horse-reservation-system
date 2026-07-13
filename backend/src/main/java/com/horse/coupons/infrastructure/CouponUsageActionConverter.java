package com.horse.coupons.infrastructure;

import com.horse.coupons.domain.CouponUsageAction;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponUsageActionConverter implements AttributeConverter<CouponUsageAction, String> {

	@Override
	public String convertToDatabaseColumn(CouponUsageAction action) {
		return action == null ? null : action.value();
	}

	@Override
	public CouponUsageAction convertToEntityAttribute(String value) {
		return value == null ? null : CouponUsageAction.fromDatabaseValue(value);
	}
}
