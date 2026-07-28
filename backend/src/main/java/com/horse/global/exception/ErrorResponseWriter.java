package com.horse.global.exception;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class ErrorResponseWriter {

	private final ObjectMapper objectMapper;

	public ErrorResponseWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletResponse response, ErrorResponse errorResponse) throws IOException {
		if (response.isCommitted()) {
			return;
		}

		response.setStatus(errorResponse.status());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), errorResponse);
	}
}
