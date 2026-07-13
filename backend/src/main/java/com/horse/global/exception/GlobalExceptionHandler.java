package com.horse.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
		ErrorResponse response = new ErrorResponse(exception.code(), exception.getMessage());
		return ResponseEntity.status(exception.status()).body(response);
	}

}
