package com.horse.coupons.application;

import java.time.Clock;
import java.time.LocalDate;

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

	private final Clock clock;
	private final CouponRepository couponRepository;
	private final MemberRepository memberRepository;

	public AdminCouponRegistrationService(
		Clock clock,
		CouponRepository couponRepository,
		MemberRepository memberRepository
	) {
		this.clock = clock;
		this.couponRepository = couponRepository;
		this.memberRepository = memberRepository;
	}

	@Transactional
	public CouponRegistrationResult register(
		Long memberId,
		String type,
		Integer totalCount,
		Integer usedCount,
		LocalDate firstUsedDate,
		String adminSubject
	) {
		if (!memberRepository.existsById(memberId)) {
			throw new MemberException(ExceptionCode.MEMBER_NOT_FOUND);
		}
		final Coupon coupon = Coupon.register(
			memberId,
			CouponType.fromRequestValue(type),
			totalCount,
			usedCount,
			firstUsedDate,
			LocalDate.now(clock),
			adminSubject);
		return CouponRegistrationResult.from(couponRepository.saveAndFlush(coupon));
	}
}
