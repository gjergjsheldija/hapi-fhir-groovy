/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-09-16
 */

package com.gjergjsheldija.validation;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.interceptor.RequestValidatingInterceptor;
import com.gjergjsheldija.app.App;
import com.gjergjsheldija.configuration.Configuration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomRequestValidatingInterceptor extends RequestValidatingInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(CustomRequestValidatingInterceptor.class);

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
	@Override
	public boolean incomingRequestPostProcessed(RequestDetails theRequestDetails, HttpServletRequest theRequest,
															  HttpServletResponse theResponse) throws AuthenticationException {

		String resourceName = theRequestDetails.getResourceName();
		if (Configuration.class.getSimpleName().equals(resourceName) || App.class.getSimpleName().equals(resourceName)) {
			logger.trace("Skipping validation for custom resource {}", resourceName);
			return true;
		}

		return super.incomingRequestPostProcessed(theRequestDetails, theRequest, theResponse);
	}
}
