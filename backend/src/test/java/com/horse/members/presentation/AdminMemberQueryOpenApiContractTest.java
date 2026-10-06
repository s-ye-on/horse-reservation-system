package com.horse.members.presentation;

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
class AdminMemberQueryOpenApiContractTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 기존_회원_목록에_선택적_검색어와_최대_길이를_노출한다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath(
				"$['paths']['/api/admin/members']['get']['parameters']"
					+ "[?(@['name'] == 'query')]['required']").value(false))
			.andExpect(jsonPath(
				"$['paths']['/api/admin/members']['get']['parameters']"
					+ "[?(@['name'] == 'query')]['schema']['maxLength']").value(100));
	}
}
