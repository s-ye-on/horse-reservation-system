package com.horse.coupons.presentation;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.coupons.application.MemberCouponQueryService;
import com.horse.coupons.presentation.dto.MemberCouponResponse;
import com.horse.coupons.presentation.dto.MemberCouponUsageResponse;

@RestController
@RequestMapping("/api/me")
public class MemberCouponQueryController {

	private final MemberCouponQueryService service;

	public MemberCouponQueryController(MemberCouponQueryService service) {
		this.service = service;
	}

	@GetMapping("/coupons")
	public List<MemberCouponResponse> getCoupons(
		@AuthenticationPrincipal(expression = "subject") String authSubject
	) {
		return service.getCoupons(authSubject).stream()
			.map(MemberCouponResponse::from)
			.toList();
	}

	@GetMapping("/coupon-usage-logs")
	public List<MemberCouponUsageResponse> getUsageLogs(
		@AuthenticationPrincipal(expression = "subject") String authSubject
	) {
		return service.getUsageLogs(authSubject).stream()
			.map(MemberCouponUsageResponse::from)
			.toList();
	}
}
