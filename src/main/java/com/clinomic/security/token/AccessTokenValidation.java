/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Leipzig
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Leipzig
 * @license All rights reserved.
 * @since 2024-07-19
 */

package com.clinomic.security.token;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;


@Component
public class AccessTokenValidation implements OAuth2TokenValidator<Jwt> {

	private final JwtDecoder jwtDecoder;
	private final OAuth2TokenValidator<Jwt> jwtValidator;

	public AccessTokenValidation(JwtDecoder jwtDecoder, OAuth2TokenValidator<Jwt> jwtValidator) {
		this.jwtDecoder = jwtDecoder;
		this.jwtValidator = jwtValidator;
	}

	public Jwt validateToken(String token) {
		token = token.substring(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER.length());

		try {
			Jwt jwt = jwtDecoder.decode(token);
			OAuth2TokenValidatorResult result = this.validate(jwt);

			if (result.hasErrors()) {
				throw new AuthenticationException("Invalid JWT", getErrorsAsThrowable(result.getErrors()));
			}

			return jwt;
		} catch (JwtException e) {
			throw new AuthenticationException("Invalid JWT", e);
		}
	}

	public static Throwable getErrorsAsThrowable(Collection<OAuth2Error> errors) {
		StringBuilder errorMessage = new StringBuilder("Errors: ");
		for (OAuth2Error error : errors) {
			errorMessage.append("\n")
				.append("Error Code: ").append(error.getErrorCode())
				.append(", Description: ").append(error.getDescription())
				.append(", URI: ").append(error.getUri());
		}
		return new Throwable(errorMessage.toString());
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		OAuth2TokenValidatorResult result = jwtValidator.validate(jwt);

		if (result.hasErrors()) {
			return result;
		}

		Instant now = Instant.now().plus(1000, ChronoUnit.MILLIS);
		Instant expiresAt = jwt.getExpiresAt();
		Instant issuedAt = jwt.getIssuedAt();

		// check expiration
		if (expiresAt == null) {
			OAuth2Error error = new OAuth2Error("invalid_token", "Missing expiration", null);
			return OAuth2TokenValidatorResult.failure(error);
		}

		// check not before
		if (now.isAfter(expiresAt)) {
			OAuth2Error error = new OAuth2Error("invalid_token", "Expired token", null);
			return OAuth2TokenValidatorResult.failure(error);
		}

		if (issuedAt == null) {
			OAuth2Error error = new OAuth2Error("invalid_token", "Missing issued at", null);
			return OAuth2TokenValidatorResult.failure(error);
		}

		// check issued at
		if (now.isBefore(issuedAt)) {
			OAuth2Error error = new OAuth2Error("invalid_token", "Token used before issued", null);
			return OAuth2TokenValidatorResult.failure(error);
		}

		return OAuth2TokenValidatorResult.success();
	}
}