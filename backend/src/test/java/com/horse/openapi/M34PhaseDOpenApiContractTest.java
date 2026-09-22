package com.horse.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.horse.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class M34PhaseDOpenApiContractTest {

	private static final List<String> RIDING_CLASSES = List.of(
		"FIRST_RIDE", "ROUND_BEGINNER", "ROUND_TROT", "LARGE_ARENA_BEGINNER",
		"LARGE_ARENA_TROT", "CANTER_BEGINNER", "CANTER", "DRESSAGE", "JUMPING");
	private static final List<String> OCCUPYING_STATUSES = List.of(
		"pending_admin_approval", "pending_payment", "confirmed");
	private static final List<String> GENERAL_TEMPLATE_CONFLICT_CODES = List.of(
		"SCHEDULE_CONFIG_VERSION_CONFLICT", "SCHEDULE_TEMPLATE_ALREADY_EXISTS");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void Phase_D_Template_lifecycle_계약을_정확히_공개한다() throws Exception {
		final JsonNode document = document();

		final JsonNode futureReservations = assertOperation(
			document,
			"/api/admin/schedule-templates/{templateId}/future-occupying-reservations",
			"get",
			"getFutureOccupyingReservations");
		assertPathParameter(futureReservations, "templateId", "integer", "int64");
		assertResponseSchema(futureReservations, "200", "ScheduleTemplateFutureReservationsResponse");
		assertRequired(document, "ScheduleTemplateFutureReservationsResponse",
			"templateId", "reservationCount", "reservations");
		assertArrayReference(document, "ScheduleTemplateFutureReservationsResponse", "reservations",
			"ScheduleTemplateFutureReservationResponse");
		assertRequired(document, "ScheduleTemplateFutureReservationResponse",
			"reservationId", "lessonDate", "startTime", "endTime", "memberId", "memberName",
			"memberPhone", "ridingClass", "status");
		assertProperty(document, "ScheduleTemplateFutureReservationResponse", "lessonDate", "string", "date");
		assertProperty(document, "ScheduleTemplateFutureReservationResponse", "startTime", "string", "time");
		assertProperty(document, "ScheduleTemplateFutureReservationResponse", "endTime", "string", "time");
		assertEnum(document, "ScheduleTemplateFutureReservationResponse", "ridingClass", RIDING_CLASSES);
		assertEnum(document, "ScheduleTemplateFutureReservationResponse", "status", OCCUPYING_STATUSES);

		final JsonNode delete = assertOperation(
			document,
			"/api/admin/schedule-templates/{templateId}",
			"delete",
			"delete");
		assertPathParameter(delete, "templateId", "integer", "int64");
		assertRequestSchema(delete, "ScheduleTemplateDeleteRequest");
		assertRequired(document, "ScheduleTemplateDeleteRequest", "expectedConfigVersion", "reason");
		assertResponseSchema(delete, "200", "ScheduleTemplateMutationResponse");

		final JsonNode create = assertOperation(
			document,
			"/api/admin/schedule-templates",
			"post",
			"create");
		assertRequestSchema(create, "ScheduleTemplateRequest");
		assertResponseSchema(create, "201", "ScheduleTemplateMutationResponse");
		assertCapacityConflictContract(document, create);

		final JsonNode update = assertOperation(
			document,
			"/api/admin/schedule-templates/{templateId}",
			"put",
			"update");
		assertCapacityConflictContract(document, update);
	}

	private JsonNode document() throws Exception {
		return objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private static JsonNode assertOperation(
		JsonNode document,
		String path,
		String method,
		String operationId
	) {
		final JsonNode operation = document.at("/paths/" + path.replace("/", "~1") + "/" + method);
		assertThat(operation.get("operationId").asText()).isEqualTo(operationId);
		final JsonNode security = operation.has("security") ? operation.get("security") : document.get("security");
		assertThat(security.at("/0/bearerAuth").isArray()).isTrue();
		return operation;
	}

	private static void assertPathParameter(
		JsonNode operation,
		String name,
		String type,
		String format
	) {
		final JsonNode parameter = operation.get("parameters").valueStream()
			.filter(current -> name.equals(current.get("name").asText()))
			.findFirst()
			.orElseThrow();
		assertThat(parameter.get("in").asText()).isEqualTo("path");
		assertThat(parameter.get("required").asBoolean()).isTrue();
		assertThat(parameter.at("/schema/type").asText()).isEqualTo(type);
		assertThat(parameter.at("/schema/format").asText()).isEqualTo(format);
	}

	private static void assertRequestSchema(JsonNode operation, String schemaName) {
		assertThat(operation.at("/requestBody/required").asBoolean()).isTrue();
		assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asText())
			.isEqualTo("#/components/schemas/" + schemaName);
	}

	private static void assertResponseSchema(JsonNode operation, String status, String schemaName) {
		assertThat(operation.at("/responses/" + status + "/content/application~1json/schema/$ref").asText())
			.isEqualTo("#/components/schemas/" + schemaName);
	}

	private static void assertCapacityConflictContract(JsonNode document, JsonNode operation) {
		assertResponseSchema(operation, "409", "ScheduleTemplateMutationConflictResponse");
		assertSchemaOneOf(document, "ScheduleTemplateMutationConflictResponse",
			"ScheduleTemplateGeneralConflictErrorResponse",
			"ScheduleTemplateCapacityConflictErrorResponse");
		assertConflictDiscriminator(document);
		assertRequired(document, "ScheduleTemplateGeneralConflictErrorResponse",
			"code", "message", "status", "timestamp", "path", "fieldErrors", "details");
		assertEnum(document, "ScheduleTemplateGeneralConflictErrorResponse", "code",
			GENERAL_TEMPLATE_CONFLICT_CODES);
		assertThat(document.at(
			"/components/schemas/ScheduleTemplateGeneralConflictErrorResponse/properties/details/"
				+ "additionalProperties").asBoolean()).isTrue();
		assertRequired(document, "ScheduleTemplateCapacityConflictErrorResponse",
			"code", "message", "status", "timestamp", "path", "fieldErrors", "details");
		assertEnum(document, "ScheduleTemplateCapacityConflictErrorResponse", "code",
			List.of("TIMESLOT_CAPACITY_BELOW_OCCUPANCY"));
		assertReference(document, "ScheduleTemplateCapacityConflictErrorResponse", "details",
			"ScheduleTemplateCapacityConflictDetails");
		assertRequired(document, "ScheduleTemplateCapacityConflictDetails",
			"lessonDate", "startTime", "totalOccupied", "roundArenaOccupied", "classOccupied",
			"requestedTotalCapacity", "requestedRoundArenaCapacity", "requestedClassCapacities");
		assertProperty(document, "ScheduleTemplateCapacityConflictDetails", "lessonDate", "string", "date");
		assertProperty(document, "ScheduleTemplateCapacityConflictDetails", "startTime", "string", "time");
		assertIntegerProperty(document, "ScheduleTemplateCapacityConflictDetails", "totalOccupied");
		assertIntegerProperty(document, "ScheduleTemplateCapacityConflictDetails", "roundArenaOccupied");
		assertIntegerMap(document, "ScheduleTemplateCapacityConflictDetails", "classOccupied");
		assertIntegerProperty(document, "ScheduleTemplateCapacityConflictDetails", "requestedTotalCapacity");
		assertIntegerProperty(document, "ScheduleTemplateCapacityConflictDetails", "requestedRoundArenaCapacity");
		assertIntegerMap(document, "ScheduleTemplateCapacityConflictDetails", "requestedClassCapacities");
	}

	private static void assertConflictDiscriminator(JsonNode document) {
		final JsonNode discriminator = document.at(
			"/components/schemas/ScheduleTemplateMutationConflictResponse/discriminator");
		assertThat(discriminator.get("propertyName").asText()).isEqualTo("code");
		assertThat(discriminator.at("/mapping/SCHEDULE_CONFIG_VERSION_CONFLICT").asText())
			.isEqualTo("#/components/schemas/ScheduleTemplateGeneralConflictErrorResponse");
		assertThat(discriminator.at("/mapping/SCHEDULE_TEMPLATE_ALREADY_EXISTS").asText())
			.isEqualTo("#/components/schemas/ScheduleTemplateGeneralConflictErrorResponse");
		assertThat(discriminator.at("/mapping/TIMESLOT_CAPACITY_BELOW_OCCUPANCY").asText())
			.isEqualTo("#/components/schemas/ScheduleTemplateCapacityConflictErrorResponse");
	}

	private static void assertSchemaOneOf(JsonNode document, String schemaName, String... schemaNames) {
		final List<String> actual = document.at(
			"/components/schemas/" + schemaName + "/oneOf")
			.valueStream()
			.map(schema -> schema.get("$ref").asText())
			.toList();
		assertThat(actual).containsExactly(
			List.of(schemaNames).stream()
				.map(name -> "#/components/schemas/" + name)
				.toArray(String[]::new));
	}

	private static void assertReference(
		JsonNode document,
		String schemaName,
		String field,
		String referencedSchema
	) {
		assertThat(document.at(
			"/components/schemas/" + schemaName + "/properties/" + field + "/$ref").asText())
			.isEqualTo("#/components/schemas/" + referencedSchema);
	}

	private static void assertIntegerProperty(JsonNode document, String schemaName, String field) {
		assertThat(document.at(
			"/components/schemas/" + schemaName + "/properties/" + field + "/type").asText())
			.isEqualTo("integer");
	}

	private static void assertIntegerMap(JsonNode document, String schemaName, String field) {
		final JsonNode property = document.at(
			"/components/schemas/" + schemaName + "/properties/" + field);
		assertThat(property.get("type").asText()).isEqualTo("object");
		assertThat(property.at("/additionalProperties/type").asText()).isEqualTo("integer");
	}

	private static void assertRequired(JsonNode document, String schemaName, String... fields) {
		final List<String> required = document.at("/components/schemas/" + schemaName + "/required")
			.valueStream()
			.map(JsonNode::asText)
			.toList();
		assertThat(required).contains(fields);
	}

	private static void assertArrayReference(
		JsonNode document,
		String schemaName,
		String field,
		String referencedSchema
	) {
		assertThat(document.at("/components/schemas/" + schemaName + "/properties/" + field + "/items/$ref")
			.asText())
			.isEqualTo("#/components/schemas/" + referencedSchema);
	}

	private static void assertProperty(
		JsonNode document,
		String schemaName,
		String field,
		String type,
		String format
	) {
		final JsonNode property = document.at(
			"/components/schemas/" + schemaName + "/properties/" + field);
		assertThat(property.get("type").asText()).isEqualTo(type);
		assertThat(property.get("format").asText()).isEqualTo(format);
	}

	private static void assertEnum(JsonNode document, String schemaName, String field, List<String> expected) {
		final List<String> actual = document.at(
			"/components/schemas/" + schemaName + "/properties/" + field + "/enum")
			.valueStream()
			.map(JsonNode::asText)
			.toList();
		assertThat(actual).containsExactlyElementsOf(expected);
	}
}
