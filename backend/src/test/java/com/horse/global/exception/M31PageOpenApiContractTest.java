package com.horse.global.exception;

import static org.hamcrest.Matchers.contains;
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
		assertPageEndpoint(
			"/api/me/reservations",
			"MemberReservationPageResponse",
			"displayGroup",
			"status");
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

	@Test
	void 회원_예약_목록은_표시_그룹과_전체_예약_상태_필터를_노출한다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['/api/me/reservations']['get']['parameters']"
					+ "[?(@.name == 'displayGroup')]['schema']['enum']",
				contains(containsInAnyOrder("UPCOMING", "PAST"))))
			.andExpect(jsonPath(
				"$['paths']['/api/me/reservations']['get']['parameters']"
					+ "[?(@.name == 'status')]['schema']['enum']",
				contains(containsInAnyOrder(
					"pending_admin_approval",
					"pending_payment",
					"payment_expired",
					"approval_expired",
					"confirmed",
					"completed",
					"rejected",
					"cancelled",
					"no_show"))))
			.andExpect(jsonPath(
				"$['paths']['/api/me/reservations']['get']['responses']['400']"
					+ "['content']['application/json']['schema']['$ref']")
				.value("#/components/schemas/ErrorResponse"));
	}

	private void assertPageEndpoint(String path, String schemaName, String... additionalParameters)
		throws Exception {
		final String[] parameterNames = new String[additionalParameters.length + 2];
		parameterNames[0] = "page";
		parameterNames[1] = "size";
		System.arraycopy(additionalParameters, 0, parameterNames, 2, additionalParameters.length);

		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters']",
				hasSize(parameterNames.length)))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters'][*]['name']",
				containsInAnyOrder(parameterNames)))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters']"
					+ "[?(@.name == 'page')]['schema']['minimum']")
				.value(0))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters']"
					+ "[?(@.name == 'size')]['schema']['minimum']")
				.value(1))
			.andExpect(jsonPath(
				"$['paths']['" + path + "']['get']['parameters']"
					+ "[?(@.name == 'size')]['schema']['maximum']")
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
