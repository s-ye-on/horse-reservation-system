package com.horse.members.presentation;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.horse.members.application.AdminMemberClassProgressionService;
import com.horse.members.presentation.dto.AdminMemberResponse;
import com.horse.members.presentation.dto.MemberClassChangeReasonRequest;
import com.horse.members.presentation.dto.MemberProgressionBaselineRequest;
import com.horse.members.presentation.dto.MemberProgressionCreditCorrectionRequest;
import com.horse.members.presentation.dto.MemberPromotionHoldRequest;
import com.horse.members.presentation.dto.MemberRideCountAdjustmentRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/members/{memberId}/class-progression")
public class AdminMemberClassProgressionController {

	private final AdminMemberClassProgressionService service;

	public AdminMemberClassProgressionController(AdminMemberClassProgressionService service) {
		this.service = service;
	}

	@PutMapping("/baseline")
	@Operation(operationId = "setMemberProgressionBaseline")
	public AdminMemberResponse setBaseline(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Parameter(description = "Preview가 반환한 quoted strong entity-tag. stateToken 값 전체를 그대로 전달한다.")
		@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String expectedStateToken,
		@Valid @RequestBody MemberProgressionBaselineRequest request
	) {
		return AdminMemberResponse.from(service.setBaseline(
			memberId,
			request.baselineClass(),
			adminSubject,
			request.reason(),
			expectedStateToken));
	}

	@DeleteMapping("/baseline")
	@Operation(operationId = "removeMemberProgressionBaseline")
	public AdminMemberResponse removeBaseline(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Parameter(description = "Preview가 반환한 quoted strong entity-tag. stateToken 값 전체를 그대로 전달한다.")
		@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String expectedStateToken,
		@Valid @RequestBody MemberClassChangeReasonRequest request
	) {
		return AdminMemberResponse.from(service.removeBaseline(
			memberId,
			adminSubject,
			request.reason(),
			expectedStateToken));
	}

	@PutMapping("/promotion-hold")
	@Operation(operationId = "setMemberPromotionHold")
	public AdminMemberResponse setPromotionHold(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Parameter(description = "Preview가 반환한 quoted strong entity-tag. stateToken 값 전체를 그대로 전달한다.")
		@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String expectedStateToken,
		@Valid @RequestBody MemberPromotionHoldRequest request
	) {
		return AdminMemberResponse.from(service.setPromotionHold(
			memberId,
			request.promotionHoldClass(),
			adminSubject,
			request.reason(),
			expectedStateToken));
	}

	@DeleteMapping("/promotion-hold")
	@Operation(operationId = "removeMemberPromotionHold")
	public AdminMemberResponse removePromotionHold(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Parameter(description = "Preview가 반환한 quoted strong entity-tag. stateToken 값 전체를 그대로 전달한다.")
		@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String expectedStateToken,
		@Valid @RequestBody MemberClassChangeReasonRequest request
	) {
		return AdminMemberResponse.from(service.removePromotionHold(
			memberId,
			adminSubject,
			request.reason(),
			expectedStateToken));
	}

	@PutMapping("/special-approval-credit")
	@Operation(operationId = "correctMemberSpecialApprovalCredit")
	public AdminMemberResponse correctSpecialApprovalCredit(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Parameter(description = "Preview가 반환한 quoted strong entity-tag. stateToken 값 전체를 그대로 전달한다.")
		@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String expectedStateToken,
		@Valid @RequestBody MemberProgressionCreditCorrectionRequest request
	) {
		return AdminMemberResponse.from(service.correctSpecialApprovalProgressionCredit(
			memberId,
			request.specialApprovalProgressionCredit(),
			adminSubject,
			request.reason(),
			expectedStateToken));
	}

	@PostMapping("/ride-count-adjustments")
	@Operation(operationId = "adjustMemberActualCompletedRideCount")
	public AdminMemberResponse adjustActualCompletedRideCount(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Parameter(description = "Preview가 반환한 quoted strong entity-tag. stateToken 값 전체를 그대로 전달한다.")
		@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String expectedStateToken,
		@Valid @RequestBody MemberRideCountAdjustmentRequest request
	) {
		return AdminMemberResponse.from(service.adjustActualCompletedRideCount(
			memberId,
			request.delta(),
			adminSubject,
			request.reason(),
			expectedStateToken));
	}
}
