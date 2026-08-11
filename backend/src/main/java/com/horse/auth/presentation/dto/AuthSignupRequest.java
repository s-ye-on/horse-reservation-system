package com.horse.auth.presentation.dto;

import com.horse.auth.domain.AuthCredentialPolicy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthSignupRequest(
	@NotBlank @Size(max = 254) String email,
	@NotBlank
	@Size(min = AuthCredentialPolicy.MINIMUM_PASSWORD_LENGTH, max = AuthCredentialPolicy.MAXIMUM_PASSWORD_BYTES)
	@Schema(writeOnly = true)
	String password,
	@NotBlank @Size(max = 100) String name,
	@NotBlank @Size(max = 30) String phone
) {
}
