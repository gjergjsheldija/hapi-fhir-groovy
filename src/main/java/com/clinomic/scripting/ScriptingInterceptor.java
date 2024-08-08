/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-07
 */

package com.clinomic.scripting;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.IPreResourceAccessDetails;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.clinomic.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Interceptor
public class ScriptingInterceptor {
	private static final Logger ourLog = LoggerFactory.getLogger(ScriptingInterceptor.class);
	@Autowired
	IScriptingSvc scriptingSvcImpl;

	public ScriptingInterceptor() {
		ourLog.info("Initializing Scripting Interceptor");
	}

	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED)
	public void scriptUpdated(IPreResourceAccessDetails accessDetails, RequestDetails requestDetails, ServletRequestDetails servletRequestDetails) {

		if (requestDetails.getResource() != null
			&& requestDetails.getResource().getClass() == Configuration.class) {
			processResource((Configuration) requestDetails.getResource());
		}
	}

	private void processResource(Configuration clioConfiguration) {

		if (clioConfiguration.getStatus() == Configuration.ConfigurationStatus.ACTIVE
			&& clioConfiguration.getType() == Configuration.ConfigurationType.SCRIPT) {
			scriptingSvcImpl.loadInterceptor(clioConfiguration.getName(), clioConfiguration.getBody().getValue());
		} else if (clioConfiguration.getStatus() == Configuration.ConfigurationStatus.INACTIVE
			&& clioConfiguration.getType() == Configuration.ConfigurationType.SCRIPT
		) {
			scriptingSvcImpl.unloadInterceptor(clioConfiguration.getName());
		}
	}
}
