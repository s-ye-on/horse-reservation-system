package com.horse.schedules.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.horse.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class AdminScheduleOpenApiContractTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 일정_운영_API와_ErrorResponse_details가_OpenAPI에_노출된다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$['paths']['/api/admin/schedule-templates']").exists())
			.andExpect(jsonPath("$['paths']['/api/admin/recurring-holidays']").exists())
			.andExpect(jsonPath("$['paths']['/api/admin/schedule-sync']").exists())
			.andExpect(jsonPath("$['paths']['/api/admin/schedule-dates/{scheduleDate}/closing']").exists())
			.andExpect(jsonPath("$['paths']['/api/admin/timeslots/{timeSlotId}/closure']").exists())
			.andExpect(jsonPath("$['components']['schemas']['ErrorResponse']['properties']['details']")
				.exists())
			.andExpect(jsonPath(
				"$['components']['schemas']['ErrorResponse']['properties']['details']"
					+ "['additionalProperties']")
				.value(true))
			.andExpect(jsonPath(
				"$['components']['schemas']['ScheduleDateActionRequest']['required']")
				.value(org.hamcrest.Matchers.hasItems("reason", "expectedVersion")))
			.andExpect(jsonPath(
				"$['components']['schemas']['ScheduleSynchronizationRetryRequest']['required']")
				.value(org.hamcrest.Matchers.hasItem("pendingVersion")))
			.andExpect(jsonPath(
				"$['components']['schemas']['TimeSlotClosureActionRequest']['required']")
				.value(org.hamcrest.Matchers.hasItems("reason", "expectedVersion")))
			.andExpect(jsonPath(
				"$['components']['schemas']['TimeSlotClosureResponse']['required']")
				.value(org.hamcrest.Matchers.hasItems(
					"status",
					"version",
					"progressPercent",
					"impacts")))
			.andExpect(jsonPath(
				"$['components']['schemas']['ScheduleDateActionRequest']"
					+ "['properties']['reason']['minLength']")
				.value(1))
			.andExpect(jsonPath(
				"$['paths']['/api/admin/schedule-templates']['post']['responses']['503']"
					+ "['content']['application/json']['schema']['$ref']")
				.value("#/components/schemas/ErrorResponse"))
			.andExpect(jsonPath(
				"$['components']['schemas']['TimeSlotClosureResponse']"
					+ "['properties']['startedAt']['format']")
				.value("date-time"));
	}
}
