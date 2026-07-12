package com.horse.auth;

import java.util.Collection;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class JwtRoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	private final JwtGrantedAuthoritiesConverter delegate;

	public JwtRoleAuthoritiesConverter() {
		this.delegate = new JwtGrantedAuthoritiesConverter();
		this.delegate.setAuthoritiesClaimName("roles");
		this.delegate.setAuthorityPrefix("ROLE_");
	}

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		return delegate.convert(jwt);
	}
}
