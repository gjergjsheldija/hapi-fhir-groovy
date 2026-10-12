package com.gjergjsheldija.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.gjergjsheldija.app.AppJson.app;
import static com.gjergjsheldija.app.AppJson.op;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppProxyFilterTest extends BaseFhirR4ForTesting {

	private final ObjectMapper mapper = new ObjectMapper();
	private StubApp stub;

	@Autowired
	AppRouteRegistry registry;

	@BeforeEach
	void setUp() throws Exception {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
		stub = new StubApp();
	}

	@AfterEach
	void tearDown() {
		stub.close();
		cleanUp();
		registry.refresh();
	}

	private void register(String status, String url) throws Exception {
		HttpResponse<String> put = AppJson.put(port, "myorg.myapp", app("myorg.myapp", status, url, "s3cr3t",
			op("daily-patient-report", "GET", "Patient/$daily-report/{date}", false) + ","
				+ op("register-user", "POST", "User/$register", true)));
		assertTrue(put.statusCode() == 200 || put.statusCode() == 201, put.body());
	}

	@Test
	void appIsCallableImmediatelyAfterRegistration() throws Exception {
		for (int i = 0; i < 10; i++) {
			String id = "fresh.app" + i;
			assertEquals(201, AppJson.put(port, id, app(id, "active", stub.url(), null, op("op", "GET", "$fresh" + i, true))).statusCode());
			HttpResponse<String> call = AppJson.send(port, "GET", "$fresh" + i, null, null, null);
			assertEquals(200, call.statusCode(), "iteration " + i + ": " + call.body());
		}
		assertEquals(10, stub.calls.get());
	}

	@Test
	void appIsGoneImmediatelyAfterDelete() throws Exception {
		for (int i = 0; i < 10; i++) {
			String id = "doomed.app" + i;
			assertEquals(201, AppJson.put(port, id, app(id, "active", stub.url(), null, op("op", "GET", "$doomed" + i, true))).statusCode());
			int deleteStatus = AppJson.send(port, "DELETE", "App/" + id, null, null, null).statusCode();
			assertTrue(deleteStatus == 200 || deleteStatus == 204, "delete returned " + deleteStatus);
			HttpResponse<String> call = AppJson.send(port, "GET", "$doomed" + i, null, null, null);
			assertNotEquals(200, call.statusCode(), "iteration " + i + ": " + call.body());
		}
		assertEquals(0, stub.calls.get());
	}

	@Test
	void appFromTransactionBundleIsCallableImmediately() throws Exception {
		String bundle = """
			{ "resourceType": "Bundle", "type": "transaction", "entry": [
			  { "resource": %s, "request": { "method": "POST", "url": "App" } } ] }""".formatted(
			app("ignored", "active", stub.url(), null, op("op", "GET", "$from-bundle", true)).replace("\"id\": \"ignored\",", ""));
		HttpResponse<String> tx = AppJson.send(port, "POST", "", "application/fhir+json", bundle, null);
		assertEquals(200, tx.statusCode(), tx.body());
		HttpResponse<String> call = AppJson.send(port, "GET", "$from-bundle", null, null, null);
		assertEquals(200, call.statusCode(), call.body());
		assertEquals(1, stub.calls.get());
	}

	@Test
	void forwardsEnvelopeAndReturnsResponseVerbatim() throws Exception {
		register("active", stub.url());
		stub.status = 418;
		stub.contentType = "text/plain";
		stub.body = "teapot";

		HttpResponse<String> response = AppJson.send(port, "GET", "Patient/$daily-report/2024-01-01?code=a&code=b",
			null, null, "Bearer user-token");

		assertEquals(418, response.statusCode());
		assertEquals("teapot", response.body());
		assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));

		String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("myorg.myapp:s3cr3t".getBytes(StandardCharsets.UTF_8));
		assertEquals(expectedAuth, stub.lastHeaders.getFirst("Authorization"));
		JsonNode env = mapper.readTree(stub.lastBody);
		assertEquals("2024-01-01", env.get("request").get("route-params").get("date").asText());
		assertEquals(2, env.get("request").get("params").get("code").size());
		assertEquals("Bearer user-token", env.get("request").get("headers").get("authorization").asText());
		assertEquals("daily-patient-report", env.get("operation").get("id").asText());
		assertEquals("myorg.myapp", env.get("operation").get("app").get("id").asText());
		assertTrue(env.get("box").get("base-url").asText().endsWith("/fhir"), env.toString());
	}

	@Test
	void formEncodedBodyIsForwardedIntact() throws Exception {
		register("active", stub.url());
		AppJson.send(port, "POST", "User/$register?x=1", "application/x-www-form-urlencoded", "name=alice&role=admin", null);
		JsonNode env = mapper.readTree(stub.lastBody);
		assertEquals("name=alice&role=admin", env.get("request").get("resource").asText());
		assertEquals("1", env.get("request").get("params").get("x").get(0).asText());
	}

	@Test
	void formEncodedPutBodyIsForwardedIntact() throws Exception {
		HttpResponse<String> put = AppJson.put(port, "myorg.putapp", app("myorg.putapp", "active", stub.url(), null,
			op("update-profile", "PUT", "$profile", false)));
		assertEquals(201, put.statusCode(), put.body());
		// Spring's FormContentFilter would otherwise consume PUT/PATCH/DELETE form bodies first.
		AppJson.send(port, "PUT", "$profile", "application/x-www-form-urlencoded", "a=1&b=2", null);
		JsonNode env = mapper.readTree(stub.lastBody);
		assertEquals("a=1&b=2", env.get("request").get("resource").asText());
	}

	@Test
	void appCallsUseHttp11WithoutUpgrade() throws Exception {
		register("active", stub.url());
		AppJson.send(port, "GET", "Patient/$daily-report/2024-01-01", null, null, null);
		assertEquals(1, stub.calls.get());
		assertNull(stub.lastHeaders.getFirst("Upgrade"), String.valueOf(stub.lastHeaders.entrySet()));
	}

	@Test
	void unreachableAppGives502() throws Exception {
		String url = stub.url();
		stub.close();
		register("active", url);
		HttpResponse<String> response = AppJson.send(port, "POST", "User/$register", null, null, null);
		assertEquals(502, response.statusCode(), response.body());
		assertTrue(response.body().contains("OperationOutcome"), response.body());
	}

	@Test
	void slowAppGives504() throws Exception {
		register("active", stub.url());
		stub.delayMs = 4000; // test base sets request-timeout-ms=2000
		HttpResponse<String> response = AppJson.send(port, "POST", "User/$register", null, null, null);
		assertEquals(504, response.statusCode(), response.body());
	}

	@Test
	void stalledResponseBodyGives504WithinDeadline() throws Exception {
		register("active", stub.url());
		stub.bodyDelayMs = 4000; // headers arrive at once; request-timeout-ms=2000 must still cover the body
		long start = System.nanoTime();
		HttpResponse<String> response = AppJson.send(port, "POST", "User/$register", null, null, null);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		assertEquals(504, response.statusCode(), response.body());
		assertTrue(elapsedMs < 3500, "took " + elapsedMs + " ms");
	}

	@Test
	void inactiveAppFallsThroughToHapi() throws Exception {
		register("inactive", stub.url());
		HttpResponse<String> response = AppJson.send(port, "GET", "Patient/$daily-report/2024-01-01", null, null, null);
		assertNotEquals(200, response.statusCode());
		assertEquals(0, stub.calls.get());
	}

	@Test
	void deletedAppFallsThroughToHapi() throws Exception {
		register("active", stub.url());
		int deleteStatus = AppJson.send(port, "DELETE", "App/myorg.myapp", null, null, null).statusCode();
		assertTrue(deleteStatus == 200 || deleteStatus == 204, "delete returned " + deleteStatus);
		AppJson.send(port, "POST", "User/$register", null, null, null);
		assertEquals(0, stub.calls.get());
	}

	@Test
	void securityDisabledLetsNonPublicOpsThrough() throws Exception {
		register("active", stub.url());
		assertEquals(200, AppJson.send(port, "GET", "Patient/$daily-report/2024-01-01", null, null, null).statusCode());
	}

	@Test
	void coreFhirIsUnaffected() throws Exception {
		register("active", stub.url());
		assertEquals(200, AppJson.send(port, "GET", "Patient", null, null, null).statusCode());
		assertEquals(0, stub.calls.get());
	}

	@Test
	void oversizedRequestBodyGives413() throws Exception {
		register("active", stub.url());
		HttpResponse<String> response = AppJson.send(port, "POST", "User/$register", "text/plain", "x".repeat(2048), null);
		assertEquals(413, response.statusCode(), response.body());
		assertTrue(response.body().contains("OperationOutcome"), response.body());
		assertEquals(0, stub.calls.get());
	}

	@Test
	void oversizedAppResponseGives502() throws Exception {
		register("active", stub.url());
		stub.body = "x".repeat(2048);
		HttpResponse<String> response = AppJson.send(port, "POST", "User/$register", null, null, null);
		assertEquals(502, response.statusCode(), response.body());
		assertTrue(response.body().contains("exceeds"), response.body());
	}

	@Test
	void malformedQueryStringGives400() throws Exception {
		register("active", stub.url());
		// java.net.URI refuses "%zz", so speak raw HTTP.
		String raw;
		try (java.net.Socket socket = new java.net.Socket("localhost", port)) {
			socket.getOutputStream().write(("POST /fhir/User/$register?a=%zz HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Length: 0\r\n\r\n")
				.getBytes(StandardCharsets.UTF_8));
			raw = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}
		assertTrue(raw.startsWith("HTTP/1.1 400"), raw);
		assertTrue(raw.contains("OperationOutcome"), raw);
		assertEquals(0, stub.calls.get());
	}
}
