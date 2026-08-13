package com.horse.auth.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.auth.WebRefreshTokenCookieManager;
import com.horse.auth.application.AuthTokenResult;
import com.horse.auth.application.WebAuthSessionService;
import com.horse.auth.presentation.dto.AuthLoginRequest;
import com.horse.auth.presentation.dto.WebAuthTokenResponse;
import com.horse.auth.presentation.dto.WebCsrfTokenResponse;
import com.horse.global.exception.BusinessException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth/web")
@SecurityRequirements
public class WebAuthController {
	private static final String ERROR_SCHEMA = "#/components/schemas/ErrorResponse";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";

	private final WebAuthSessionService webAuthSessionService;
	private final WebRefreshTokenCookieManager refreshTokenCookieManager;

	public WebAuthController(
		WebAuthSessionService webAuthSessionService,
		WebRefreshTokenCookieManager refreshTokenCookieManager
	) {
		this.webAuthSessionService = webAuthSessionService;
		this.refreshTokenCookieManager = refreshTokenCookieManager;
	}

	@GetMapping("/csrf")
	@Operation(operationId = "initializeWebCsrfToken")
	@ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
		schema = @Schema(implementation = WebCsrfTokenResponse.class)))
	public WebCsrfTokenResponse csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
		return WebCsrfTokenResponse.from(csrfToken);
	}

	@PostMapping("/login")
	@Operation(
		operationId = "webLogin",
		description = "Access Token은 JSON으로 반환하고 Refresh Token은 HttpOnly Cookie로 설정한다.",
		parameters = @Parameter(
		name = CSRF_HEADER,
		in = ParameterIn.HEADER,
		required = true,
		description = "GET /api/auth/web/csrf에서 초기화한 CSRF Token",
		schema = @Schema(type = "string")
		)
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = WebAuthTokenResponse.class))),
		@ApiResponse(responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA))),
		@ApiResponse(responseCode = "401", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA))),
		@ApiResponse(responseCode = "403", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public WebAuthTokenResponse login(
		@Valid @RequestBody AuthLoginRequest request,
		HttpServletResponse response
	) {
		final AuthTokenResult result = webAuthSessionService.login(request.email(), request.password());
		refreshTokenCookieManager.write(response, result.refreshToken());
		return WebAuthTokenResponse.from(result);
	}

	@PostMapping("/refresh")
	@Operation(
		operationId = "refreshWebAccessToken",
		description = "Request body 없이 HttpOnly Refresh Cookie를 회전한다.",
		parameters = @Parameter(
		name = CSRF_HEADER,
		in = ParameterIn.HEADER,
		required = true,
		description = "GET /api/auth/web/csrf에서 초기화한 CSRF Token",
		schema = @Schema(type = "string")
		)
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = WebAuthTokenResponse.class))),
		@ApiResponse(responseCode = "401", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA))),
		@ApiResponse(responseCode = "403", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public WebAuthTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
		try {
			final AuthTokenResult result = webAuthSessionService.refresh(refreshTokenCookieManager.read(request));
			refreshTokenCookieManager.write(response, result.refreshToken());
			return WebAuthTokenResponse.from(result);
		}
		catch (BusinessException exception) {
			refreshTokenCookieManager.clear(response);
			throw exception;
		}
	}

	@PostMapping("/logout")
	@Operation(
		operationId = "webLogout",
		description = "HttpOnly Refresh Cookie를 사용하며 Cookie 상태와 무관하게 삭제 후 204를 반환한다.",
		parameters = @Parameter(
		name = CSRF_HEADER,
		in = ParameterIn.HEADER,
		required = true,
		description = "GET /api/auth/web/csrf에서 초기화한 CSRF Token",
		schema = @Schema(type = "string")
		)
	)
	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "403", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(ref = ERROR_SCHEMA)))
	})
	public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		webAuthSessionService.logout(refreshTokenCookieManager.read(request));
		refreshTokenCookieManager.clear(response);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
