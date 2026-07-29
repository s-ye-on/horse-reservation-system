package com.horse.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import com.horse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class M31ApiTimeOpenApiContractTest {

	private static final Set<String> PAGE_REQUIRED_FIELDS = Set.of(
		"content", "page", "size", "totalElements", "totalPages", "hasNext"
	);
	private static final Set<String> ERROR_REQUIRED_FIELDS = Set.of(
		"code", "message", "status", "timestamp", "path", "fieldErrors", "details"
	);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void 모든_응답_DTO는_직렬화되는_필드를_required로_노출한다() throws Exception {
		final JsonNode schemas = apiDocs().path("components").path("schemas");

		for (String name : schemas.propertyNames()) {
			if (!name.endsWith("Response") && !name.equals("FieldError")) {
				continue;
			}
			final JsonNode schema = schemas.path(name);
			assertThat(fieldNames(schema.path("required")))
				.as(name + " required fields")
				.containsExactlyInAnyOrderElementsOf(fieldNames(schema.path("properties")));
		}
	}

	@Test
	void Page와_ErrorResponse의_필수_필드_계약을_유지한다() throws Exception {
		final JsonNode schemas = apiDocs().path("components").path("schemas");

		assertThat(fieldNames(schemas.path("AdminReservationPageResponse").path("required")))
			.containsExactlyInAnyOrderElementsOf(PAGE_REQUIRED_FIELDS);
		assertThat(fieldNames(schemas.path("MemberCouponPageResponse").path("required")))
			.containsExactlyInAnyOrderElementsOf(PAGE_REQUIRED_FIELDS);
		assertThat(fieldNames(schemas.path("ErrorResponse").path("required")))
			.containsExactlyInAnyOrderElementsOf(ERROR_REQUIRED_FIELDS);
	}

	@Test
	void 절대_시점과_날짜와_현지_시각은_서로_다른_format을_노출한다() throws Exception {
		final JsonNode schemas = apiDocs().path("components").path("schemas");

		assertFormat(schemas, "AdminReservationResponse", "createdAt", "date-time");
		assertFormat(schemas, "AdminReservationResponse", "updatedAt", "date-time");
		assertFormat(schemas, "ReservationCancelResponse", "cancelledAt", "date-time");
		assertFormat(schemas, "ReservationApplicationResponse", "paymentDueAt", "date-time");
		assertFormat(schemas, "AdminReservationAuditResponse", "occurredAt", "date-time");
		assertFormat(schemas, "TimeSlotResponse", "lessonDate", "date");
		assertFormat(schemas, "TimeSlotResponse", "startTime", "time");
		assertFormat(schemas, "ScheduleTemplateResponse", "startTime", "time");
		assertFormat(schemas, "ScheduleTemplateResponse", "endTime", "time");
	}

	@Test
	void nullable_객체는_필수_키이면서_reference와_null을_모두_허용한다() throws Exception {
		final JsonNode schemas = apiDocs().path("components").path("schemas");

		assertNullableReference(
			schemas.path("ReservationApplicationResponse").path("properties").path("coupon"),
			"#/components/schemas/ReservationCouponResponse"
		);
		assertNullableReference(
			schemas.path("MemberReservationResponse").path("properties").path("coupon"),
			"#/components/schemas/MemberReservationCouponResponse"
		);
		assertNullableReference(
			schemas.path("AdminReservationResponse").path("properties").path("coupon"),
			"#/components/schemas/AdminReservationCouponResponse"
		);
	}

	@Test
	void header와_path와_query의_required_여부는_Controller_계약과_일치한다() throws Exception {
		final JsonNode paths = apiDocs().path("paths");

		assertThat(parameter(paths, "/api/reservations", "post", "Idempotency-Key").path("required").asBoolean())
			.isTrue();
		assertThat(parameter(paths, "/api/admin/reservations", "post", "Idempotency-Key")
			.path("required").asBoolean()).isTrue();
		assertThat(parameter(paths, "/api/me/reservations/{reservationId}", "get", "reservationId")
			.path("required").asBoolean()).isTrue();
		assertThat(parameter(paths, "/api/me/reservations", "get", "page").path("required").asBoolean())
			.isFalse();
	}

	private JsonNode apiDocs() throws Exception {
		final String body = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body);
	}

	private void assertFormat(JsonNode schemas, String schemaName, String propertyName, String format) {
		assertThat(schemas.path(schemaName).path("properties").path(propertyName).path("format").asString())
			.isEqualTo(format);
	}

	private void assertNullableReference(JsonNode property, String reference) {
		assertThat(property.path("oneOf").size()).isEqualTo(2);
		assertThat(property.path("oneOf").path(0).path("$ref").asString()).isEqualTo(reference);
		assertThat(property.path("oneOf").path(1).path("type").asString()).isEqualTo("null");
	}

	private JsonNode parameter(JsonNode paths, String path, String method, String name) {
		for (JsonNode parameter : paths.path(path).path(method).path("parameters")) {
			if (name.equals(parameter.path("name").asString())) {
				return parameter;
			}
		}
		throw new AssertionError("OpenAPI parameter not found: " + method + " " + path + " " + name);
	}

	private Set<String> fieldNames(JsonNode node) {
		final Set<String> names = new HashSet<>();
		if (node.isArray()) {
			node.forEach(item -> names.add(item.asString()));
			return names;
		}
		names.addAll(node.propertyNames());
		return names;
	}
}
