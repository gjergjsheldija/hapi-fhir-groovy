package com.gjergjsheldija.scripting;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.gjergjsheldija.configuration.Configuration;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.net.http.HttpResponse;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Security on: managing scripts (Configuration writes and the script operations) needs the admin scope. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
	"hapi.fhir.security.enabled=true",
	// as in production application.yaml
	"hapi.fhir.custom-provider-classes=com.gjergjsheldija.scripting.ScriptingProvider"})
@Import(ScriptingSecurityTest.TokenConfig.class)
class ScriptingSecurityTest extends BaseFhirR4ForTesting {

	private static final String LOAD_PARAMS = """
		{ "resourceType": "Parameters", "parameter": [ { "name": "name", "valueString": "PatientCreated" } ] }""";

	// Deliberately not @TestConfiguration: that is a @Component and would be component-scanned into other test contexts.
	static class TokenConfig {
		@Bean(name = "myAccessTokenValidation")
		AccessTokenValidation accessTokenValidation() {
			JwtDecoder decoder = token -> {
				Jwt.Builder jwt = Jwt.withTokenValue(token).header("alg", "none").subject("tester")
					.issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(3600));
				return switch (token) {
					case "admin-token" -> jwt.claim("scope", "openid app:admin").build();
					case "user-token" -> jwt.claim("scope", "openid").build();
					default -> throw new BadJwtException("bad token");
				};
			};
			return new AccessTokenValidation(decoder, jwt -> OAuth2TokenValidatorResult.success());
		}
	}

	@Autowired
	RestfulServer restfulServer;

	@Autowired
	ScriptingInterceptor scriptingInterceptor;

	@Autowired
	ScriptingSvcImpl scriptingSvc;

	@Autowired
	InterceptorSvcImpl interceptorSvc;

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
		interceptorSvc.unloadCustomScript("PatientCreated");
		cleanUp();
	}

	private int storedConfigurations() {
		return myConfigurationDao.search(SearchParameterMap.newSynchronous(), new SystemRequestDetails()).size();
	}

	private boolean exists(String id) {
		try {
			myConfigurationDao.read(new IdType("Configuration", id), new SystemRequestDetails());
			return true;
		} catch (ResourceNotFoundException | ResourceGoneException e) {
			return false;
		}
	}

	private boolean anyScriptLoaded() {
		return !scriptingSvc.listCustomScripts().isEmpty() || !interceptorSvc.listCustomScripts().isEmpty();
	}

	private void storeScriptInternally(String id) throws Exception {
		Configuration c = ourCtx.newJsonParser().parseResource(Configuration.class, ScriptJson.scriptConfiguration(id));
		myConfigurationDao.update(c, new SystemRequestDetails());
	}

	@Test
	void userTokenCannotPostScript() throws Exception {
		HttpResponse<String> response = ScriptJson.send(port, "POST", "Configuration", ScriptJson.scriptConfiguration(null), "Bearer user-token");
		assertEquals(403, response.statusCode(), response.body());
		assertTrue(response.body().contains("Writing Configuration resources requires scope app:admin"), response.body());
		assertEquals(0, storedConfigurations());
		assertFalse(ScriptJson.within(2_000, this::anyScriptLoaded), "script loaded after a forbidden write");
	}

	@Test
	void userTokenCannotPutScript() throws Exception {
		HttpResponse<String> response = ScriptJson.send(port, "PUT", "Configuration/s1", ScriptJson.scriptConfiguration("s1"), "Bearer user-token");
		assertEquals(403, response.statusCode(), response.body());
		assertFalse(exists("s1"));
		assertFalse(ScriptJson.within(2_000, this::anyScriptLoaded), "script loaded after a forbidden write");
	}

	@Test
	void noTokenCannotWrite() throws Exception {
		assertEquals(403, ScriptJson.send(port, "PUT", "Configuration/s2", ScriptJson.scriptConfiguration("s2"), null).statusCode());
		assertFalse(exists("s2"));
	}

	@Test
	void plainConfigurationEntriesAreGatedToo() throws Exception {
		Configuration c = new Configuration();
		c.setId("cfg");
		c.setName("some-setting");
		c.setStatus(Configuration.ConfigurationStatus.ACTIVE);
		c.setType(Configuration.ConfigurationType.CONFIGURATION);
		c.setBody(new StringType("value"));
		String json = ourCtx.newJsonParser().encodeResourceToString(c);
		assertEquals(403, ScriptJson.send(port, "PUT", "Configuration/cfg", json, "Bearer user-token").statusCode());
		assertEquals(201, ScriptJson.send(port, "PUT", "Configuration/cfg", json, "Bearer admin-token").statusCode());
	}

	@Test
	void adminTokenWritesAndLoadsScript() throws Exception {
		HttpResponse<String> response = ScriptJson.send(port, "PUT", "Configuration/s3", ScriptJson.scriptConfiguration("s3"), "Bearer admin-token");
		assertEquals(201, response.statusCode(), response.body());
		assertTrue(exists("s3"));
		assertTrue(ScriptJson.within(10_000, this::anyScriptLoaded), "script not loaded after an admin write");
	}

	@Test
	void scriptOperationsNeedAdminScope() throws Exception {
		storeScriptInternally("s4");
		HttpResponse<String> load = ScriptJson.send(port, "POST", "Configuration/$load-script", LOAD_PARAMS, "Bearer user-token");
		assertEquals(403, load.statusCode(), load.body());
		assertTrue(load.body().contains("$load-script requires scope app:admin"), load.body());
		assertFalse(anyScriptLoaded());
		assertEquals(403, ScriptJson.send(port, "POST", "Configuration/$unload-script", LOAD_PARAMS, "Bearer user-token").statusCode());
		assertEquals(403, ScriptJson.send(port, "GET", "Configuration/$list-scripts", null, "Bearer user-token").statusCode());
		assertEquals(403, ScriptJson.send(port, "GET", "Configuration/$list-scripts", null, null).statusCode());

		assertEquals(200, ScriptJson.send(port, "GET", "Configuration/$list-scripts", null, "Bearer admin-token").statusCode());
		// Admins pass the guard. (Whether the stored script is then found is the operation's own business, not tested here.)
		load = ScriptJson.send(port, "POST", "Configuration/$load-script", LOAD_PARAMS, "Bearer admin-token");
		assertEquals(200, load.statusCode(), load.body());
		assertFalse(load.body().contains("requires scope"), load.body());
		assertEquals(200, ScriptJson.send(port, "POST", "Configuration/$unload-script", LOAD_PARAMS, "Bearer admin-token").statusCode());
	}

	@Test
	void deleteNeedsAdminScope() throws Exception {
		storeScriptInternally("s5");
		assertEquals(403, ScriptJson.send(port, "DELETE", "Configuration/s5", null, "Bearer user-token").statusCode());
		assertTrue(exists("s5"));
		int status = ScriptJson.send(port, "DELETE", "Configuration/s5", null, "Bearer admin-token").statusCode();
		assertTrue(status == 200 || status == 204, "status " + status);
		assertFalse(exists("s5"));
	}
}
