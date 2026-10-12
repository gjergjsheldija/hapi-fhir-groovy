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
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import org.hl7.fhir.r4.model.IdType;

import java.net.http.HttpResponse;
import java.util.List;
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

	@Autowired
	IFhirResourceDao<App> appDao;

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
			Arguments.of("bad status", app("v", "enabled", URL, null, op("o", "GET", "$x", false)), "status must be one of"),
			Arguments.of("bad endpoint type", app("v", "active", URL, null, op("o", "GET", "$x", false))
				.replace("\"http-rpc\"", "\"grpc\""), "endpoint.type must be http-rpc"),
			Arguments.of("non-http url", app("v", "active", "ftp://host", null, op("o", "GET", "$x", false)), "endpoint.url must be an absolute http(s) URL"),
			Arguments.of("relative url", app("v", "active", "/relative", null, op("o", "GET", "$x", false)), "endpoint.url must be an absolute http(s) URL"),
			Arguments.of("bad method", app("v", "active", URL, null, op("o", "HEAD", "$x", false)), "method must be one of"),
			Arguments.of("duplicate op id", app("v", "active", URL, null,
				op("o", "GET", "$x", false) + "," + op("o", "GET", "$y", false)), "duplicate operation id"),
			Arguments.of("malformed path", app("v", "active", URL, null, op("o", "GET", "a//b", false)), "empty segment"),
			Arguments.of("duplicate param", app("v", "active", URL, null, op("o", "GET", "$x/{a}/{a}", false)), "duplicate route param"),
			Arguments.of("shadows read", app("v", "active", URL, null, op("o", "GET", "Patient/{id}", false)), "shadow"),
			Arguments.of("shadows type", app("v", "active", URL, null, op("o", "GET", "Patient", false)), "shadow"),
			Arguments.of("shadows custom type", app("v", "active", URL, null, op("o", "GET", "App/{id}", false)), "shadow"),
			Arguments.of("param first", app("v", "active", URL, null, op("o", "GET", "{anything}", false)), "shadow"),
			Arguments.of("metadata", app("v", "active", URL, null, op("o", "GET", "metadata", false)), "shadow"),
			Arguments.of("no status", app("v", "active", URL, null, op("o", "GET", "$x", false))
					.replace("\"status\": \"active\",", ""), "status must be one of"),
			Arguments.of("no method", app("v", "active", URL, null, op("o", "GET", "$x", false))
					.replace("\"method\": \"GET\",", ""), "method must be one of"),
			Arguments.of("no operations", app("v", "active", URL, null, ""), "at least one operation"),
			Arguments.of("expunge", app("v", "active", URL, null, op("o", "GET", "$expunge", false)), "shadow server operation"),
			Arguments.of("validate on type", app("v", "active", URL, null, op("o", "GET", "Patient/$validate", false)), "shadow server operation"),
			Arguments.of("everything", app("v", "active", URL, null, op("o", "GET", "Patient/$everything", false)), "shadow server operation"),
			Arguments.of("export", app("v", "active", URL, null, op("o", "GET", "$export", false)), "shadow server operation"),
			Arguments.of("mdm-submit", app("v", "active", URL, null, op("o", "GET", "Patient/$mdm-submit", false)), "shadow server operation"),
			Arguments.of("well-known", app("v", "active", URL, null, op("o", "GET", ".well-known/smart-configuration", false)), "shadow"),
			Arguments.of("swagger", app("v", "active", URL, null, op("o", "GET", "swagger-ui", false)), "shadow"),
			Arguments.of("configuration read", app("v", "active", URL, null, op("o", "GET", "Configuration/{id}", false)), "shadow"),
			Arguments.of("instance path", app("v", "active", URL, null, op("o", "GET", "Patient/{id}/$x", false)), "shadow"),
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
	void createWithServerAssignedIdIsCheckedAgainstOtherApps() throws Exception {
		assertEquals(201, AppJson.put(port, "first", app("first", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		HttpResponse<String> ok = AppJson.send(port, "POST", "App", "application/fhir+json",
			app("new1", "active", URL, null, op("o", "GET", "$y", false)).replace("\"id\": \"new1\",", ""), null);
		assertEquals(201, ok.statusCode(), ok.body());
		HttpResponse<String> clash = AppJson.send(port, "POST", "App", "application/fhir+json",
			app("new2", "active", URL, null, op("o", "GET", "$x/{b}", false)).replace("\"id\": \"new2\",", ""), null);
		assertEquals(422, clash.statusCode(), clash.body());
		assertTrue(clash.body().contains("collides"), clash.body());
	}

	@Test
	void updatingTheSameAppIsNotACollision() throws Exception {
		assertEquals(201, AppJson.put(port, "same", app("same", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		assertEquals(200, AppJson.put(port, "same", app("same", "active", "http://127.0.0.1:2", null, op("o", "GET", "$x/{a}", false))).statusCode());
	}

	@Test
	void deletedAppViaRestDoesNotCollide() throws Exception {
		assertEquals(201, AppJson.put(port, "gone", app("gone", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		int deleteStatus = AppJson.send(port, "DELETE", "App/gone", null, null, null).statusCode();
		assertTrue(deleteStatus == 200 || deleteStatus == 204, "delete returned " + deleteStatus);
		HttpResponse<String> next = AppJson.put(port, "next", app("next", "active", URL, null, op("o", "GET", "$x/{a}", false)));
		assertEquals(201, next.statusCode(), next.body());
	}

	@Test
	void staleRegistryDoesNotCauseCollision() throws Exception {
		assertEquals(201, AppJson.put(port, "gone", app("gone", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		registry.refresh();
		// A DAO delete fires no REST hook, so the registry keeps routing the deleted App until the next refresh.
		appDao.delete(new IdType("App/gone"), new SystemRequestDetails());
		assertTrue(registry.find("GET", List.of("$x", "1")).isPresent(), "precondition: registry is stale");
		HttpResponse<String> next = AppJson.put(port, "next", app("next", "active", URL, null, op("o", "GET", "$x/{a}", false)));
		assertEquals(201, next.statusCode(), next.body());
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

	@Test
	void rejectsCollidingAppsInOneTransactionBundle() throws Exception {
		String a = app("ba", "active", URL, null, op("o", "GET", "$x/{a}", false));
		String b = app("bb", "active", URL, null, op("o", "GET", "$x/{b}", false));
		String bundle = """
			{ "resourceType": "Bundle", "type": "transaction", "entry": [
			  { "resource": %s, "request": { "method": "PUT", "url": "App/ba" } },
			  { "resource": %s, "request": { "method": "PUT", "url": "App/bb" } } ] }""".formatted(a, b);
		HttpResponse<String> response = AppJson.send(port, "POST", "", "application/fhir+json", bundle, null);
		assertEquals(422, response.statusCode(), response.body());
		assertTrue(response.body().contains("collides"), response.body());
		assertEquals(404, AppJson.send(port, "GET", "App/ba", null, null, null).statusCode());
		assertEquals(404, AppJson.send(port, "GET", "App/bb", null, null, null).statusCode());
	}
}
