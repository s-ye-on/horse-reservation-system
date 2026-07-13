package com.horse.coupons.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.coupons.infrastructure.CouponRepository;
import com.horse.coupons.infrastructure.CouponUsageLogRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class MemberCouponQueryService {

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
	public List<MemberCouponResult> getCoupons(String authSubject) {
		final Member member = findMember(authSubject);
		return couponRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(member.getId()).stream()
			.map(MemberCouponResult::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MemberCouponUsageResult> getUsageLogs(String authSubject) {
		final Member member = findMember(authSubject);
		return usageLogRepository.findAllByMemberIdOrderByOccurredAtDescIdDesc(member.getId()).stream()
			.map(MemberCouponUsageResult::from)
			.toList();
	}

	private Member findMember(String authSubject) {
		return memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}
}
