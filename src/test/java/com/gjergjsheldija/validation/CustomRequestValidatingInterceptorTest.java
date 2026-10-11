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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.validation.IValidationContext;
import ca.uhn.fhir.validation.IValidatorModule;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import org.hl7.fhir.instance.model.api.IBaseResource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.gjergjsheldija.configuration.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

	private void stubFhirRequest(String resourceName) {
		RestfulServer server = mock(RestfulServer.class);
		when(server.getFhirContext()).thenReturn(FhirContext.forR4());
		when(requestDetails.getServer()).thenReturn(server);
		when(requestDetails.getHeaders("Content-Type")).thenReturn(List.of("application/fhir+json"));
		when(requestDetails.loadRequestContents()).thenReturn("{\"resourceType\":\"Basic\"}".getBytes(StandardCharsets.UTF_8));
		when(requestDetails.getResourceName()).thenReturn(resourceName);
		interceptor.setFailOnSeverity(ResultSeverityEnum.ERROR);
		interceptor.addValidatorModule(new IValidatorModule() {
			@Override
			public void validateResource(IValidationContext<IBaseResource> theCtx) {
				SingleValidationMessage message = new SingleValidationMessage();
				message.setSeverity(ResultSeverityEnum.ERROR);
				message.setMessage("always fails");
				theCtx.addValidationMessage(message);
			}
		});
	}

	@Test
	void validatesNonCustomResource() {
		stubFhirRequest("Patient");

		assertThrows(UnprocessableEntityException.class,
			() -> interceptor.incomingRequestPostProcessed(requestDetails, httpServletRequest, httpServletResponse));
	}

	@Test
	void skipsValidationForApp() throws AuthenticationException {
		stubFhirRequest("App");

		assertTrue(interceptor.incomingRequestPostProcessed(requestDetails, httpServletRequest, httpServletResponse));
	}
}
