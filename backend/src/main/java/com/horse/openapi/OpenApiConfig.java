package com.horse.openapi;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.horse.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
	title = "Horse Reservation API",
	version = "v1",
	description = "마장 예약 서비스 API"),
	servers = @Server(url = "http://localhost:8080", description = "Generated server url"))
@SecurityScheme(
	name = "bearerAuth",
	type = SecuritySchemeType.HTTP,
	scheme = "bearer",
	bearerFormat = "JWT")
public class OpenApiConfig {

	@Bean
	OpenApiCustomizer apiSchemaCustomizer() {
		return openApi -> {
			ModelConverters.getInstance()
				.readAll(ErrorResponse.class)
				.forEach(openApi.getComponents()::addSchemas);
			openApi.getComponents().getSchemas().forEach((name, schema) -> {
				normalizeNullableReferences(schema);
				requireSerializedResponseProperties(name, schema);
			});
		};
	}

	private static void normalizeNullableReferences(Schema<?> schema) {
		if (schema.getProperties() == null) {
			return;
		}
		schema.getProperties().replaceAll((name, property) -> {
			if (property.get$ref() == null || property.getTypes() == null
				|| !property.getTypes().contains("null")) {
				return property;
			}
			return new ComposedSchema()
				.addOneOfItem(new Schema<>().$ref(property.get$ref()))
				.addOneOfItem(new Schema<>().types(Set.of("null")));
		});
	}

	private static void requireSerializedResponseProperties(String name, Schema<?> schema) {
		if (!isSerializedResponse(name) || schema.getProperties() == null) {
			return;
		}
		schema.setRequired(List.copyOf(schema.getProperties().keySet()));
	}

	private static boolean isSerializedResponse(String name) {
		return name.endsWith("Response") || name.equals("FieldError");
	}
}
