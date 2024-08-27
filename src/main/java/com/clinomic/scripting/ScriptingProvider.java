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

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import com.clinomic.configuration.Configuration;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScriptingProvider {

	private static final Logger logger = LoggerFactory.getLogger(ScriptingProvider.class);

	private enum scriptType {
		INTERCEPTOR,
		PROVIDER
	}

	@Autowired
	@Lazy
	ProviderSvcImpl providerSvc;

	@Autowired
	InterceptorSvcImpl interceptorSvc;

	@Autowired
	DaoRegistry daoRegistry;

	@PostConstruct
	public void init() {
		logger.info("Clinomic Scripting Provider Registered");
	}


	// TODO : add canonical URL
	@Operation(name = "list-scripts", idempotent = true, type = Configuration.class)
	public OperationOutcome listScripts() {
		List<Object> providerList = providerSvc.listCustomScripts();
		List<Object> interceptorList = interceptorSvc.listCustomScripts();

		OperationOutcome outcome = new OperationOutcome();

		buildOperationOutcome(outcome, providerList);
		buildOperationOutcome(outcome, interceptorList);

		return outcome;
	}

	// TODO : add canonical URL
	@Operation(name = "load-script", idempotent = false, type = Configuration.class)
	public OperationOutcome loadScript(
		@Description(shortDefinition = "The name of the Script to load")
		@OperationParam(name = "name", min = 1, max = 1, typeName = "string") StringType name,
		RequestDetails theRequestDetails
	) {

		String storedScript = "";
		try {
			storedScript = getScript(name, theRequestDetails);
		} catch (Exception e) {
			return generateErrorOutcome("Script not found : " + name);
		}

		try {
			compileAndLoadScript(name.toString(), storedScript);
		} catch (Exception e) {
			return generateErrorOutcome("Error loading script : " + e.getMessage());
		}

		return generateSuccessOutcome("Script loaded : " + name);
	}

	// TODO : add canonical URL
	@Operation(name = "unload-script", idempotent = false, type = Configuration.class)
	public OperationOutcome unloadScript(
		@Description(shortDefinition = "The name of the Script to load")
		@OperationParam(name = "name", min = 1, max = 1, typeName = "string") StringType name,
		RequestDetails theRequestDetails
	) {

		String storedScript = "";
		try {
			storedScript = getScript(name, theRequestDetails);
		} catch (Exception e) {
			return generateErrorOutcome("Script not found : " + name);
		}

		try {
			unloadAndRemoveScript(name.toString(), storedScript);
		} catch (Exception e) {
			return generateErrorOutcome("Error unloading script : " + e.getMessage());
		}

		return generateSuccessOutcome("Script unloaded : " + name);
	}

	private void unloadAndRemoveScript(String scriptName, String storedScript) {
		scriptType type = getScriptType(storedScript);

		if (type == scriptType.PROVIDER) {
			providerSvc.unloadCustomScript(scriptName);
		} else if (type == scriptType.INTERCEPTOR) {
			interceptorSvc.unloadCustomScript(scriptName);
		} else {
			throw new RuntimeException("Script is neither an Interceptor nor a Provider");
		}
	}

	private void compileAndLoadScript(String name, String storedScript) {
		scriptType type = getScriptType(storedScript);

		if (type == scriptType.PROVIDER) {
			providerSvc.loadCustomScript(name, storedScript);
		} else if (type == scriptType.INTERCEPTOR) {
			interceptorSvc.loadCustomScript(name, storedScript);
		} else {
			throw new RuntimeException("Script is neither an Interceptor nor a Provider");
		}
	}

	private scriptType getScriptType(String script) {
		if (script.contains(ProviderSvcImpl.PROVIDER)) {
			return scriptType.PROVIDER;
		} else if (script.contains(InterceptorSvcImpl.INTERCEPTOR)) {
			return scriptType.INTERCEPTOR;
		} else {
			throw new RuntimeException("Script is neither an Interceptor nor a Provider");
		}
	}

	private String getScript(StringType scriptName, RequestDetails theRequestDetails) {
		SearchParameterMap parameterMap = new SearchParameterMap();
		parameterMap.add(Configuration.SP_NAME, new StringParam(scriptName.toString()));
		parameterMap.add(Configuration.SP_TYPE, new TokenParam(Configuration.ConfigurationType.SCRIPT.getDisplay()));
		parameterMap.add(Configuration.SP_STATUS, new TokenParam(Configuration.ConfigurationStatus.ACTIVE.getDisplay()));
		parameterMap.setLoadSynchronous(true);

		List<IBaseResource> configurations = daoRegistry
			.getResourceDao(Configuration.class)
			.search(parameterMap, theRequestDetails)
			.getAllResources();

		return ((Configuration) configurations.get(0)).getBody().toString();
	}

	private OperationOutcome generateErrorOutcome(String message) {
		OperationOutcome outcome = new OperationOutcome();

		OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
		issue.setSeverity(OperationOutcome.IssueSeverity.ERROR);
		issue.setCode(OperationOutcome.IssueType.EXCEPTION);
		issue.setDiagnostics(message);

		outcome.addIssue(issue);

		return outcome;
	}

	private OperationOutcome generateSuccessOutcome(String message) {
		OperationOutcome outcome = new OperationOutcome();

		OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
		issue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
		issue.setCode(OperationOutcome.IssueType.INFORMATIONAL);
		issue.setDiagnostics(message);

		outcome.addIssue(issue);

		return outcome;
	}

	private void buildOperationOutcome(OperationOutcome outcome, List<Object> scripts) {

		for (Object script : scripts) {
			OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
			issue.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
			issue.setCode(OperationOutcome.IssueType.INFORMATIONAL);
			issue.setDiagnostics(scripts.toString());

			outcome.addIssue(issue);
		}
	}
}
