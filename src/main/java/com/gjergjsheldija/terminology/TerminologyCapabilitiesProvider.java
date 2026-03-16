/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-26
 */

package com.gjergjsheldija.terminology;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.RestfulServerConfiguration;
import ca.uhn.fhir.rest.server.provider.ServerCapabilityStatementProvider;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminologyCapabilitiesProvider extends ServerCapabilityStatementProvider {

	private static final Logger ourLog = LoggerFactory.getLogger(TerminologyCapabilitiesProvider.class);

	public static final String TERMINOLOGY = "terminology";

	public TerminologyCapabilitiesProvider(RestfulServer theServer) {
		super(theServer);
	}

	public TerminologyCapabilitiesProvider(FhirContext theContext, RestfulServerConfiguration theServerConfiguration) {
		super(theContext, theServerConfiguration);
	}

	public TerminologyCapabilitiesProvider(RestfulServer theRestfulServer, ISearchParamRegistry theSearchParamRegistry, IValidationSupport theValidationSupport) {
		super(theRestfulServer, theSearchParamRegistry, theValidationSupport);
	}

	@Metadata(cacheMillis = 0)
	public IBaseConformance getMetadataResource(HttpServletRequest request, RequestDetails requestDetails) {

		ourLog.info("Calling the server metadata");

		if (request.getParameter("mode") != null && request.getParameter("mode").equals(TERMINOLOGY)) {
			return new TerminologyCapabilities().withDefaults(requestDetails.getServer(), request);
		} else {
			return super.getServerConformance(request, requestDetails);
		}
	}
}
