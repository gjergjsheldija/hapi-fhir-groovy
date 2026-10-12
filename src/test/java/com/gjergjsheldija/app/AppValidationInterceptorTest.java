package com.gjergjsheldija.app;

import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.net.http.HttpResponse;
import java.util.stream.Stream;

import static com.gjergjsheldija.app.AppJson.app;
import static com.gjergjsheldija.app.AppJson.op;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppValidationInterceptorTest extends BaseFhirR4ForTesting {

	private static final String URL = "http://127.0.0.1:1";

	@Autowired
	AppRouteRegistry registry;

	@BeforeEach
	void setUpClient() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
	}

	@AfterEach
	void tearDown() {
		cleanUp();
		registry.refresh();
	}

	static Stream<Arguments> invalidApps() {
		return Stream.of(
			Arguments.of("bad status", app("v", "enabled", URL, null, op("o", "GET", "$x", false)), "status"),
			Arguments.of("bad endpoint type", app("v", "active", URL, null, op("o", "GET", "$x", false))
				.replace("\"http-rpc\"", "\"grpc\""), "endpoint.type"),
			Arguments.of("non-http url", app("v", "active", "ftp://host", null, op("o", "GET", "$x", false)), "endpoint.url"),
			Arguments.of("relative url", app("v", "active", "/relative", null, op("o", "GET", "$x", false)), "endpoint.url"),
			Arguments.of("bad method", app("v", "active", URL, null, op("o", "HEAD", "$x", false)), "method"),
			Arguments.of("duplicate op id", app("v", "active", URL, null,
				op("o", "GET", "$x", false) + "," + op("o", "GET", "$y", false)), "duplicate operation id"),
			Arguments.of("malformed path", app("v", "active", URL, null, op("o", "GET", "a//b", false)), "empty segment"),
			Arguments.of("duplicate param", app("v", "active", URL, null, op("o", "GET", "$x/{a}/{a}", false)), "duplicate route param"),
			Arguments.of("shadows read", app("v", "active", URL, null, op("o", "GET", "Patient/{id}", false)), "shadow"),
			Arguments.of("shadows type", app("v", "active", URL, null, op("o", "GET", "Patient", false)), "shadow"),
			Arguments.of("shadows custom type", app("v", "active", URL, null, op("o", "GET", "App/{id}", false)), "shadow"),
			Arguments.of("param first", app("v", "active", URL, null, op("o", "GET", "{anything}", false)), "shadow"),
			Arguments.of("metadata", app("v", "active", URL, null, op("o", "GET", "metadata", false)), "shadow"),
			Arguments.of("self collision", app("v", "active", URL, null,
				op("o1", "GET", "$x/{a}", false) + "," + op("o2", "GET", "$x/{b}", false)), "collides"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidApps")
	void rejectsInvalidApp(String name, String json, String expectedMessagePart) throws Exception {
		HttpResponse<String> response = AppJson.put(port, "v", json);
		assertEquals(422, response.statusCode(), response.body());
		assertTrue(response.body().contains(expectedMessagePart), response.body());
	}

	@Test
	void acceptsValidApp() throws Exception {
		HttpResponse<String> response = AppJson.put(port, "ok", app("ok", "active", URL, "s",
			op("o1", "GET", "Patient/$daily-report/{date}", false) + "," + op("o2", "POST", "$register", true)));
		assertEquals(201, response.statusCode(), response.body());
	}

	@Test
	void rejectsCollisionWithAnotherActiveApp() throws Exception {
		assertEquals(201, AppJson.put(port, "first", app("first", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		HttpResponse<String> second = AppJson.put(port, "second", app("second", "active", URL, null, op("o", "GET", "$x/{b}", false)));
		assertEquals(422, second.statusCode(), second.body());
		assertTrue(second.body().contains("first"), second.body());
	}

	@Test
	void updatingTheSameAppIsNotACollision() throws Exception {
		assertEquals(201, AppJson.put(port, "same", app("same", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		assertEquals(200, AppJson.put(port, "same", app("same", "active", "http://127.0.0.1:2", null, op("o", "GET", "$x/{a}", false))).statusCode());
	}

	@Test
	void inactiveAppDoesNotCollide() throws Exception {
		assertEquals(201, AppJson.put(port, "first", app("first", "active", URL, null, op("o", "GET", "$x", false))).statusCode());
		assertEquals(201, AppJson.put(port, "parked", app("parked", "inactive", URL, null, op("o", "GET", "$x", false))).statusCode());
	}

	@Test
	void rejectsAmbiguousCollisionWithDifferentShape() throws Exception {
		assertEquals(201, AppJson.put(port, "first", app("first", "active", URL, null, op("o", "GET", "a/{x}/c", false))).statusCode());
		HttpResponse<String> second = AppJson.put(port, "second", app("second", "active", URL, null, op("o", "GET", "a/b/{y}", false)));
		assertEquals(422, second.statusCode(), second.body());
		assertTrue(second.body().contains("collides"), second.body());
	}
}
