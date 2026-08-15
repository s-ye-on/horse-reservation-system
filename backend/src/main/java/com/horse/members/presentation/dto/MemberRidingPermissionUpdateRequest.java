package com.horse.members.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberRidingPermissionUpdateRequest(
	boolean dressageApproved,
	boolean jumpingApproved,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
	@NotBlank @Size(max = 500) String reason
) {
}
