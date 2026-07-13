package com.horse.coupons.infrastructure;

import com.horse.coupons.domain.CouponType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponTypeConverter implements AttributeConverter<CouponType, String> {

	@Override
	public String convertToDatabaseColumn(CouponType type) {
		return type == null ? null : type.value();
	}

	@Override
	public CouponType convertToEntityAttribute(String value) {
		return value == null ? null : CouponType.fromDatabaseValue(value);
	}
}
