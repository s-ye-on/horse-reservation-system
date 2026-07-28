package com.horse.auth;

import java.io.IOException;

import com.horse.global.exception.ErrorResponse;
import com.horse.global.exception.ErrorResponseWriter;
import com.horse.global.exception.ExceptionCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

	private final ErrorResponseWriter errorResponseWriter;

	public JwtAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		AccessDeniedException accessDeniedException
	) throws IOException {
		errorResponseWriter.write(response, ErrorResponse.from(
			ExceptionCode.COMMON_FORBIDDEN,
			request.getRequestURI()
		));
	}
}
