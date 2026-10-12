/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-07
 */

package com.gjergjsheldija.scripting;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.gjergjsheldija.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Interceptor
public class ScriptingInterceptor {
	private static final Logger ourLog = LoggerFactory.getLogger(ScriptingInterceptor.class);
	private final ScriptingSvcImpl scriptingSvcImpl;

	public ScriptingInterceptor(ScriptingSvcImpl scriptingSvcImpl) {
		this.scriptingSvcImpl = scriptingSvcImpl;
		ourLog.info("Initializing Scripting Interceptor");
	}

	/**
	 * Only after a successful create/update: the resource here is the request body, so a rejected write must not load it.
	 * PATCH bodies are not Configurations; patched scripts are (re)loaded via $load-script.
	 */
	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
	public void scriptUpdated(RequestDetails requestDetails) {
		RestOperationTypeEnum operation = requestDetails.getRestOperationType();
		if ((operation == RestOperationTypeEnum.CREATE || operation == RestOperationTypeEnum.UPDATE)
				&& requestDetails.getResource() instanceof Configuration configuration) {
			processResource(configuration);
		}
	}

	private void processResource(Configuration clioConfiguration) {

		if (clioConfiguration.getStatus() == Configuration.ConfigurationStatus.ACTIVE
				&& clioConfiguration.getType() == Configuration.ConfigurationType.SCRIPT) {
			scriptingSvcImpl.loadCustomScript(clioConfiguration.getName(), clioConfiguration.getBody().getValue());
		} else if (clioConfiguration.getStatus() == Configuration.ConfigurationStatus.INACTIVE
				&& clioConfiguration.getType() == Configuration.ConfigurationType.SCRIPT) {
			scriptingSvcImpl.unloadCustomScript(clioConfiguration.getName());
		}
	}
}
