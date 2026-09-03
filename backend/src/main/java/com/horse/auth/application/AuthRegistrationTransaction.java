package com.horse.auth.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.horse.auth.domain.AuthAccount;
import com.horse.auth.infrastructure.AuthAccountRepository;
import com.horse.members.application.MemberClassProgressionSnapshot;
import com.horse.members.domain.Member;
import com.horse.members.domain.MemberClassProgressionAuditAction;
import com.horse.members.domain.MemberClassProgressionAuditLog;
import com.horse.members.infrastructure.MemberClassProgressionAuditLogRepository;
import com.horse.members.infrastructure.MemberRepository;

@Component
public class AuthRegistrationTransaction {

	private static final String INITIALIZATION_ACTOR = "SYSTEM";
	private static final String INITIALIZATION_REASON = "신규 회원 progression 초기화";

	private final Clock clock;
	private final MemberRepository memberRepository;
	private final MemberClassProgressionAuditLogRepository progressionAuditLogRepository;
	private final AuthAccountRepository authAccountRepository;

	public AuthRegistrationTransaction(
		Clock clock,
		MemberRepository memberRepository,
		MemberClassProgressionAuditLogRepository progressionAuditLogRepository,
		AuthAccountRepository authAccountRepository
	) {
		this.clock = clock;
		this.memberRepository = memberRepository;
		this.progressionAuditLogRepository = progressionAuditLogRepository;
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
		final Member member = memberRepository.saveAndFlush(Member.createManaged(
			authSubject,
			name,
			phone,
			LocalDateTime.now(clock)));
		progressionAuditLogRepository.append(MemberClassProgressionAuditLog.create(
			member,
			MemberClassProgressionAuditAction.PROGRESSION_INITIALIZED,
			null,
			MemberClassProgressionSnapshot.from(member).toAuditState(),
			INITIALIZATION_ACTOR,
			INITIALIZATION_REASON));
		return authAccountRepository.saveAndFlush(AuthAccount.createMember(
			member.getId(),
			authSubject,
			normalizedEmail,
			passwordHash
		));
	}
}
