/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-26
 */

package com.gjergjsheldija.terminology;

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
