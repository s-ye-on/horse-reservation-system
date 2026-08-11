package com.horse.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthLoginRequest(
	@NotBlank @Size(max = 254) String email,
	@NotBlank @Schema(writeOnly = true) String password
) {
}
