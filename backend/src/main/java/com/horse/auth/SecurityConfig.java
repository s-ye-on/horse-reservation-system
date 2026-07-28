package com.horse.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		JwtAuthenticationConverter jwtAuthenticationConverter,
		JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
		JwtAccessDeniedHandler jwtAccessDeniedHandler) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
				.requestMatchers("/api/admin/**").hasAuthority(UserRole.ADMIN.authority())
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll())
			.exceptionHandling(exceptionHandling -> exceptionHandling
				.authenticationEntryPoint(jwtAuthenticationEntryPoint)
				.accessDeniedHandler(jwtAccessDeniedHandler))
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint(jwtAuthenticationEntryPoint)
				.accessDeniedHandler(jwtAccessDeniedHandler))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable);

		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
		SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(secretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
	}

	@Bean
	JwtRoleAuthoritiesConverter jwtRoleAuthoritiesConverter() {
		return new JwtRoleAuthoritiesConverter();
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter(JwtRoleAuthoritiesConverter authoritiesConverter) {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}
}
