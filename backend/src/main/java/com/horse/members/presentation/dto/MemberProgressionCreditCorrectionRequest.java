package com.horse.members.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MemberProgressionCreditCorrectionRequest(
	@NotNull @PositiveOrZero Integer specialApprovalProgressionCredit,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(max = 500) String reason
) {
}
