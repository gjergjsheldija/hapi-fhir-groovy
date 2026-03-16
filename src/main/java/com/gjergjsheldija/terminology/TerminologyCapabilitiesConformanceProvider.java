package com.gjergjsheldija.terminology;

import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;

@Component
public class TerminologyCapabilitiesConformanceProvider implements IResourceProvider {

    @Override
    public Class<TerminologyCapabilitiesConformance> getResourceType() {
        return TerminologyCapabilitiesConformance.class;
    }

    @Operation(name = "$terminology", idempotent = true)
    public TerminologyCapabilitiesConformance getTerminologyCapabilities(RequestDetails theRequestDetails) {

    	TerminologyCapabilitiesConformance terminologyCapabilities = new TerminologyCapabilitiesConformance();
       
    	return terminologyCapabilities;
    }
    
    /*
	 * private TerminologyCapabilities createTerminologyCapabilities() {
	 * TerminologyCapabilities tc = new TerminologyCapabilities();
	 * tc.setVersion(Enumerations.FHIRVersion._4_0_1.toCode());
	 * tc.getSoftware().setName("My Terminology Server"); // Additional
	 * configuration for the TerminologyCapabilities return tc; }
	 */
}