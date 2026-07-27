package com.horse.openapi;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.horse.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.core.converter.ModelConverters;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
	title = "Horse Reservation API",
	version = "v1",
	description = "마장 예약 서비스 API"))
@SecurityScheme(
	name = "bearerAuth",
	type = SecuritySchemeType.HTTP,
	scheme = "bearer",
	bearerFormat = "JWT")
public class OpenApiConfig {

	@Bean
	OpenApiCustomizer errorResponseSchemaCustomizer() {
		return openApi -> ModelConverters.getInstance()
			.readAll(ErrorResponse.class)
			.forEach(openApi.getComponents()::addSchemas);
	}
}
