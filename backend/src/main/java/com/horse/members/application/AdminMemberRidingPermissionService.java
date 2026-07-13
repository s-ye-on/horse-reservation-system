package com.horse.members.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class AdminMemberRidingPermissionService {

	private final MemberRepository memberRepository;

	public AdminMemberRidingPermissionService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional
	public AdminMemberQueryResult changeRidingPermissions(
		Long memberId,
		boolean dressageApproved,
		boolean jumpingApproved
	) {
		final Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		member.changeDressageApproval(dressageApproved);
		member.changeJumpingApproval(jumpingApproved);
		return AdminMemberQueryResult.from(member);
	}

}
