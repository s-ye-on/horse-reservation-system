package com.horse.members.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.members.application.AdminMemberRidingPermissionService;
import com.horse.members.presentation.dto.AdminMemberResponse;
import com.horse.members.presentation.dto.MemberRidingPermissionUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberRidingPermissionController {

	private final AdminMemberRidingPermissionService service;

	public AdminMemberRidingPermissionController(AdminMemberRidingPermissionService service) {
		this.service = service;
	}

	@PatchMapping("/{memberId}/riding-permissions")
	public AdminMemberResponse changeRidingPermissions(
		@PathVariable Long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody MemberRidingPermissionUpdateRequest request
	) {
		return AdminMemberResponse.from(service.changeRidingPermissions(
			memberId,
			request.dressageApproved(),
			request.jumpingApproved(),
			adminSubject,
			request.reason()));
	}

}
