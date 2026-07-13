package com.horse.members.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class AdminMemberQueryService {

	private final MemberRepository memberRepository;

	public AdminMemberQueryService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional(readOnly = true)
	public List<AdminMemberQueryResult> getMembers() {
		return memberRepository.findAllByOrderByIdAsc().stream()
			.map(AdminMemberQueryResult::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public AdminMemberQueryResult getMember(Long memberId) {
		final Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		return AdminMemberQueryResult.from(member);
	}

}
