package com.gjergjsheldija.scripting;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Security off: a script Configuration is loaded only when its write succeeded. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScriptingInterceptorTest extends BaseFhirR4ForTesting {

	@Autowired
	RestfulServer restfulServer;

	@Autowired
	ScriptingInterceptor scriptingInterceptor;

	@Autowired
	ScriptingSvcImpl scriptingSvc;

	@BeforeEach
	void setUp() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
		// The server does not register ScriptingInterceptor itself (not in custom-interceptor-classes), so do it here.
		restfulServer.registerInterceptor(scriptingInterceptor);
	}

	@AfterEach
	void tearDown() {
		restfulServer.unregisterInterceptor(scriptingInterceptor);
		scriptingSvc.unloadInterceptors();
		cleanUp();
	}

	private boolean scriptLoaded() {
		return !scriptingSvc.listCustomScripts().isEmpty();
	}

	@Test
	void successfulCreateLoadsTheScript() throws Exception {
		HttpResponse<String> response = ScriptJson.send(port, "POST", "Configuration", ScriptJson.scriptConfiguration(null), null);
		assertEquals(201, response.statusCode(), response.body());
		assertTrue(ScriptJson.within(10_000, this::scriptLoaded), "script not loaded after a successful create");
	}

	@Test
	void failedUpdateDoesNotLoadTheScript() throws Exception {
		// URL id differs from the body id: HAPI rejects the PUT with 400 before storing anything.
		HttpResponse<String> response = ScriptJson.send(port, "PUT", "Configuration/other", ScriptJson.scriptConfiguration("mine"), null);
		assertEquals(400, response.statusCode(), response.body());
		assertFalse(ScriptJson.within(2_000, this::scriptLoaded), "script loaded after a failed write");
	}
}
