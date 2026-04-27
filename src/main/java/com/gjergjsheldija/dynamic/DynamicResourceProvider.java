package com.gjergjsheldija.dynamic;

import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.SearchContainedModeEnum;
import ca.uhn.fhir.rest.api.SearchTotalModeEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;
import java.util.Map;

public class DynamicResourceProvider extends BaseJpaResourceProvider<IBaseResource> {

    private final Class<? extends IBaseResource> myResourceType;

    public DynamicResourceProvider(Class<? extends IBaseResource> theResourceType) {
        myResourceType = theResourceType;
    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return myResourceType;
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

            return getDao().search(parameterMap, theRequestDetails, theServletResponse);
        } finally {
            endRequest(theServletRequest);
        }
    }
}
