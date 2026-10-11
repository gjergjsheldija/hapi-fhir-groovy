package com.gjergjsheldija.app;

import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.Offset;
import ca.uhn.fhir.rest.annotation.RawParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * Read/create/update/delete/patch come from {@link BaseJpaResourceProvider}; search is declared here.
 */
public class AppResourceProvider extends BaseJpaResourceProvider<App> {

	@Override
	public Class<App> getResourceType() {
		return App.class;
	}

	@Search(allowUnknownParams = true)
	public IBundleProvider search(
		HttpServletRequest theServletRequest,
		HttpServletResponse theServletResponse,
		RequestDetails theRequestDetails,
		@RawParam Map<String, List<String>> theRawParams,
		@Count Integer theCount,
		@Offset Integer theOffset) {
		startRequest(theServletRequest);
		try {
			SearchParameterMap map = new SearchParameterMap();
			map.setCount(theCount);
			map.setOffset(theOffset);
			map.setLoadSynchronous(true);
			getDao().translateRawParameters(theRawParams, map);
			return getDao().search(map, theRequestDetails, theServletResponse);
		} finally {
			endRequest(theServletRequest);
		}
	}
}
