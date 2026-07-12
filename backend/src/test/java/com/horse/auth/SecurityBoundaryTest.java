package com.horse.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({ SecurityConfig.class, SecurityBoundaryTest.TestEndpoints.class })
@WebMvcTest(controllers = SecurityBoundaryTest.TestEndpoints.class)
class SecurityBoundaryTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtRoleAuthoritiesConverter authoritiesConverter;

	@Test
	void 헬스_체크는_인증_없이_접근할_수_있다() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk());
	}

	@Test
	void API는_인증이_필요하다() throws Exception {
		mockMvc.perform(get("/api/profile"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 회원은_회원_API에_접근하고_관리자_API에는_접근할_수_없다() throws Exception {
		var memberJwt = jwt().authorities(new SimpleGrantedAuthority(UserRole.MEMBER.authority()));

		mockMvc.perform(get("/api/profile").with(memberJwt))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/dashboard").with(memberJwt))
			.andExpect(status().isForbidden());
	}

	@Test
	void 관리자는_관리자_API에_접근할_수_있다() throws Exception {
		mockMvc.perform(get("/api/admin/dashboard")
				.with(jwt().authorities(new SimpleGrantedAuthority(UserRole.ADMIN.authority()))))
			.andExpect(status().isOk());
	}

	@Test
	void 역할_클레임을_역할_권한으로_변환한다() {
		Jwt token = new Jwt(
			"token",
			Instant.now(),
			Instant.now().plusSeconds(60),
			Map.of("alg", "HS256"),
			Map.of("sub", "member-1", "roles", List.of("MEMBER", "ADMIN")));

		var authorities = authoritiesConverter.convert(token);

		org.assertj.core.api.Assertions.assertThat(authorities)
			.extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_MEMBER", "ROLE_ADMIN");
	}

	@RestController
	static class TestEndpoints {
		@GetMapping("/actuator/health")
		String health() {
			return "UP";
		}

		@GetMapping("/api/profile")
		String profile() {
			return "profile";
		}

		@GetMapping("/api/admin/dashboard")
		String adminDashboard() {
			return "admin";
		}
	}
}
