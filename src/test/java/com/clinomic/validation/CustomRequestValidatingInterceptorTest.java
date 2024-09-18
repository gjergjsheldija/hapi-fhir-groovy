/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Andrey Zagariya <azagariya@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-09-17
 */

package com.clinomic.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import com.clinomic.configuration.Configuration;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class CustomRequestValidatingInterceptorTest {

	private CustomRequestValidatingInterceptor interceptor;

	@Mock
	private RequestDetails requestDetails;

	@Mock
	private HttpServletRequest httpServletRequest;

	@Mock
	private HttpServletResponse httpServletResponse;

	@Mock
	private Logger spyLogger;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		interceptor = new CustomRequestValidatingInterceptor();
	}

	@Test
	void testSkipValidationForConfigurationResource() throws AuthenticationException {
		when(requestDetails.getResourceName()).thenReturn(Configuration.class.getSimpleName());

		boolean result = interceptor.incomingRequestPostProcessed(requestDetails, httpServletRequest,
				httpServletResponse);

		assertTrue(result, "Validation should be skipped for Configuration resource");
	}
}
