package com.horse.families.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.families.domain.FamilyGroup;
import com.horse.families.domain.FamilyGroupAuditLog;
import com.horse.families.domain.FamilyGroupStatus;
import com.horse.families.domain.FamilyMembership;
import com.horse.families.domain.exception.FamilyException;
import com.horse.families.infrastructure.FamilyGroupAuditLogRepository;
import com.horse.families.infrastructure.FamilyGroupMemberCountProjection;
import com.horse.families.infrastructure.FamilyGroupRepository;
import com.horse.families.infrastructure.FamilyMembershipRepository;
import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;

@Service
public class FamilyGroupQueryService {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	private final FamilyGroupRepository groupRepository;
	private final FamilyMembershipRepository membershipRepository;
	private final FamilyGroupAuditLogRepository auditLogRepository;

	public FamilyGroupQueryService(
		FamilyGroupRepository groupRepository,
		FamilyMembershipRepository membershipRepository,
		FamilyGroupAuditLogRepository auditLogRepository
	) {
		this.groupRepository = groupRepository;
		this.membershipRepository = membershipRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional(readOnly = true)
	public FamilyGroupPageResult getGroups(
		String query,
		FamilyGroupStatus status,
		Integer page,
		Integer size
	) {
		final Page<FamilyGroup> groups = groupRepository.search(
			normalizeQuery(query),
			status,
			pageRequest(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		final Map<Long, Long> memberCounts = activeMemberCounts(groups.getContent());
		return new FamilyGroupPageResult(
			groups.getContent().stream()
				.map(group -> FamilyGroupSummaryResult.from(
					group,
					memberCounts.getOrDefault(group.getId(), 0L)))
				.toList(),
			groups.getNumber(),
			groups.getSize(),
			groups.getTotalElements(),
			groups.getTotalPages(),
			groups.hasNext());
	}

	@Transactional(readOnly = true)
	public FamilyGroupMemberPageResult getMembers(long groupId, Integer page, Integer size) {
		findGroup(groupId);
		final Page<FamilyMembership> memberships = membershipRepository.findActiveMembers(
			groupId,
			pageRequest(page, size, Sort.unsorted()));
		return new FamilyGroupMemberPageResult(
			memberships.getContent().stream().map(FamilyGroupMemberResult::from).toList(),
			memberships.getNumber(),
			memberships.getSize(),
			memberships.getTotalElements(),
			memberships.getTotalPages(),
			memberships.hasNext());
	}

	@Transactional(readOnly = true)
	public FamilyMemberCandidatePageResult getMemberCandidates(String query, Integer page, Integer size) {
		final Page<Member> members = membershipRepository.findAvailableMemberCandidates(
			normalizeQuery(query),
			pageRequest(page, size, Sort.unsorted()));
		return new FamilyMemberCandidatePageResult(
			members.getContent().stream().map(FamilyMemberCandidateResult::from).toList(),
			members.getNumber(),
			members.getSize(),
			members.getTotalElements(),
			members.getTotalPages(),
			members.hasNext());
	}

	@Transactional(readOnly = true)
	public FamilyGroupAuditPageResult getAuditLogs(long groupId, Integer page, Integer size) {
		findGroup(groupId);
		final Page<FamilyGroupAuditLog> auditLogs = auditLogRepository.findPageByGroupId(
			groupId,
			pageRequest(page, size, Sort.unsorted()));
		return new FamilyGroupAuditPageResult(
			auditLogs.getContent().stream().map(FamilyGroupAuditResult::from).toList(),
			auditLogs.getNumber(),
			auditLogs.getSize(),
			auditLogs.getTotalElements(),
			auditLogs.getTotalPages(),
			auditLogs.hasNext());
	}

	private Map<Long, Long> activeMemberCounts(List<FamilyGroup> groups) {
		if (groups.isEmpty()) {
			return Map.of();
		}
		return membershipRepository.countActiveMembersByGroupIds(
			groups.stream().map(FamilyGroup::getId).toList()).stream()
			.collect(Collectors.toUnmodifiableMap(
				FamilyGroupMemberCountProjection::getGroupId,
				FamilyGroupMemberCountProjection::getMemberCount,
				(first, ignored) -> first));
	}

	private FamilyGroup findGroup(long groupId) {
		if (groupId <= 0) {
			throw new FamilyException(ExceptionCode.FAMILY_GROUP_NOT_FOUND);
		}
		return groupRepository.findById(groupId)
			.orElseThrow(() -> new FamilyException(ExceptionCode.FAMILY_GROUP_NOT_FOUND));
	}

	private PageRequest pageRequest(Integer page, Integer size, Sort sort) {
		return PageRequest.of(
			page == null ? DEFAULT_PAGE : page,
			size == null ? DEFAULT_SIZE : size,
			sort);
	}

	private String normalizeQuery(String query) {
		return query == null ? "" : query.trim();
	}
}
