/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-09-17
 */

package com.gjergjsheldija.validation;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.gjergjsheldija.configuration.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
