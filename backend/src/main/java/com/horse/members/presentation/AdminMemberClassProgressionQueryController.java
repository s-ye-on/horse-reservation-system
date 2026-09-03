package com.horse.members.presentation;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.members.application.AdminMemberClassProgressionQueryService;
import com.horse.members.presentation.dto.MemberClassProgressionAuditPageRequest;
import com.horse.members.presentation.dto.MemberClassProgressionAuditPageResponse;
import com.horse.members.presentation.dto.MemberClassProgressionPreviewRequest;
import com.horse.members.presentation.dto.MemberClassProgressionPreviewResponse;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/members/{memberId}/class-progression")
public class AdminMemberClassProgressionQueryController {

	private final AdminMemberClassProgressionQueryService service;

	public AdminMemberClassProgressionQueryController(AdminMemberClassProgressionQueryService service) {
		this.service = service;
	}

	@PostMapping("/preview")
	@Operation(operationId = "previewMemberClassProgression")
	public MemberClassProgressionPreviewResponse preview(
		@PathVariable long memberId,
		@Valid @RequestBody MemberClassProgressionPreviewRequest request
	) {
		return MemberClassProgressionPreviewResponse.from(service.preview(
			memberId,
			request.action(),
			request.baselineClass(),
			request.promotionHoldClass(),
			request.specialApprovalProgressionCredit(),
			request.rideCountDelta()));
	}

	@GetMapping("/audit-logs")
	@Operation(operationId = "getMemberClassProgressionAuditLogs")
	public MemberClassProgressionAuditPageResponse getAuditLogs(
		@PathVariable long memberId,
		@Valid @ParameterObject @ModelAttribute MemberClassProgressionAuditPageRequest request
	) {
		return MemberClassProgressionAuditPageResponse.from(service.getAuditLogs(
			memberId,
			request.page(),
			request.size()));
	}
}
