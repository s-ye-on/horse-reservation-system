package com.horse.families.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FamilyGroupCreateRequest(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100)
	@NotBlank @Size(min = 1, max = 100) String name,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(min = 1, max = 500) String reason
) {
}
