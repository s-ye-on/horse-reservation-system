package com.horse.members.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.members.application.AdminMemberClassProgressionService;
import com.horse.members.presentation.dto.AdminMemberResponse;
import com.horse.members.presentation.dto.MemberClassChangeReasonRequest;
import com.horse.members.presentation.dto.MemberProgressionBaselineRequest;
import com.horse.members.presentation.dto.MemberProgressionCreditCorrectionRequest;
import com.horse.members.presentation.dto.MemberPromotionHoldRequest;
import com.horse.members.presentation.dto.MemberRideCountAdjustmentRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/members/{memberId}/class-progression")
public class AdminMemberClassProgressionController {

	private final AdminMemberClassProgressionService service;

	public AdminMemberClassProgressionController(AdminMemberClassProgressionService service) {
		this.service = service;
	}

	@PutMapping("/baseline")
	public AdminMemberResponse setBaseline(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody MemberProgressionBaselineRequest request
	) {
		return AdminMemberResponse.from(service.setBaseline(
			memberId,
			request.baselineClass(),
			adminSubject,
			request.reason()));
	}

	@DeleteMapping("/baseline")
	public AdminMemberResponse removeBaseline(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody MemberClassChangeReasonRequest request
	) {
		return AdminMemberResponse.from(service.removeBaseline(
			memberId,
			adminSubject,
			request.reason()));
	}

	@PutMapping("/promotion-hold")
	public AdminMemberResponse setPromotionHold(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody MemberPromotionHoldRequest request
	) {
		return AdminMemberResponse.from(service.setPromotionHold(
			memberId,
			request.promotionHoldClass(),
			adminSubject,
			request.reason()));
	}

	@DeleteMapping("/promotion-hold")
	public AdminMemberResponse removePromotionHold(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody MemberClassChangeReasonRequest request
	) {
		return AdminMemberResponse.from(service.removePromotionHold(
			memberId,
			adminSubject,
			request.reason()));
	}

	@PutMapping("/special-approval-credit")
	public AdminMemberResponse correctSpecialApprovalCredit(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody MemberProgressionCreditCorrectionRequest request
	) {
		return AdminMemberResponse.from(service.correctSpecialApprovalProgressionCredit(
			memberId,
			request.specialApprovalProgressionCredit(),
			adminSubject,
			request.reason()));
	}

	@PostMapping("/ride-count-adjustments")
	public AdminMemberResponse adjustActualCompletedRideCount(
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody MemberRideCountAdjustmentRequest request
	) {
		return AdminMemberResponse.from(service.adjustActualCompletedRideCount(
			memberId,
			request.delta(),
			adminSubject,
			request.reason()));
	}
}
