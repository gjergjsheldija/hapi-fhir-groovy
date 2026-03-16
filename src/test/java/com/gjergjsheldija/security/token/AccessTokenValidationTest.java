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


package com.gjergjsheldija.security.token;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenValidationTest {

	@InjectMocks
	private AccessTokenValidation accessTokenValidation;

	@Mock
	private JwtDecoder jwtDecoder;

	@Mock
	private OAuth2TokenValidator<Jwt> jwtValidator;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testValidateToken_ValidToken() {
		String token = "Bearer valid_token";
		Jwt jwt = mock(Jwt.class);
		when(jwtDecoder.decode(token.substring(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER.length())))
			.thenReturn(jwt);
		when(jwtValidator.validate(jwt)).thenReturn(OAuth2TokenValidatorResult.success());
		when(jwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
		when(jwt.getIssuedAt()).thenReturn(Instant.now().minusSeconds(60));

		Jwt result = accessTokenValidation.validateToken(token);

		assertNotNull(result);
		assertEquals(jwt, result);
	}

	@Test
	void testValidateToken_InvalidToken() {
		String token = "Bearer invalid_token";
		when(jwtDecoder.decode(token.substring(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER.length())))
			.thenThrow(new JwtException("Invalid token"));

		assertThrows(AuthenticationException.class, () -> accessTokenValidation.validateToken(token));
	}

	@Test
	void testValidate_MissingExpiration() {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getExpiresAt()).thenReturn(null);
		when(jwt.getIssuedAt()).thenReturn(Instant.now().minusSeconds(60));
		when(jwtValidator.validate(jwt)).thenReturn(OAuth2TokenValidatorResult.success());

		OAuth2TokenValidatorResult result = accessTokenValidation.validate(jwt);

		assertTrue(result.hasErrors());
		assertEquals("Missing expiration", result.getErrors().iterator().next().getDescription());
	}

	@Test
	void testValidate_ExpiredToken() {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getExpiresAt()).thenReturn(Instant.now().minusSeconds(60));
		when(jwt.getIssuedAt()).thenReturn(Instant.now().minusSeconds(3600));
		when(jwtValidator.validate(jwt)).thenReturn(OAuth2TokenValidatorResult.success());

		OAuth2TokenValidatorResult result = accessTokenValidation.validate(jwt);

		assertTrue(result.hasErrors());
		assertEquals("Expired token", result.getErrors().iterator().next().getDescription());
	}

	@Test
	void testValidate_MissingIssuedAt() {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
		when(jwt.getIssuedAt()).thenReturn(null);
		when(jwtValidator.validate(jwt)).thenReturn(OAuth2TokenValidatorResult.success());

		OAuth2TokenValidatorResult result = accessTokenValidation.validate(jwt);

		assertTrue(result.hasErrors());
		assertEquals("Missing issued at", result.getErrors().iterator().next().getDescription());
	}

	@Test
	void testValidate_TokenUsedBeforeIssued() {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
		when(jwt.getIssuedAt()).thenReturn(Instant.now().plusSeconds(60));
		when(jwtValidator.validate(jwt)).thenReturn(OAuth2TokenValidatorResult.success());

		OAuth2TokenValidatorResult result = accessTokenValidation.validate(jwt);

		assertTrue(result.hasErrors());
		assertEquals("Token used before issued", result.getErrors().iterator().next().getDescription());
	}

	@Test
	void testValidate_ValidToken() {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
		when(jwt.getIssuedAt()).thenReturn(Instant.now().minusSeconds(60));
		when(jwtValidator.validate(jwt)).thenReturn(OAuth2TokenValidatorResult.success());

		OAuth2TokenValidatorResult result = accessTokenValidation.validate(jwt);

		assertFalse(result.hasErrors());
	}

	@Test
	void testGetErrorsAsThrowable() {

		Collection<OAuth2Error> errors = List.of(
			new OAuth2Error("invalid_request", "The request is missing a required parameter", "https://example.com/error/invalid_request"),
			new OAuth2Error("unauthorized_client", "The client is not authorized to request an authorization code", "https://example.com/error/unauthorized_client")
		);

		Throwable throwable = AccessTokenValidation.getErrorsAsThrowable(errors);

		String expectedMessage = "Errors: \n" +
			"Error Code: invalid_request, Description: The request is missing a required parameter, URI: https://example.com/error/invalid_request\n" +
			"Error Code: unauthorized_client, Description: The client is not authorized to request an authorization code, URI: https://example.com/error/unauthorized_client";

		assertEquals(expectedMessage, throwable.getMessage());
	}
}