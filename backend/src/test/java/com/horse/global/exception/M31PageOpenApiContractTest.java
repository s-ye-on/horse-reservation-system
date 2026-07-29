package com.horse.global.exception;

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
class M31PageOpenApiContractTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 대상_목록은_평면_페이지_조건과_6필드_Page_응답을_노출한다() throws Exception {
		assertPageEndpoint("/api/admin/members", "AdminMemberPageResponse");
		assertPageEndpoint("/api/me/reservations", "MemberReservationPageResponse");
		assertPageEndpoint("/api/me/coupons", "MemberCouponPageResponse");
		assertPageEndpoint("/api/me/coupon-usage-logs", "MemberCouponUsagePageResponse");

		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['components']['schemas']['AdminReservationPageResponse']['properties'].*",
				hasSize(6)))
			.andExpect(jsonPath(
				"$['components']['schemas']['AdminReservationAuditPageResponse']['properties'].*",
				hasSize(6)));
	}

	@Test
	void 회원_예약_상세는_명시적인_응답_DTO를_노출한다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['/api/me/reservations/{reservationId}']['get']"
					+ "['parameters'][0]['name']")
				.value("reservationId"))
			.andExpect(jsonPath(
				"$['paths']['/api/me/reservations/{reservationId}']['get']"
					+ "['parameters'][0]['required']")
				.value(true))
			.andExpect(jsonPath(
				"$['paths']['/api/me/reservations/{reservationId}']['get']"
					+ "['responses']['200']['content']['*/*']['schema']['$ref']")
				.value("#/components/schemas/MemberReservationResponse"));
	}

	private void assertPageEndpoint(String path, String schemaName) throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters']",
				hasSize(2)))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters'][*]['name']",
				containsInAnyOrder("page", "size")))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters'][0]['schema']['minimum']")
				.value(0))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters'][1]['schema']['minimum']")
				.value(1))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters'][1]['schema']['maximum']")
				.value(100))
			.andExpect(jsonPath(
				"$['components']['schemas']['" + schemaName + "']['properties'].*",
				hasSize(6)))
			.andExpect(jsonPath(
				"$['components']['schemas']['" + schemaName + "']['properties'].content")
				.exists())
			.andExpect(jsonPath(
				"$['components']['schemas']['" + schemaName + "']['properties'].hasNext")
				.exists());
	}
}
