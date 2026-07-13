package com.horse.coupons.infrastructure;

import com.horse.coupons.domain.CouponActorType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponActorTypeConverter implements AttributeConverter<CouponActorType, String> {

	@Override
	public String convertToDatabaseColumn(CouponActorType actorType) {
		return actorType == null ? null : actorType.value();
	}

	@Override
	public CouponActorType convertToEntityAttribute(String value) {
		return value == null ? null : CouponActorType.fromDatabaseValue(value);
	}
}
