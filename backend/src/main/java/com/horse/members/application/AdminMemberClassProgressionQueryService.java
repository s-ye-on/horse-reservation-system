package com.horse.members.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.Member;
import com.horse.members.domain.MemberClassProgressionAuditLog;
import com.horse.members.domain.MemberClassProgressionProjection;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberClassProgressionAuditLogRepository;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class AdminMemberClassProgressionQueryService {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 10;

	private final MemberRepository memberRepository;
	private final MemberClassProgressionAuditLogRepository auditLogRepository;

	public AdminMemberClassProgressionQueryService(
		MemberRepository memberRepository,
		MemberClassProgressionAuditLogRepository auditLogRepository
	) {
		this.memberRepository = memberRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional(readOnly = true)
	public MemberClassProgressionPreviewResult preview(
		long memberId,
		MemberClassProgressionPreviewAction action,
		GeneralRidingGrade baselineClass,
		GeneralRidingGrade promotionHoldClass,
		Integer specialApprovalProgressionCredit,
		Integer rideCountDelta
	) {
		final Member member = findMember(memberId);
		final String stateToken = MemberClassProgressionSnapshot.from(member).strongEntityTag();
		final MemberClassProgressionProjection current = member.currentClassProgressionProjection();
		final MemberClassProgressionProjection expected = switch (required(action)) {
			case SET_BASELINE -> member.previewProgressionBaseline(required(baselineClass));
			case REMOVE_BASELINE -> member.previewWithoutProgressionBaseline();
			case SET_PROMOTION_HOLD -> member.previewPromotionHold(required(promotionHoldClass));
			case REMOVE_PROMOTION_HOLD -> member.previewWithoutPromotionHold();
			case CORRECT_SPECIAL_APPROVAL_CREDIT -> member.previewSpecialApprovalProgressionCredit(
				required(specialApprovalProgressionCredit));
			case ADJUST_RIDE_COUNT -> member.previewGeneralRideCountAdjustment(required(rideCountDelta));
		};
		return new MemberClassProgressionPreviewResult(stateToken, current, expected);
	}

	@Transactional(readOnly = true)
	public MemberClassProgressionAuditPageResult getAuditLogs(long memberId, Integer page, Integer size) {
		findMember(memberId);
		final Page<MemberClassProgressionAuditLog> auditLogs = auditLogRepository.findPageByMemberId(
			memberId,
			PageRequest.of(page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size));
		return new MemberClassProgressionAuditPageResult(
			auditLogs.getContent().stream().map(MemberClassProgressionAuditResult::from).toList(),
			auditLogs.getNumber(),
			auditLogs.getSize(),
			auditLogs.getTotalElements(),
			auditLogs.getTotalPages(),
			auditLogs.hasNext());
	}

	private Member findMember(long memberId) {
		return memberRepository.findById(memberId)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}

	private <T> T required(T value) {
		if (value == null) {
			throw new MemberException(ExceptionCode.COMMON_INVALID_REQUEST);
		}
		return value;
	}
}
