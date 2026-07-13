package com.horse.members.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class MemberAvailableRidingClassesService {

	private final MemberRepository memberRepository;

	public MemberAvailableRidingClassesService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional(readOnly = true)
	public MemberAvailableRidingClassesResult getAvailableRidingClasses(String authSubject) {
		final Member member = memberRepository.findByAuthSubject(authSubject)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		return MemberAvailableRidingClassesResult.from(member);
	}

}
