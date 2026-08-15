package com.horse.members.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.GeneralRidingGrade;
import com.horse.members.domain.Member;
import com.horse.members.domain.MemberClassProgressionAuditAction;
import com.horse.members.domain.MemberClassProgressionAuditLog;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberClassProgressionAuditLogRepository;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class AdminMemberClassProgressionService {

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final MemberClassProgressionAuditLogRepository auditLogRepository;

	public AdminMemberClassProgressionService(
		Clock clock,
		MemberRepository memberRepository,
		MemberClassProgressionAuditLogRepository auditLogRepository
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public AdminMemberQueryResult setBaseline(
		long memberId,
		GeneralRidingGrade baselineClass,
		String actorAuthSubject,
		String reason
	) {
		final Member member = findMemberForUpdate(memberId);
		final LocalDateTime now = LocalDateTime.now(clock);
		if (!member.isProgressionInitialized()) {
			member.initializeProgression(now);
			appendAudit(
				member,
				MemberClassProgressionAuditAction.PROGRESSION_INITIALIZED,
				null,
				snapshot(member),
				actorAuthSubject,
				reason);
		}
		final MemberClassProgressionSnapshot before = snapshot(member);
		member.changeProgressionBaseline(baselineClass, now);
		appendAudit(
			member,
			before.baselineClass() == null
				? MemberClassProgressionAuditAction.BASELINE_SET
				: MemberClassProgressionAuditAction.BASELINE_CHANGED,
			before,
			snapshot(member),
			actorAuthSubject,
			reason);
		return AdminMemberQueryResult.from(member);
	}

	@Transactional
	public AdminMemberQueryResult removeBaseline(
		long memberId,
		String actorAuthSubject,
		String reason
	) {
		final Member member = findMemberForUpdate(memberId);
		final MemberClassProgressionSnapshot before = snapshot(member);
		member.removeProgressionBaseline();
		appendAudit(
			member,
			MemberClassProgressionAuditAction.BASELINE_REMOVED,
			before,
			snapshot(member),
			actorAuthSubject,
			reason);
		return AdminMemberQueryResult.from(member);
	}

	@Transactional
	public AdminMemberQueryResult setPromotionHold(
		long memberId,
		GeneralRidingGrade holdClass,
		String actorAuthSubject,
		String reason
	) {
		final Member member = findMemberForUpdate(memberId);
		final MemberClassProgressionSnapshot before = snapshot(member);
		member.changePromotionHold(holdClass);
		appendAudit(
			member,
			before.promotionHoldClass() == null
				? MemberClassProgressionAuditAction.PROMOTION_HOLD_SET
				: MemberClassProgressionAuditAction.PROMOTION_HOLD_CHANGED,
			before,
			snapshot(member),
			actorAuthSubject,
			reason);
		return AdminMemberQueryResult.from(member);
	}

	@Transactional
	public AdminMemberQueryResult removePromotionHold(
		long memberId,
		String actorAuthSubject,
		String reason
	) {
		final Member member = findMemberForUpdate(memberId);
		final MemberClassProgressionSnapshot before = snapshot(member);
		member.removePromotionHold();
		appendAudit(
			member,
			MemberClassProgressionAuditAction.PROMOTION_HOLD_REMOVED,
			before,
			snapshot(member),
			actorAuthSubject,
			reason);
		return AdminMemberQueryResult.from(member);
	}

	@Transactional
	public AdminMemberQueryResult correctSpecialApprovalProgressionCredit(
		long memberId,
		int correctedCredit,
		String actorAuthSubject,
		String reason
	) {
		final Member member = findMemberForUpdate(memberId);
		final MemberClassProgressionSnapshot before = snapshot(member);
		member.correctSpecialApprovalProgressionCredit(correctedCredit);
		appendAudit(
			member,
			MemberClassProgressionAuditAction.SPECIAL_APPROVAL_CREDIT_CORRECTED,
			before,
			snapshot(member),
			actorAuthSubject,
			reason);
		return AdminMemberQueryResult.from(member);
	}

	@Transactional
	public AdminMemberQueryResult adjustActualCompletedRideCount(
		long memberId,
		int delta,
		String actorAuthSubject,
		String reason
	) {
		final Member member = findMemberForUpdate(memberId);
		final MemberClassProgressionSnapshot before = snapshot(member);
		member.adjustGeneralRideCount(delta);
		final MemberClassProgressionSnapshot after = snapshot(member);
		auditLogRepository.append(MemberClassProgressionAuditLog.create(
			member,
			MemberClassProgressionAuditAction.RIDE_COUNT_ADJUSTED,
			before.toAuditState(),
			after.toRideCountAdjustmentAuditState(delta),
			actorAuthSubject,
			reason));
		return AdminMemberQueryResult.from(member);
	}

	private Member findMemberForUpdate(long memberId) {
		return memberRepository.findByIdForUpdate(memberId)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
	}

	private MemberClassProgressionSnapshot snapshot(Member member) {
		return MemberClassProgressionSnapshot.from(member);
	}

	private void appendAudit(
		Member member,
		MemberClassProgressionAuditAction action,
		MemberClassProgressionSnapshot before,
		MemberClassProgressionSnapshot after,
		String actorAuthSubject,
		String reason
	) {
		auditLogRepository.append(MemberClassProgressionAuditLog.create(
			member,
			action,
			before == null ? null : before.toAuditState(),
			after.toAuditState(),
			actorAuthSubject,
			reason));
	}
}
