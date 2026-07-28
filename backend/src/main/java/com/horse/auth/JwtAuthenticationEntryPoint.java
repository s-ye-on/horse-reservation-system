package com.horse.auth;

import java.io.IOException;

import com.horse.global.exception.ErrorResponse;
import com.horse.global.exception.ErrorResponseWriter;
import com.horse.global.exception.ExceptionCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ErrorResponseWriter errorResponseWriter;

	public JwtAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException authenticationException
	) throws IOException {
		errorResponseWriter.write(response, ErrorResponse.from(
			ExceptionCode.COMMON_UNAUTHORIZED,
			request.getRequestURI()
		));
	}
}
