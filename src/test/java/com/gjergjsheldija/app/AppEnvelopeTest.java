package com.gjergjsheldija.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppEnvelopeTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final AppRoute route = new AppRoute("GET", List.of("Patient", "$daily-report", "{date}"),
		"myorg.myapp", "daily-patient-report", false, "http://x", "s");

	private JsonNode build(MockHttpServletRequest request, String body) throws Exception {
		byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
		return mapper.readTree(AppEnvelope.build(mapper, route, Map.of("date", "2024-01-01"), request, bytes, "http://base/fhir"));
	}

	@Test
	void buildsEnvelopeForGet() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fhir/Patient/$daily-report/2024-01-01");
		request.setQueryString("code=a&code=b&x=1%202");
		request.addHeader("Authorization", "Bearer t");
		request.addHeader("X-Multi", "one");
		request.addHeader("X-Multi", "two");

		JsonNode env = build(request, null);

		assertEquals("operation", env.get("type").asText());
		assertTrue(env.get("request").get("resource").isNull());
		assertEquals(List.of("a", "b"), mapper.convertValue(env.get("request").get("params").get("code"), List.class));
		assertEquals("1 2", env.get("request").get("params").get("x").get(0).asText());
		assertEquals("2024-01-01", env.get("request").get("route-params").get("date").asText());
		assertEquals("Bearer t", env.get("request").get("headers").get("authorization").asText());
		assertEquals("one, two", env.get("request").get("headers").get("x-multi").asText());
		assertEquals("http://base/fhir", env.get("box").get("base-url").asText());
		assertEquals("daily-patient-report", env.get("operation").get("id").asText());
		assertEquals("myorg.myapp", env.get("operation").get("app").get("id").asText());
		assertEquals("App", env.get("operation").get("app").get("resourceType").asText());
	}

	@Test
	void cookieHeaderIsForwarded() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fhir/x");
		request.addHeader("Cookie", "a=b");
		request.addHeader("X-Other", "y");
		JsonNode headers = build(request, null).get("request").get("headers");
		assertEquals("a=b", headers.get("cookie").asText());
		assertEquals("y", headers.get("x-other").asText());
	}

	@Test
	void jsonBodyIsEmbeddedAsJson() throws Exception {
		JsonNode env = build(new MockHttpServletRequest("POST", "/fhir/x"), "{\"resourceType\":\"Parameters\"}");
		assertEquals("Parameters", env.get("request").get("resource").get("resourceType").asText());
	}

	@Test
	void nonJsonBodyIsEmbeddedAsString() throws Exception {
		JsonNode env = build(new MockHttpServletRequest("POST", "/fhir/x"), "a=1&b=2");
		assertEquals("a=1&b=2", env.get("request").get("resource").asText());
	}

	@Test
	void parseQueryHandlesEdgeCases() {
		assertEquals(Map.of(), AppEnvelope.parseQuery(null));
		assertEquals(Map.of(), AppEnvelope.parseQuery(""));
		assertEquals(Map.of("flag", List.of("")), AppEnvelope.parseQuery("flag"));
		assertEquals(Map.of("a", List.of("x=y")), AppEnvelope.parseQuery("a=x%3Dy"));
	}
}
