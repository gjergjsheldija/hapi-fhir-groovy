package com.gjergjsheldija.util;

import com.gjergjsheldija.security.token.AccessTokenValidation;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Fake tokens for {@link SecurityEnabledFhirR4ForTesting}: the union of what the security tests need,
 * so they share one cached Spring context.
 * Deliberately not @TestConfiguration: that is a @Component and would be component-scanned into other test contexts.
 */
public class TestTokenConfig {

	@Bean(name = "myAccessTokenValidation")
	AccessTokenValidation accessTokenValidation() {
		JwtDecoder decoder = token -> {
			Jwt.Builder jwt = Jwt.withTokenValue(token).header("alg", "none").subject("tester")
				.issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(3600));
			return switch (token) {
				case "admin-token" -> jwt.claim("scope", "openid app:admin").build();
				case "good-token" -> jwt.claim("scope", "app:admin").build();
				case "kc-admin-token" -> jwt.claim("realm_access", Map.of("roles", List.of("app:admin"))).build();
				case "near-token" -> jwt.claim("scope", "app:admin-readonly app:administrator").build();
				case "user-token" -> jwt.claim("scope", "openid").build();
				default -> throw new BadJwtException("bad token");
			};
		};
		return new AccessTokenValidation(decoder, jwt -> OAuth2TokenValidatorResult.success());
	}
}
