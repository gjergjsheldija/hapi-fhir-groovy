package com.gjergjsheldija.scripting;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.gjergjsheldija.configuration.Configuration;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Security off: a script Configuration is loaded only when its write succeeded. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScriptingInterceptorTest extends BaseFhirR4ForTesting {

	@Autowired
	IInterceptorService jpaInterceptorService;

	@Autowired
	ScriptingInterceptor scriptingInterceptor;

	@Autowired
	ScriptingSvcImpl scriptingSvc;

	@BeforeEach
	void setUp() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
		// The server does not register ScriptingInterceptor (auto-load is not wired, see doc/scripting.md), so do it here.
		jpaInterceptorService.registerInterceptor(scriptingInterceptor);
	}

	@AfterEach
	void tearDown() {
		jpaInterceptorService.unregisterInterceptor(scriptingInterceptor);
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
	void matchedConditionalCreateDoesNotLoadTheRequestBody() throws Exception {
		myConfigurationDao.update(ourCtx.newJsonParser().parseResource(Configuration.class, ScriptJson.scriptConfiguration("x")),
			new SystemRequestDetails());
		String intruder = """
			package scripting

			import ca.uhn.fhir.interceptor.api.Hook
			import ca.uhn.fhir.interceptor.api.Interceptor
			import ca.uhn.fhir.interceptor.api.Pointcut
			import com.gjergjsheldija.scripting.api.CustomScript

			@Interceptor
			@CustomScript
			class Intruder {
			    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
			    void execute() {
			    }
			}""";
		// If-None-Exist matches x: nothing is stored, but the request body is a different script.
		HttpResponse<String> response = ScriptJson.send(port, "POST", "Configuration", "application/fhir+json",
			ScriptJson.scriptConfiguration(null, "Intruder", intruder), null, Map.of("If-None-Exist", "_id=x"));
		assertTrue(response.statusCode() == 200 || response.statusCode() == 201, response.statusCode() + " " + response.body());
		assertEquals(1, myConfigurationDao.search(SearchParameterMap.newSynchronous(), new SystemRequestDetails()).size());
		assertFalse(ScriptJson.within(2_000, () -> scriptingSvc.listCustomScripts().stream()
			.anyMatch(o -> o.getClass().getName().contains("Intruder"))), "script of a matched conditional create was loaded");
	}

	@Test
	void failedUpdateDoesNotLoadTheScript() throws Exception {
		// URL id differs from the body id: HAPI rejects the PUT with 400 before storing anything.
		HttpResponse<String> response = ScriptJson.send(port, "PUT", "Configuration/other", ScriptJson.scriptConfiguration("mine"), null);
		assertEquals(400, response.statusCode(), response.body());
		assertFalse(ScriptJson.within(2_000, this::scriptLoaded), "script loaded after a failed write");
	}
}
