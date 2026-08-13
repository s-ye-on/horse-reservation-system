package com.horse.families.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.families.domain.FamilyGroup;
import com.horse.families.domain.FamilyGroupAuditAction;
import com.horse.families.domain.FamilyGroupAuditLog;
import com.horse.families.domain.FamilyMembership;
import com.horse.families.domain.exception.FamilyException;
import com.horse.families.infrastructure.FamilyGroupAuditLogRepository;
import com.horse.families.infrastructure.FamilyGroupRepository;
import com.horse.families.infrastructure.FamilyMembershipRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class FamilyGroupCommandService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final Clock clock;
	private final FamilyGroupRepository groupRepository;
	private final FamilyMembershipRepository membershipRepository;
	private final FamilyGroupAuditLogRepository auditLogRepository;
	private final MemberRepository memberRepository;

	public FamilyGroupCommandService(
		Clock clock,
		FamilyGroupRepository groupRepository,
		FamilyMembershipRepository membershipRepository,
		FamilyGroupAuditLogRepository auditLogRepository,
		MemberRepository memberRepository
	) {
		this.clock = clock;
		this.groupRepository = groupRepository;
		this.membershipRepository = membershipRepository;
		this.auditLogRepository = auditLogRepository;
		this.memberRepository = memberRepository;
	}

	@Transactional
	public FamilyGroupView create(String name, String actorAuthSubject, String reason) {
		final FamilyGroup group = groupRepository.saveAndFlush(FamilyGroup.create(name));
		appendAudit(
			group,
			null,
			FamilyGroupAuditAction.GROUP_CREATED,
			null,
			groupState(group, List.of()),
			actorAuthSubject,
			reason);
		return FamilyGroupView.from(group);
	}

	@Transactional
	public FamilyMembershipView addMember(
		long groupId,
		long memberId,
		String actorAuthSubject,
		String reason
	) {
		final FamilyGroup group = findActiveGroupForUpdate(groupId);
		final Member member = findMemberForUpdate(memberId);
		if (membershipRepository.findActiveByMemberIdForUpdate(memberId).isPresent()) {
			throw new FamilyException(ExceptionCode.FAMILY_MEMBER_ALREADY_ASSIGNED);
		}
		final FamilyMembership membership = membershipRepository.saveAndFlush(
			FamilyMembership.create(group, member, now()));
		appendAudit(
			group,
			member,
			FamilyGroupAuditAction.MEMBER_ADDED,
			membershipAbsentState(groupId, memberId),
			membershipState(membership),
			actorAuthSubject,
			reason);
		return FamilyMembershipView.from(membership);
	}

	@Transactional
	public FamilyMembershipView removeMember(
		long groupId,
		long memberId,
		String actorAuthSubject,
		String reason
	) {
		final FamilyGroup group = findActiveGroupForUpdate(groupId);
		final Member member = findMemberForUpdate(memberId);
		final FamilyMembership membership = membershipRepository
			.findActiveByGroupIdAndMemberIdForUpdate(groupId, memberId)
			.orElseThrow(() -> new FamilyException(ExceptionCode.FAMILY_MEMBERSHIP_NOT_FOUND));
		final Map<String, Object> fromState = membershipState(membership);
		membership.end(now());
		appendAudit(
			group,
			member,
			FamilyGroupAuditAction.MEMBER_REMOVED,
			fromState,
			membershipState(membership),
			actorAuthSubject,
			reason);
		return FamilyMembershipView.from(membership);
	}

	@Transactional
	public FamilyGroupView dissolve(long groupId, String actorAuthSubject, String reason) {
		final FamilyGroup group = findActiveGroupForUpdate(groupId);
		final List<FamilyMembership> activeMemberships =
			membershipRepository.findAllActiveByGroupIdForUpdate(groupId);
		final List<Long> activeMemberIds = activeMemberships.stream()
			.map(membership -> membership.getMember().getId())
			.toList();
		final Map<String, Object> fromState = groupState(group, activeMemberIds);
		final LocalDateTime dissolvedAt = now();
		activeMemberships.forEach(membership -> membership.end(dissolvedAt));
		group.dissolve(dissolvedAt);
		appendAudit(
			group,
			null,
			FamilyGroupAuditAction.GROUP_DISSOLVED,
			fromState,
			groupState(group, List.of()),
			actorAuthSubject,
			reason);
		return FamilyGroupView.from(group);
	}

	private FamilyGroup findActiveGroupForUpdate(long groupId) {
		if (groupId <= 0) {
			throw new FamilyException(ExceptionCode.FAMILY_GROUP_NOT_FOUND);
		}
		final FamilyGroup group = groupRepository.findByIdForUpdate(groupId)
			.orElseThrow(() -> new FamilyException(ExceptionCode.FAMILY_GROUP_NOT_FOUND));
		group.ensureActive();
		return group;
	}

	private Member findMemberForUpdate(long memberId) {
		if (memberId <= 0) {
			throw new FamilyException(ExceptionCode.FAMILY_INVALID_MEMBER_ID);
		}
		return memberRepository.findByIdForUpdate(memberId)
			.orElseThrow(() -> new FamilyException(ExceptionCode.MEMBER_NOT_FOUND));
	}

	private void appendAudit(
		FamilyGroup group,
		Member member,
		FamilyGroupAuditAction action,
		Map<String, Object> fromState,
		Map<String, Object> toState,
		String actorAuthSubject,
		String reason
	) {
		auditLogRepository.append(FamilyGroupAuditLog.create(
			group,
			member,
			action,
			fromState,
			toState,
			actorAuthSubject,
			reason));
	}

	private Map<String, Object> groupState(FamilyGroup group, List<Long> activeMemberIds) {
		final Map<String, Object> state = new LinkedHashMap<>();
		state.put("groupId", group.getId());
		state.put("name", group.getName());
		state.put("status", group.getStatus().name());
		state.put("activeMemberIds", List.copyOf(activeMemberIds));
		if (group.getDissolvedAt() != null) {
			state.put("dissolvedAt", group.getDissolvedAt().toString());
		}
		return Map.copyOf(state);
	}

	private Map<String, Object> membershipAbsentState(long groupId, long memberId) {
		return Map.of(
			"groupId", groupId,
			"memberId", memberId,
			"status", "NONE");
	}

	private Map<String, Object> membershipState(FamilyMembership membership) {
		final Map<String, Object> state = new LinkedHashMap<>();
		state.put("membershipId", membership.getId());
		state.put("groupId", membership.getFamilyGroup().getId());
		state.put("memberId", membership.getMember().getId());
		state.put("status", membership.isActive() ? "ACTIVE" : "ENDED");
		state.put("joinedAt", membership.getJoinedAt().toString());
		if (membership.getEndedAt() != null) {
			state.put("endedAt", membership.getEndedAt().toString());
		}
		return Map.copyOf(state);
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), SEOUL_ZONE);
	}
}
