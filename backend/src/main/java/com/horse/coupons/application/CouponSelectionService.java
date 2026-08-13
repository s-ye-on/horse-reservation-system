package com.horse.coupons.application;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.domain.CouponType;
import com.horse.coupons.domain.exception.CouponException;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.families.infrastructure.FamilyMembershipRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.RidingClass;

@Service
public class CouponSelectionService {

	private final CouponRepository couponRepository;
	private final FamilyMembershipRepository membershipRepository;

	public CouponSelectionService(
		CouponRepository couponRepository,
		FamilyMembershipRepository membershipRepository
	) {
		this.couponRepository = couponRepository;
		this.membershipRepository = membershipRepository;
	}

	@Transactional(readOnly = true)
	public Optional<CouponSelectionResult> select(
		Long memberId,
		RidingClass ridingClass,
		LocalDate lessonDate
	) {
		final CouponType couponType = CouponType.fromRidingClass(ridingClass);
		return couponRepository.findFirstSelectableId(memberId, couponType.value(), lessonDate)
			.map(couponRepository::getReferenceById)
			.map(coupon -> result(memberId, coupon));
	}

	@Transactional
	public Optional<CouponSelectionResult> selectForUpdate(
		Long memberId,
		RidingClass ridingClass,
		LocalDate lessonDate
	) {
		final CouponType couponType = CouponType.fromRidingClass(ridingClass);
		return couponRepository.findFirstSelectableIdForUpdate(
			memberId,
			couponType.value(),
			lessonDate)
			.map(couponRepository::getReferenceById)
			.map(coupon -> result(memberId, coupon));
	}

	private CouponSelectionResult result(Long reservationMemberId, Coupon coupon) {
		final Long familyGroupId = coupon.getMemberId().equals(reservationMemberId)
			? null
			: membershipRepository.findSharedActiveGroupId(
				reservationMemberId,
				coupon.getMemberId())
				.orElseThrow(() -> new CouponException(ExceptionCode.COUPON_HOLD_NOT_AVAILABLE));
		return CouponSelectionResult.from(coupon, familyGroupId);
	}
}
