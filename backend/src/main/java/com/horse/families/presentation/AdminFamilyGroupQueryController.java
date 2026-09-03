package com.horse.families.presentation;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.families.application.FamilyGroupQueryService;
import com.horse.families.presentation.dto.FamilyGroupAuditPageResponse;
import com.horse.families.presentation.dto.FamilyGroupMemberPageResponse;
import com.horse.families.presentation.dto.FamilyGroupPageRequest;
import com.horse.families.presentation.dto.FamilyGroupPageResponse;
import com.horse.families.presentation.dto.FamilyGroupQueryRequest;
import com.horse.families.presentation.dto.FamilyMemberCandidatePageResponse;
import com.horse.families.presentation.dto.FamilyMemberCandidateQueryRequest;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/admin/family-groups")
public class AdminFamilyGroupQueryController {

	private final FamilyGroupQueryService service;

	public AdminFamilyGroupQueryController(FamilyGroupQueryService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(operationId = "getFamilyGroups")
	public FamilyGroupPageResponse getGroups(
		@Valid @ParameterObject @ModelAttribute FamilyGroupQueryRequest request
	) {
		return FamilyGroupPageResponse.from(service.getGroups(
			request.query(),
			request.status(),
			request.page(),
			request.size()));
	}

	@GetMapping("/member-candidates")
	@Operation(operationId = "getFamilyMemberCandidates")
	public FamilyMemberCandidatePageResponse getMemberCandidates(
		@Valid @ParameterObject @ModelAttribute FamilyMemberCandidateQueryRequest request
	) {
		return FamilyMemberCandidatePageResponse.from(service.getMemberCandidates(
			request.query(),
			request.page(),
			request.size()));
	}

	@GetMapping("/{groupId}/members")
	@Operation(operationId = "getFamilyGroupMembers")
	public FamilyGroupMemberPageResponse getMembers(
		@PathVariable long groupId,
		@Valid @ParameterObject @ModelAttribute FamilyGroupPageRequest request
	) {
		return FamilyGroupMemberPageResponse.from(service.getMembers(
			groupId,
			request.page(),
			request.size()));
	}

	@GetMapping("/{groupId}/audit-logs")
	@Operation(operationId = "getFamilyGroupAuditLogs")
	public FamilyGroupAuditPageResponse getAuditLogs(
		@PathVariable long groupId,
		@Valid @ParameterObject @ModelAttribute FamilyGroupPageRequest request
	) {
		return FamilyGroupAuditPageResponse.from(service.getAuditLogs(
			groupId,
			request.page(),
			request.size()));
	}
}
