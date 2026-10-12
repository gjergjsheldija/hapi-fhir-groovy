package com.gjergjsheldija.app;

import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppResourceTest extends BaseFhirR4ForTesting {

	@BeforeEach
	void setUpClient() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	@Test
	void putReadAndSearchRoundTrip() throws Exception {
		String json = AppJson.app("myorg.myapp", "active", "http://127.0.0.1:9999", "s3cr3t",
			AppJson.op("daily-patient-report", "GET", "Patient/$daily-report/{date}", false) + ","
				+ AppJson.op("register-user", "POST", "User/$register", true));

		HttpResponse<String> put = AppJson.put(port, "myorg.myapp", json);
		assertEquals(201, put.statusCode(), put.body());

		HttpResponse<String> read = AppJson.send(port, "GET", "App/myorg.myapp", null, null, null);
		assertEquals(200, read.statusCode(), read.body());
		App app = ourCtx.newJsonParser().parseResource(App.class, read.body());
		assertEquals("active", app.getStatus());
		assertEquals("http-rpc", app.getEndpoint().getType());
		assertEquals("http://127.0.0.1:9999", app.getEndpoint().getUrl());
		assertEquals("s3cr3t", app.getEndpoint().getSecret());
		assertEquals(2, app.getOperation().size());
		assertEquals("daily-patient-report", app.getOperation().get(0).getId());
		assertEquals("GET", app.getOperation().get(0).getMethod());
		assertEquals("Patient/$daily-report/{date}", app.getOperation().get(0).getPath());
		assertEquals(false, app.getOperation().get(0).isPublic());
		assertEquals(true, app.getOperation().get(1).isPublic());

		HttpResponse<String> search = AppJson.send(port, "GET", "App", null, null, null);
		assertEquals(200, search.statusCode(), search.body());
		assertTrue(search.body().contains("\"total\":1") || search.body().contains("\"total\": 1"), search.body());
	}
}
