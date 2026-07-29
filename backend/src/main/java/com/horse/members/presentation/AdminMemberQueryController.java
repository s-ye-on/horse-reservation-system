package com.horse.members.presentation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springdoc.core.annotations.ParameterObject;

import com.horse.members.application.AdminMemberPageResult;
import com.horse.members.application.AdminMemberQueryService;
import com.horse.members.presentation.dto.AdminMemberPageResponse;
import com.horse.members.presentation.dto.AdminMemberQueryRequest;
import com.horse.members.presentation.dto.AdminMemberResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberQueryController {

	private final AdminMemberQueryService service;

	public AdminMemberQueryController(AdminMemberQueryService service) {
		this.service = service;
	}

	@GetMapping
	public AdminMemberPageResponse getMembers(
		@Valid @ParameterObject @ModelAttribute AdminMemberQueryRequest request
	) {
		final AdminMemberPageResult result = service.getMembers(request.page(), request.size());
		return AdminMemberPageResponse.from(result);
	}

	@GetMapping("/{memberId}")
	public AdminMemberResponse getMember(@PathVariable Long memberId) {
		return AdminMemberResponse.from(service.getMember(memberId));
	}

}
