package com.horse.coupons.infrastructure;

import com.horse.coupons.domain.CouponStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponStatusConverter implements AttributeConverter<CouponStatus, String> {

	@Override
	public String convertToDatabaseColumn(CouponStatus status) {
		return status == null ? null : status.value();
	}

	@Override
	public CouponStatus convertToEntityAttribute(String value) {
		return value == null ? null : CouponStatus.fromDatabaseValue(value);
	}
}
