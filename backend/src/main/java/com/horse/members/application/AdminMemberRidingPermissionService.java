package com.horse.members.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.MemberClassProgressionAuditAction;
import com.horse.members.domain.MemberClassProgressionAuditLog;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberClassProgressionAuditLogRepository;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class AdminMemberRidingPermissionService {

	private final MemberRepository memberRepository;
	private final MemberClassProgressionAuditLogRepository auditLogRepository;

	public AdminMemberRidingPermissionService(
		MemberRepository memberRepository,
		MemberClassProgressionAuditLogRepository auditLogRepository
	) {
		this.memberRepository = memberRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public AdminMemberQueryResult changeRidingPermissions(
		Long memberId,
		boolean dressageApproved,
		boolean jumpingApproved,
		String actorAuthSubject,
		String reason
	) {
		final Member member = memberRepository.findByIdForUpdate(memberId)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		final MemberClassProgressionSnapshot before = MemberClassProgressionSnapshot.from(member);
		member.changeSpecialApprovals(dressageApproved, jumpingApproved);
		final MemberClassProgressionSnapshot after = MemberClassProgressionSnapshot.from(member);
		auditLogRepository.append(MemberClassProgressionAuditLog.create(
			member,
			MemberClassProgressionAuditAction.SPECIAL_APPROVAL_CHANGED,
			before.toAuditState(),
			after.toAuditState(),
			actorAuthSubject,
			reason));
		return AdminMemberQueryResult.from(member);
	}

}
