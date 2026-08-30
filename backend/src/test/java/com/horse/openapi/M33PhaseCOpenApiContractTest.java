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
class M33PhaseCOpenApiContractTest {

	private static final List<String> MONTHLY_RIDE_TYPES = List.of(
		"ALL", "GENERAL", "DRESSAGE", "JUMPING");
	private static final List<String> RIDING_CLASSES = List.of(
		"FIRST_RIDE", "ROUND_BEGINNER", "ROUND_TROT", "LARGE_ARENA_BEGINNER",
		"LARGE_ARENA_TROT", "CANTER_BEGINNER", "CANTER", "DRESSAGE", "JUMPING");
	private static final List<String> CALENDAR_STATUSES = List.of(
		"pending_admin_approval", "pending_payment", "confirmed", "completed");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void Phase_C_월간_통계와_주간_운영_캘린더_계약을_정확히_공개한다() throws Exception {
		final JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());

		final JsonNode monthly = assertOperation(
			document,
			"/api/admin/reservations/monthly-ride-statistics",
			"get",
			"getMonthlyRideStatistics");
		assertOptionalParameter(monthly, "month", "string", null);
		assertThat(parameter(monthly, "month").at("/schema/pattern").asText())
			.isEqualTo("^\\d{4}-(0[1-9]|1[0-2])$");
		assertOptionalParameter(monthly, "rideType", "string", null);
		assertThat(enumValues(parameter(monthly, "rideType").get("schema")))
			.containsExactlyElementsOf(MONTHLY_RIDE_TYPES);
		assertThat(parameter(monthly, "rideType").at("/schema/default").asText()).isEqualTo("ALL");
		assertResponseSchema(monthly, "AdminMonthlyRideStatisticsResponse");
		assertRequired(document, "AdminMonthlyRideStatisticsResponse",
			"month", "rideType", "totalCompletedRideCount", "topCompletedRideCount", "leaders");
		assertEnum(document, "AdminMonthlyRideStatisticsResponse", "rideType", MONTHLY_RIDE_TYPES);
		assertRequired(document, "AdminMonthlyRideLeaderResponse",
			"memberId", "memberName", "completedRideCount");

		final JsonNode weekly = assertOperation(
			document,
			"/api/admin/reservations/weekly-operations-calendar",
			"get",
			"getWeeklyOperationsCalendar");
		assertOptionalParameter(weekly, "referenceDate", "string", null);
		assertThat(parameter(weekly, "referenceDate").at("/schema/pattern").asText())
			.isEqualTo("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$");
		assertResponseSchema(weekly, "AdminWeeklyOperationsCalendarResponse");
		assertRequired(document, "AdminWeeklyOperationsCalendarResponse",
			"referenceDate", "weekStartDate", "weekEndDate", "timeSlots");
		assertArrayReference(document, "AdminWeeklyOperationsCalendarResponse", "timeSlots",
			"AdminWeeklyOperationsTimeSlotResponse");
		assertRequired(document, "AdminWeeklyOperationsTimeSlotResponse",
			"timeSlotId", "lessonDate", "startTime", "endTime", "totalCapacity",
			"roundArenaCapacity", "classCapacities", "closed", "reservations");
		assertArrayReference(document, "AdminWeeklyOperationsTimeSlotResponse", "reservations",
			"AdminWeeklyOperationsReservationResponse");
		assertRequired(document, "AdminWeeklyOperationsReservationResponse",
			"reservationId", "memberId", "memberName", "ridingClass", "status");
		assertEnum(document, "AdminWeeklyOperationsReservationResponse", "ridingClass", RIDING_CLASSES);
		assertEnum(document, "AdminWeeklyOperationsReservationResponse", "status", CALENDAR_STATUSES);
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

	private static void assertOptionalParameter(
		JsonNode operation,
		String name,
		String type,
		String format
	) {
		final JsonNode parameter = parameter(operation, name);
		assertThat(parameter.get("in").asText()).isEqualTo("query");
		assertThat(parameter.get("required").asBoolean()).isFalse();
		assertThat(parameter.at("/schema/type").asText()).isEqualTo(type);
		if (format != null) {
			assertThat(parameter.at("/schema/format").asText()).isEqualTo(format);
		}
	}

	private static JsonNode parameter(JsonNode operation, String name) {
		return operation.get("parameters").valueStream()
			.filter(parameter -> name.equals(parameter.get("name").asText()))
			.findFirst()
			.orElseThrow();
	}

	private static void assertResponseSchema(JsonNode operation, String schemaName) {
		assertThat(operation.at("/responses/200/content/application~1json/schema/$ref").asText())
			.isEqualTo("#/components/schemas/" + schemaName);
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

	private static void assertEnum(JsonNode document, String schemaName, String field, List<String> expected) {
		assertThat(enumValues(document.at(
			"/components/schemas/" + schemaName + "/properties/" + field)))
			.containsExactlyElementsOf(expected);
	}

	private static List<String> enumValues(JsonNode schema) {
		return schema.get("enum").valueStream().map(JsonNode::asText).toList();
	}

}
