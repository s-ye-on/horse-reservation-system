package com.horse.auth.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.auth.application.AuthAccountQueryService;
import com.horse.auth.application.AuthRegistrationService;
import com.horse.auth.application.AuthSessionService;
import com.horse.auth.presentation.dto.AuthAccountResponse;
import com.horse.auth.presentation.dto.AuthLoginRequest;
import com.horse.auth.presentation.dto.AuthLogoutRequest;
import com.horse.auth.presentation.dto.AuthLogoutResponse;
import com.horse.auth.presentation.dto.AuthRefreshRequest;
import com.horse.auth.presentation.dto.AuthSignupRequest;
import com.horse.auth.presentation.dto.AuthTokenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";

	private final AuthRegistrationService registrationService;
	private final AuthSessionService sessionService;
	private final AuthAccountQueryService accountQueryService;

	public AuthController(
		AuthRegistrationService registrationService,
		AuthSessionService sessionService,
		AuthAccountQueryService accountQueryService
	) {
		this.registrationService = registrationService;
		this.sessionService = sessionService;
		this.accountQueryService = accountQueryService;
	}

	@PostMapping("/signup")
	@SecurityRequirements
	@Operation(operationId = "signup")
	@ApiResponses({
		@ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AuthAccountResponse.class))),
		@ApiResponse(responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA))),
		@ApiResponse(responseCode = "409", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public ResponseEntity<AuthAccountResponse> signup(@Valid @RequestBody AuthSignupRequest request) {
		final AuthAccountResponse response = AuthAccountResponse.from(registrationService.signup(
			request.email(),
			request.password(),
			request.name(),
			request.phone()
		));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(operationId = "login")
	@ApiResponses({
		@ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AuthTokenResponse.class))),
		@ApiResponse(responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA))),
		@ApiResponse(responseCode = "401", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public AuthTokenResponse login(@Valid @RequestBody AuthLoginRequest request) {
		return AuthTokenResponse.from(sessionService.login(request.email(), request.password()));
	}

	@PostMapping("/refresh")
	@SecurityRequirements
	@Operation(operationId = "refreshAccessToken")
	@ApiResponses({
		@ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AuthTokenResponse.class))),
		@ApiResponse(responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA))),
		@ApiResponse(responseCode = "401", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public AuthTokenResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
		return AuthTokenResponse.from(sessionService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	@SecurityRequirements
	@Operation(operationId = "logout")
	@ApiResponses({
		@ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AuthLogoutResponse.class))),
		@ApiResponse(responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA))),
		@ApiResponse(responseCode = "401", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public AuthLogoutResponse logout(@Valid @RequestBody AuthLogoutRequest request) {
		sessionService.logout(request.refreshToken());
		return AuthLogoutResponse.success();
	}

	@GetMapping("/me")
	@Operation(operationId = "getCurrentAuthAccount")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
		@ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AuthAccountResponse.class))),
		@ApiResponse(responseCode = "401", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public AuthAccountResponse me(@AuthenticationPrincipal(expression = "subject") String authSubject) {
		return AuthAccountResponse.from(accountQueryService.findMe(authSubject));
	}
}
