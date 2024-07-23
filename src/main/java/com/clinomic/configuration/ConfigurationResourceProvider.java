/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-06-06
 */

package com.clinomic.configuration;

import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.SearchContainedModeEnum;
import ca.uhn.fhir.rest.api.SearchTotalModeEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConfigurationResourceProvider extends BaseJpaResourceProvider<Configuration> {

	private static final Logger ourLog = LoggerFactory.getLogger(ConfigurationResourceProvider.class);

	@Autowired
	ConfigurationBean configurationBean;

	@Autowired
	RestfulServer fhirServer;

	@PostConstruct
	public void init() {
		fhirServer.unregisterProvider(getResourceType());
		fhirServer.registerProvider(configurationBean.resourceProviderConfiguration());

		ourLog.info("Clinomic Configuration Resource Provider Registered");
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