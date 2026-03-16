/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-13
 */

package com.gjergjsheldija.terminology;

import java.util.Set;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.NamingSystem;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;

@Interceptor
public class SupportedResourceTypeInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(SupportedResourceTypeInterceptor.class);

	private static final Set<String> ALLOWED_RESOURCES = Set.of(
			CodeSystem.class.getSimpleName(),
			ConceptMap.class.getSimpleName(), 
			TerminologyCapabilities.class.getSimpleName(),
			NamingSystem.class.getSimpleName(), 
			ValueSet.class.getSimpleName());

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
	public void restrictResource(RequestDetails theRequestDetails) {
		String resource = theRequestDetails.getResourceName();

		if (resource != null && !ALLOWED_RESOURCES.contains(resource)) {
			logger.warn("Resource {} is not allowed", resource);
			throw new ForbiddenOperationException("Resource " + resource + " is not allowed on this server.");
		}
	}
}
