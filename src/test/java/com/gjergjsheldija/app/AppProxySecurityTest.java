package com.gjergjsheldija.app;

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
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.time.Instant;

import static com.gjergjsheldija.app.AppJson.app;
import static com.gjergjsheldija.app.AppJson.op;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "hapi.fhir.security.enabled=true")
@Import(AppProxySecurityTest.TokenConfig.class)
class AppProxySecurityTest extends BaseFhirR4ForTesting {

	// Deliberately not @TestConfiguration: that is a @Component and would be component-scanned into other test contexts.
	static class TokenConfig {
		@Bean(name = "myAccessTokenValidation")
		AccessTokenValidation accessTokenValidation() {
			JwtDecoder decoder = token -> {
				if (!"good-token".equals(token) && !"admin-token".equals(token)) {
					throw new BadJwtException("bad token");
				}
				return Jwt.withTokenValue(token).header("alg", "none").subject("tester").claim("scope", "app:admin")
					.issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(3600)).build();
			};
			return new AccessTokenValidation(decoder, jwt -> OAuth2TokenValidatorResult.success());
		}
	}

	private StubApp stub;

	@Autowired
	AppRouteRegistry registry;

	@BeforeEach
	void setUp() throws Exception {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
		stub = new StubApp();
		AppJson.put(port, "sec", app("sec", "active", stub.url(), null,
			op("private", "GET", "$private", false) + "," + op("open", "GET", "$open", true)), "Bearer admin-token");
	}

	@AfterEach
	void tearDown() {
		stub.close();
		cleanUp();
		registry.refresh();
	}

	@Test
	void nonPublicOpWithoutTokenIs401() throws Exception {
		assertEquals(401, AppJson.send(port, "GET", "$private", null, null, null).statusCode());
		assertEquals(0, stub.calls.get());
	}

	@Test
	void nonPublicOpWithBadTokenIs401() throws Exception {
		assertEquals(401, AppJson.send(port, "GET", "$private", null, null, "Bearer nope").statusCode());
	}

	@Test
	void nonPublicOpWithGoodTokenPasses() throws Exception {
		assertEquals(200, AppJson.send(port, "GET", "$private", null, null, "Bearer good-token").statusCode());
	}

	@Test
	void publicOpNeedsNoToken() throws Exception {
		assertEquals(200, AppJson.send(port, "GET", "$open", null, null, null).statusCode());
	}

	private void assertRejected(String authorization) throws Exception {
		var response = AppJson.send(port, "GET", "$private", null, null, authorization);
		assertEquals(401, response.statusCode(), response.body());
		assertTrue(response.body().contains("OperationOutcome"), response.body());
		assertEquals(0, stub.calls.get());
	}

	@Test
	void emptyBearerTokenIs401() throws Exception {
		assertRejected("Bearer ");
	}

	@Test
	void basicSchemeIs401() throws Exception {
		assertRejected("Basic dXNlcjpwYXNz");
	}

	@Test
	void lowercaseBearerSchemeIs401() throws Exception {
		assertRejected("bearer good-token");
	}

	@Test
	void noAuthorizationHeaderBodyIsOperationOutcome() throws Exception {
		assertRejected(null);
	}

	@Test
	void duplicateAuthorizationHeadersAre401() throws Exception {
		var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/fhir/$private"))
			.header("Authorization", "Bearer good-token").header("Authorization", "Bearer good-token").GET().build();
		var response = AppJson.CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
		assertEquals(401, response.statusCode(), response.body());
		assertEquals(0, stub.calls.get());
	}
}
