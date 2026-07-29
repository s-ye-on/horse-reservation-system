package com.horse.coupons.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springdoc.core.annotations.ParameterObject;

import com.horse.coupons.application.MemberCouponPageResult;
import com.horse.coupons.application.MemberCouponQueryService;
import com.horse.coupons.application.MemberCouponUsagePageResult;
import com.horse.coupons.presentation.dto.MemberCouponPageResponse;
import com.horse.coupons.presentation.dto.MemberCouponQueryRequest;
import com.horse.coupons.presentation.dto.MemberCouponUsagePageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me")
public class MemberCouponQueryController {

	private final MemberCouponQueryService service;

	public MemberCouponQueryController(MemberCouponQueryService service) {
		this.service = service;
	}

	@GetMapping("/coupons")
	public MemberCouponPageResponse getCoupons(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@Valid @ParameterObject @ModelAttribute MemberCouponQueryRequest request
	) {
		final MemberCouponPageResult result = service.getCoupons(
			authSubject,
			request.page(),
			request.size());
		return MemberCouponPageResponse.from(result);
	}

	@GetMapping("/coupon-usage-logs")
	public MemberCouponUsagePageResponse getUsageLogs(
		@AuthenticationPrincipal(expression = "subject") String authSubject,
		@Valid @ParameterObject @ModelAttribute MemberCouponQueryRequest request
	) {
		final MemberCouponUsagePageResult result = service.getUsageLogs(
			authSubject,
			request.page(),
			request.size());
		return MemberCouponUsagePageResponse.from(result);
	}
}
