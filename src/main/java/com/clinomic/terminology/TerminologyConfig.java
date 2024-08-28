/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-26
 */

package com.clinomic.terminology;

import ca.uhn.fhir.rest.server.RestfulServer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TerminologyConfig {

	@Autowired
	RestfulServer restfulServer;

	@PostConstruct
	public void init() {
		restfulServer.setServerConformanceProvider(new TerminologyCapabilitiesProvider(restfulServer));
	}

}
