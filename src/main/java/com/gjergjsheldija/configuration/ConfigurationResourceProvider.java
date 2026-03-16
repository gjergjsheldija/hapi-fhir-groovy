/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-06-06
 */

package com.gjergjsheldija.configuration;

import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.SearchContainedModeEnum;
import ca.uhn.fhir.rest.api.SearchTotalModeEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

public class ConfigurationResourceProvider extends BaseJpaResourceProvider<Configuration> {

	private static final Logger ourLog = LoggerFactory.getLogger(ConfigurationResourceProvider.class);

	ConfigurationResourceProvider()
	{
		ourLog.info("ConfigurationResourceProvider ctor.");
	}

	@Autowired
	RestfulServer fhirServer;

	void debugProviders(String msg)
	{
		int i = 1;
      for (IResourceProvider p : fhirServer.getResourceProviders())
      {
      	ourLog.trace("{} {} - {}", msg, i++, p.getResourceType());
			if  (p instanceof ConfigurationResourceProvider)
			{
				ourLog.debug("ConfigurationResourceProvider :");
				ourLog.debug("		fhirServer 			: {}", 	((ConfigurationResourceProvider) p).fhirServer);
				ourLog.debug("		Dao					: {}", 	((ConfigurationResourceProvider) p).getDao());
				ourLog.debug("		myStorageSettings	: {}", 	((ConfigurationResourceProvider) p).myStorageSettings);
				ourLog.debug("		getContext 			: {}", 	((ConfigurationResourceProvider) p).getContext());
			}
      }
	}

	@PostConstruct
	public void init() {

		ourLog.debug("ConfigurationResourceProvider PostConstruct :");

		fhirServer.unregisterProvider(this);

		fhirServer.registerProvider(this);

		if (ourLog.isDebugEnabled()) debugProviders("Registered Providers :");

	}


	@Override
	public Class<Configuration> getResourceType() {
		return Configuration.class;
	}

	@Search(allowUnknownParams = true)
	public IBundleProvider search(
		jakarta.servlet.http.HttpServletRequest theServletRequest,
		jakarta.servlet.http.HttpServletResponse theServletResponse,
		ca.uhn.fhir.rest.api.server.RequestDetails theRequestDetails,

		@RawParam
		Map<String, List<String>> theAdditionalRawParams,

		@Sort
		SortSpec theSort,

		@Count
		Integer theCount,

		@Offset
		Integer theOffset,

		SummaryEnum theSummaryMode,

		SearchTotalModeEnum theSearchTotalMode,

		SearchContainedModeEnum theSearchContainedMode
	) {
		startRequest(theServletRequest);
		try {
			SearchParameterMap parameterMap = new SearchParameterMap();

			parameterMap.setSort(theSort);
			parameterMap.setCount(theCount);
			parameterMap.setOffset(theOffset);
			parameterMap.setSummaryMode(theSummaryMode);
			parameterMap.setSearchTotalMode(theSearchTotalMode);
			parameterMap.setSearchContainedMode(theSearchContainedMode);

			parameterMap.setLoadSynchronous(true);

			getDao().translateRawParameters(theAdditionalRawParams, parameterMap);

			ca.uhn.fhir.rest.api.server.IBundleProvider retVal = getDao().search(parameterMap, theRequestDetails, theServletResponse);

			return retVal;
		} finally {
			endRequest(theServletRequest);
		}

	}

}