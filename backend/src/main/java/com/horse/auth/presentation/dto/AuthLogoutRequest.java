package com.horse.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AuthLogoutRequest(
	@NotBlank @Schema(writeOnly = true) String refreshToken
) {
}
