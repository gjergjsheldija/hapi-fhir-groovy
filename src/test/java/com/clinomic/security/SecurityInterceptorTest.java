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

package com.clinomic.security;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.clinomic.security.token.AccessTokenValidation;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityInterceptorTest {

	@Mock
	private ServletRequestDetails theRequestDetails;

	@InjectMocks
	private SecurityInterceptor securityInterceptor;

	@Mock
	private AccessTokenValidation accessTokenValidation;

	@Mock
	private RequestDetails requestDetails;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testBuildRuleList_SecurityDisabled() {
		ReflectionTestUtils.setField(securityInterceptor, "securityEnabled", false);

		List<IAuthRule> adminRules = securityInterceptor.createAdminRules();
		List<IAuthRule> rules = securityInterceptor.buildRuleList(requestDetails);

		assertFalse(rules.isEmpty());
		assertEquals(adminRules.toString(), rules.toString());
	}

	@Test
	void testBuildRuleList_NoAuthHeader() {
		ReflectionTestUtils.setField(securityInterceptor, "securityEnabled", true);
		when(requestDetails.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

		List<IAuthRule> rules = securityInterceptor.buildRuleList(requestDetails);
		List<IAuthRule> errorRules = securityInterceptor.buildErrorRules("No authentication mechanism provided, missing Authentication headers");

		assertFalse(rules.isEmpty());
		assertEquals(errorRules.toString(), rules.toString());
	}

	@Test
	void testBuildRuleList_ValidToken() {
		String validToken = "valid_token";
		List<IAuthRule> adminRules = securityInterceptor.createAdminRules();
		Jwt validJwt = createMockJwt("user123", List.of("READ", "WRITE"));

		ReflectionTestUtils.setField(securityInterceptor, "securityEnabled", true);

		when(requestDetails.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(validToken);
		when(accessTokenValidation.validateToken(any())).thenReturn(validJwt);

		List<IAuthRule> rules = securityInterceptor.buildRuleList(requestDetails);

		assertFalse(rules.isEmpty());
		assertEquals(adminRules.toString(), rules.toString());
		verify(accessTokenValidation).validateToken(validToken);
	}

	@Test
	void testBuildRuleList_InvalidToken() throws Exception {
		String invalidToken = "invalid_token";
		ReflectionTestUtils.setField(securityInterceptor, "securityEnabled", true);

		List<IAuthRule> errorRules = securityInterceptor.buildErrorRules("No authentication mechanism provided, missing Authentication headers");

		when(requestDetails.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + invalidToken);
		doThrow(new JwtException("Invalid token")).when(accessTokenValidation).validateToken(any());

		List<IAuthRule> rules = securityInterceptor.buildRuleList(requestDetails);

		assertFalse(rules.isEmpty());
		assertEquals(errorRules.toString(), rules.toString());
	}

	private Jwt createMockJwt(String subject, List<String> scopes) {
		Instant now = Instant.now();
		Map<String, Object> headers = new HashMap<>();
		headers.put("alg", "RS256");

		Map<String, Object> claims = new HashMap<>();
		claims.put("sub", subject);
		claims.put("scope", String.join(" ", scopes));

		return new Jwt("token", now, now.plusSeconds(3600), headers, claims);
	}
}