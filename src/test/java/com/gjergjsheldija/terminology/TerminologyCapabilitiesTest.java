package com.gjergjsheldija.terminology;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.IServerAddressStrategy;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TerminologyCapabilitiesTest extends BaseFhirR4ForTesting {

	@Mock
	private RestfulServer restfulServer;

	@Mock
	private HttpServletRequest requestDetails;

	@Mock
	private IServerAddressStrategy iServerAddressStrategy;

	private TerminologyCapabilities terminologyCapabilities;

	@BeforeEach
	void beforeEach() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		String ourServerBase = "http://localhost:" + port + "/fhir/";
		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);

		when(restfulServer.getServerName()).thenReturn("name");
		when(restfulServer.getServletInfo()).thenReturn("info");
		when(restfulServer.getServerVersion()).thenReturn("0.1");

		when(restfulServer.getFhirContext()).thenReturn(ourCtx);

		when(restfulServer.getServerAddressStrategy()).thenReturn(iServerAddressStrategy);
		when(iServerAddressStrategy.determineServerBase(any(), any())).thenReturn(ourServerBase);

	}

	@Test
	public void testSetCodeSystem_OneCodeSystem() {
		// Simulate a Bundle with one CodeSystem
		CodeSystem codeSystem = new CodeSystem();
		codeSystem.setUrl("http://example.com/codesystem");
		codeSystem.setVersion("1.0");

		MethodOutcome result = ourClient.create().resource(codeSystem).execute();

		terminologyCapabilities = new TerminologyCapabilities();
		terminologyCapabilities.withDefaults(restfulServer, requestDetails);

		assertEquals(1, terminologyCapabilities.getCodeSystem().size(), "Expected one CodeSystem component");
		assertEquals("http://example.com/codesystem", terminologyCapabilities.getCodeSystem().get(0).getUri(), "Expected URI to match");
		assertEquals("1.0", terminologyCapabilities.getCodeSystem().get(0).getVersion().get(0).getCode(), "Expected version to match");
	}

	@AfterEach
	public void after() {
		cleanUp();
	}
}