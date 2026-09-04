package com.horse.coupons.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.horse.coupons.application.AdminCouponRegistrationService;
import com.horse.coupons.presentation.dto.CouponRegistrationRequest;
import com.horse.coupons.presentation.dto.CouponResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/members/{memberId}/coupons")
public class AdminCouponRegistrationController {

	private final AdminCouponRegistrationService service;

	public AdminCouponRegistrationController(AdminCouponRegistrationService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CouponResponse register(
		@PathVariable Long memberId,
		@Valid @RequestBody CouponRegistrationRequest request,
		@AuthenticationPrincipal(expression = "subject") String adminSubject
	) {
		return CouponResponse.from(service.register(
			memberId,
			request.type(),
			request.totalCount(),
			request.usedCount(),
			request.firstUsedDate(),
			adminSubject));
	}
}
