package com.horse.reservations.presentation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.global.observability.OperationalJobContext;
import com.horse.global.observability.OperationalJobLogger;
import com.horse.global.observability.OperationalJobName;
import com.horse.reservations.application.ApprovalExpiryResult;
import com.horse.reservations.application.ApprovalExpiryService;
import com.horse.reservations.presentation.dto.ApprovalExpiryResponse;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminApprovalExpiryJobController {

	private final ApprovalExpiryService service;
	private final OperationalJobLogger jobLogger;

	public AdminApprovalExpiryJobController(
		ApprovalExpiryService service,
		OperationalJobLogger jobLogger
	) {
		this.service = service;
		this.jobLogger = jobLogger;
	}

	@PostMapping("/expire-pending-approvals")
	public ApprovalExpiryResponse expirePendingApprovals(
		@AuthenticationPrincipal(expression = "subject") String adminSubject
	) {
		final ApprovalExpiryResult result = jobLogger.execute(
			OperationalJobContext.manual(OperationalJobName.PENDING_APPROVAL_EXPIRY, adminSubject),
			service::expireDueApprovals,
			value -> "expiredCount=%d,executedAt=%s".formatted(
				value.expiredCount(), value.executedAt()));
		return ApprovalExpiryResponse.from(result);
	}
}
