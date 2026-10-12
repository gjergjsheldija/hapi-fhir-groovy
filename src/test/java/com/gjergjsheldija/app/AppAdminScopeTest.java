package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.gjergjsheldija.security.AdminScopeGuard;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.gjergjsheldija.app.AppJson.app;
import static com.gjergjsheldija.app.AppJson.op;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "hapi.fhir.security.enabled=true")
@Import(AppAdminScopeTest.TokenConfig.class)
class AppAdminScopeTest extends BaseFhirR4ForTesting {

	// Deliberately not @TestConfiguration: that is a @Component and would be component-scanned into other test contexts.
	static class TokenConfig {
		@Bean(name = "myAccessTokenValidation")
		AccessTokenValidation accessTokenValidation() {
			JwtDecoder decoder = token -> {
				Jwt.Builder jwt = Jwt.withTokenValue(token).header("alg", "none").subject("tester")
					.issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(3600));
				return switch (token) {
					case "admin-token" -> jwt.claim("scope", "openid app:admin").build();
					case "kc-admin-token" -> jwt.claim("realm_access", Map.of("roles", List.of("app:admin"))).build();
					case "near-token" -> jwt.claim("scope", "app:admin-readonly app:administrator").build();
					case "user-token" -> jwt.claim("scope", "openid").build();
					default -> throw new BadJwtException("bad token");
				};
			};
			return new AccessTokenValidation(decoder, jwt -> OAuth2TokenValidatorResult.success());
		}
	}

	@Autowired
	IFhirResourceDao<App> appDao;

	@Autowired
	AppRouteRegistry registry;

	private static String body(String id) {
		return app(id, "active", "http://localhost:1", null, op("o", "GET", "$scoped", false));
	}

	private boolean exists(String id) {
		try {
			appDao.read(new org.hl7.fhir.r4.model.IdType("App", id), new SystemRequestDetails());
			return true;
		} catch (ResourceNotFoundException e) {
			return false;
		}
	}

	@BeforeEach
	void setUp() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
	}

	@AfterEach
	void tearDown() {
		cleanUp();
		registry.refresh();
	}

	@Test
	void userTokenCannotWrite() throws Exception {
		assertEquals(403, AppJson.put(port, "s1", body("s1"), "Bearer user-token").statusCode());
		assertTrue(!exists("s1"));
	}

	@Test
	void noTokenCannotWrite() throws Exception {
		assertEquals(403, AppJson.put(port, "s2", body("s2")).statusCode());
	}

	@Test
	void adminScopeCanWrite() throws Exception {
		assertEquals(201, AppJson.put(port, "s3", body("s3"), "Bearer admin-token").statusCode());
	}

	@Test
	void keycloakRealmRoleCanWrite() throws Exception {
		assertEquals(201, AppJson.put(port, "s4", body("s4"), "Bearer kc-admin-token").statusCode());
	}

	@Test
	void deleteNeedsAdminScope() throws Exception {
		assertEquals(201, AppJson.put(port, "s5", body("s5"), "Bearer admin-token").statusCode());
		assertEquals(403, AppJson.send(port, "DELETE", "App/s5", null, null, "Bearer user-token").statusCode());
		assertTrue(exists("s5"));
		int status = AppJson.send(port, "DELETE", "App/s5", null, null, "Bearer admin-token").statusCode();
		assertTrue(status == 200 || status == 204, "status " + status);
	}

	@Test
	void transactionWithAppEntryNeedsAdminScope() throws Exception {
		String bundle = """
			{ "resourceType": "Bundle", "type": "transaction",
			  "entry": [ { "resource": %s, "request": { "method": "PUT", "url": "App/s6" } } ] }""".formatted(body("s6"));
		assertEquals(403, AppJson.send(port, "POST", "", "application/fhir+json", bundle, "Bearer user-token").statusCode());
		assertTrue(!exists("s6"));
	}

	@Test
	void internalWritesAreNotChecked() {
		App app = ourCtx.newJsonParser().parseResource(App.class, body("s7"));
		appDao.update(app, new SystemRequestDetails());
		assertTrue(exists("s7"));
	}

	@Test
	void adminTokenTransactionWithAppSucceeds() throws Exception {
		String bundle = """
			{ "resourceType": "Bundle", "type": "transaction",
			  "entry": [ { "resource": %s, "request": { "method": "PUT", "url": "App/s8" } } ] }""".formatted(body("s8"));
		var response = AppJson.send(port, "POST", "", "application/fhir+json", bundle, "Bearer admin-token");
		assertEquals(200, response.statusCode(), response.body());
		assertTrue(exists("s8"));
	}

	@Test
	void nearMissScopeIsRejected() throws Exception {
		assertEquals(403, AppJson.put(port, "s9", body("s9"), "Bearer near-token").statusCode());
		assertTrue(!exists("s9"));
	}

	@Test
	void conditionalDeleteNeedsAdminScope() throws Exception {
		assertEquals(201, AppJson.put(port, "c1", app("c1", "active", "http://localhost:1", null, op("o", "GET", "$c1", false)), "Bearer admin-token").statusCode());
		assertEquals(201, AppJson.put(port, "c2", app("c2", "active", "http://localhost:1", null, op("o", "GET", "$c2", false)), "Bearer admin-token").statusCode());
		assertEquals(403, AppJson.send(port, "DELETE", "App?_id=c1", null, null, "Bearer user-token").statusCode());
		assertTrue(exists("c1"));
		assertTrue(exists("c2"));
	}

	@Test
	void blankAdminScopeFailsAtStartup() {
		var e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> new AdminScopeGuard(true, null, " "));
		assertTrue(e.getMessage().contains("hapi.fhir.apps.admin-scope"));
	}
}
