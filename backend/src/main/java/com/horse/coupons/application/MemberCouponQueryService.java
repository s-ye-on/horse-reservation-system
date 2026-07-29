package com.horse.coupons.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.domain.Coupon;
import com.horse.coupons.domain.CouponUsageLog;
import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.coupons.infrastructure.CouponUsageLogRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class MemberCouponQueryService {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	private final MemberRepository memberRepository;
	private final CouponRepository couponRepository;
	private final CouponUsageLogRepository usageLogRepository;

	public MemberCouponQueryService(
		MemberRepository memberRepository,
		CouponRepository couponRepository,
		CouponUsageLogRepository usageLogRepository
	) {
		this.memberRepository = memberRepository;
		this.couponRepository = couponRepository;
		this.usageLogRepository = usageLogRepository;
	}

	@Transactional(readOnly = true)
	public MemberCouponPageResult getCoupons(String authSubject, Integer page, Integer size) {
		final Member member = findMember(authSubject);
		final Page<Coupon> coupons = couponRepository.findAllByMemberId(
			member.getId(),
			PageRequest.of(
				page == null ? DEFAULT_PAGE : page,
				size == null ? DEFAULT_SIZE : size,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		return new MemberCouponPageResult(
			coupons.getContent().stream().map(MemberCouponResult::from).toList(),
			coupons.getNumber(),
			coupons.getSize(),
			coupons.getTotalElements(),
			coupons.getTotalPages(),
			coupons.hasNext());
	}

	@Transactional(readOnly = true)
	public MemberCouponUsagePageResult getUsageLogs(
		String authSubject,
		Integer page,
		Integer size
	) {
		final Member member = findMember(authSubject);
		final Page<CouponUsageLog> usageLogs = usageLogRepository.findAllByMemberId(
			member.getId(),
			PageRequest.of(
				page == null ? DEFAULT_PAGE : page,
				size == null ? DEFAULT_SIZE : size,
				Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
		return new MemberCouponUsagePageResult(
			usageLogs.getContent().stream().map(MemberCouponUsageResult::from).toList(),
			usageLogs.getNumber(),
			usageLogs.getSize(),
			usageLogs.getTotalElements(),
			usageLogs.getTotalPages(),
			usageLogs.hasNext());
	}

	private Member findMember(String authSubject) {
		return memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}
}
