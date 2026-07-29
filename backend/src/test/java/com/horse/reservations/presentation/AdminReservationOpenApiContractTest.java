package com.horse.reservations.presentation;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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
class AdminReservationOpenApiContractTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 관리자_예약_조회_조건은_평면_Query_Parameter로_노출된다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['/api/admin/reservations']['get']['parameters']",
				hasSize(8)))
			.andExpect(jsonPath(
				"$['paths']['/api/admin/reservations']['get']['parameters'][*]['name']",
				containsInAnyOrder(
					"status",
					"lessonDateFrom",
					"lessonDateTo",
					"classType",
					"keyword",
					"sort",
					"page",
					"size")));
	}
}
