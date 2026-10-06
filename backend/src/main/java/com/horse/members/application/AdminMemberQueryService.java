package com.horse.members.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.horse.global.exception.ExceptionCode;
import com.horse.members.domain.Member;
import com.horse.members.domain.exception.MemberException;
import com.horse.members.infrastructure.MemberRepository;

@Service
public class AdminMemberQueryService {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	private final MemberRepository memberRepository;

	public AdminMemberQueryService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional(readOnly = true)
	public AdminMemberPageResult getMembers(Integer page, Integer size, String query) {
		final Pageable pageable = PageRequest.of(
			page == null ? DEFAULT_PAGE : page,
			size == null ? DEFAULT_SIZE : size,
			Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		final String normalizedQuery = query == null ? "" : query.strip();
		final String phoneQuery = normalizedQuery.matches("[0-9\\s\\-]+")
			? normalizedQuery.replaceAll("[^0-9]", "") : "";
		final Page<Member> members = normalizedQuery.isEmpty()
			? memberRepository.findAll(pageable)
			: memberRepository.searchByNameOrPhone(normalizedQuery, phoneQuery, pageable);
		return new AdminMemberPageResult(
			members.getContent().stream().map(AdminMemberQueryResult::from).toList(),
			members.getNumber(),
			members.getSize(),
			members.getTotalElements(),
			members.getTotalPages(),
			members.hasNext());
	}

	@Transactional(readOnly = true)
	public AdminMemberQueryResult getMember(Long memberId) {
		final Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new MemberException(ExceptionCode.MEMBER_NOT_FOUND));
		return AdminMemberQueryResult.from(member);
	}

}
