/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-07-19
 */

package com.gjergjsheldija.security;

import com.gjergjsheldija.security.token.AccessTokenValidation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;


@Component
@Configuration
public class SecurityBean {

	@Value("${hapi.fhir.security.jwks}")
	private String JwksAddress;

	@Value("${hapi.fhir.security.server}")
	private String server;

	@Bean
	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withJwkSetUri(JwksAddress).build();
	}

	@Bean(name = "myAccessTokenValidation")
	public AccessTokenValidation getAccessTokenValidation() {
		NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(JwksAddress).build();
		OAuth2TokenValidator<Jwt> jwtValidator = JwtValidators.createDefaultWithIssuer(server);

		return new AccessTokenValidation(jwtDecoder, jwtValidator);
	}

}
