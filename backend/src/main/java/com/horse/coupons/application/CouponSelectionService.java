package com.horse.coupons.application;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.CouponType;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.members.domain.RidingClass;

@Service
public class CouponSelectionService {

	private final CouponRepository couponRepository;

	public CouponSelectionService(CouponRepository couponRepository) {
		this.couponRepository = couponRepository;
	}

	@Transactional(readOnly = true)
	public Optional<CouponSelectionResult> select(
		Long memberId,
		RidingClass ridingClass,
		LocalDate lessonDate
	) {
		final CouponType couponType = CouponType.fromRidingClass(ridingClass);
		return couponRepository.findFirstSelectable(memberId, couponType.value(), lessonDate)
			.map(CouponSelectionResult::from);
	}
}
