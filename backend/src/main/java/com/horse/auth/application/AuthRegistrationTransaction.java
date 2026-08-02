package com.horse.auth.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.horse.auth.domain.AuthAccount;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.members.domain.Member;
import com.horse.members.infrastructure.MemberRepository;

@Component
public class AuthRegistrationTransaction {

	private final MemberRepository memberRepository;
	private final AuthAccountRepository authAccountRepository;

	public AuthRegistrationTransaction(
		MemberRepository memberRepository,
		AuthAccountRepository authAccountRepository
	) {
		this.memberRepository = memberRepository;
		this.authAccountRepository = authAccountRepository;
	}

	@Transactional
	public AuthAccount createMemberAccount(
		String authSubject,
		String normalizedEmail,
		String passwordHash,
		String name,
		String phone
	) {
		final Member member = memberRepository.saveAndFlush(Member.create(authSubject, name, phone));
		return authAccountRepository.saveAndFlush(AuthAccount.createMember(
			member.getId(),
			authSubject,
			normalizedEmail,
			passwordHash
		));
	}
}
