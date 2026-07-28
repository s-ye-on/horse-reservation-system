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
class ReservationApplicationOpenApiContractTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 예약_생성은_필수_Idempotency_Key와_구조화된_성공_응답을_노출한다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['/api/reservations']['post']['parameters']"
					+ "[?(@['name'] == 'Idempotency-Key')]['in']")
				.value("header"))
			.andExpect(jsonPath(
				"$['paths']['/api/reservations']['post']['parameters']"
					+ "[?(@['name'] == 'Idempotency-Key')]['required']")
				.value(true))
			.andExpect(jsonPath(
				"$['paths']['/api/reservations']['post']['responses']['201']"
					+ "['content']['application/json']['schema']['$ref']")
				.value("#/components/schemas/ReservationApplicationResponse"));
	}
}
