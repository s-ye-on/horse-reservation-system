package com.horse.families.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FamilyMemberAddRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull @Positive Long memberId,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(min = 1, max = 500) String reason
) {
}
