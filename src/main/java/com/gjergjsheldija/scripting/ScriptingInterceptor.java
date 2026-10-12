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
import com.gjergjsheldija.configuration.Configuration;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Loads a script Configuration when it is stored. Not registered by the server (scripts load via $load-script,
 * see doc/scripting.md); if wired, register it on the JPA interceptor service.
 */
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
	 * PRECOMMIT fires only for real writes (not a failed request or a matched conditional create), after the
	 * PRESTORAGE checks, with the resource actually stored.
	 */
	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
	public void onCreated(IBaseResource theResource) {
		afterCommit(theResource);
	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED)
	public void onUpdated(IBaseResource theOldResource, IBaseResource theNewResource) {
		afterCommit(theNewResource);
	}

	/** Load only once the write is committed, so a rolled-back write loads nothing. */
	private void afterCommit(IBaseResource theResource) {
		if (!(theResource instanceof Configuration configuration)) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					processResource(configuration);
				}
			});
		} else {
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
