package com.horse.reservations.presentation;

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
class AdminManualReservationOpenApiContractTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 관리자_수동_예약은_필수_Idempotency_Key와_요청_응답_계약을_노출한다()
		throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['/api/admin/reservations']['post']['parameters']"
					+ "[?(@['name'] == 'Idempotency-Key')]['in']")
				.value("header"))
			.andExpect(jsonPath(
				"$['paths']['/api/admin/reservations']['post']['parameters']"
					+ "[?(@['name'] == 'Idempotency-Key')]['required']")
				.value(true))
			.andExpect(jsonPath(
				"$['paths']['/api/admin/reservations']['post']['requestBody']"
					+ "['content']['application/json']['schema']['$ref']")
				.value("#/components/schemas/AdminManualReservationRequest"))
			.andExpect(jsonPath(
				"$['paths']['/api/admin/reservations']['post']['responses']['201']"
					+ "['content']['application/json']['schema']['$ref']")
				.value("#/components/schemas/ReservationApplicationResponse"))
			.andExpect(jsonPath(
				"$['components']['schemas']['AdminManualReservationRequest']['required']")
				.value(org.hamcrest.Matchers.containsInAnyOrder(
					"memberId",
					"timeSlotId",
					"classType",
					"reason")));
	}
}
