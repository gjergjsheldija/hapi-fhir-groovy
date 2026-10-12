package com.gjergjsheldija.scripting;

import ca.uhn.fhir.context.FhirContext;
import com.gjergjsheldija.configuration.Configuration;
import com.google.common.io.Resources;
import org.hl7.fhir.r4.model.StringType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Raw HTTP helpers, so tests control the URL, body and Authorization header exactly. */
final class ScriptJson {

	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	private ScriptJson() {
	}

	/** An active script Configuration named PatientCreated holding the PatientDemo interceptor. */
	static String scriptConfiguration(String id) throws IOException {
		Configuration c = new Configuration();
		if (id != null) {
			c.setId(id);
		}
		c.setName("PatientCreated");
		c.setStatus(Configuration.ConfigurationStatus.ACTIVE);
		c.setType(Configuration.ConfigurationType.SCRIPT);
		c.setBody(new StringType(Resources.toString(Resources.getResource("scripting/PatientDemo.groovy"), StandardCharsets.UTF_8)));
		return FhirContext.forR4().newJsonParser().encodeResourceToString(c);
	}

	static HttpResponse<String> send(int port, String method, String pathAndQuery, String body, String authorization)
		throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/fhir/" + pathAndQuery))
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (body != null) {
			builder.header("Content-Type", "application/fhir+json");
		}
		if (authorization != null) {
			builder.header("Authorization", authorization);
		}
		return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * ScriptingInterceptor loads on SERVER_PROCESSING_COMPLETED_NORMALLY, which runs after the response was sent,
	 * so the client can see the response before the script is loaded. Polls for up to {@code millis}.
	 */
	static boolean within(long millis, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + millis;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() >= deadline) {
				return false;
			}
			Thread.sleep(50);
		}
		return true;
	}
}
