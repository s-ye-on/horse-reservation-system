package com.horse.coupons.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.domain.CouponType;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class AdminCouponRegistrationService {

	private final CouponRepository couponRepository;
	private final MemberRepository memberRepository;

	public AdminCouponRegistrationService(
		CouponRepository couponRepository,
		MemberRepository memberRepository
	) {
		this.couponRepository = couponRepository;
		this.memberRepository = memberRepository;
	}

	@Transactional
	public CouponRegistrationResult register(
		Long memberId,
		String type,
		Integer totalCount,
		String adminSubject
	) {
		if (!memberRepository.existsById(memberId)) {
			throw new MemberException(ExceptionCode.MEMBER_NOT_FOUND);
		}
		final Coupon coupon = Coupon.create(
			memberId,
			CouponType.fromRequestValue(type),
			totalCount,
			adminSubject);
		return CouponRegistrationResult.from(couponRepository.saveAndFlush(coupon));
	}
}
