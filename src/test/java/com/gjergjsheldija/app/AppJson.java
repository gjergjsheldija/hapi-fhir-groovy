package com.gjergjsheldija.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

final class AppJson {

	static final HttpClient CLIENT = HttpClient.newHttpClient();

	private AppJson() {
	}

	static String op(String id, String method, String path, boolean isPublic) {
		return """
			{ "id": "%s", "method": "%s", "path": "%s", "public": %s }""".formatted(id, method, path, isPublic);
	}

	static String app(String id, String status, String endpointUrl, String secret, String operationsJson) {
		String secretJson = secret == null ? "" : ", \"secret\": \"%s\"".formatted(secret);
		return """
			{
			  "resourceType": "App",
			  "id": "%s",
			  "status": "%s",
			  "endpoint": { "type": "http-rpc", "url": "%s"%s },
			  "operation": [ %s ]
			}""".formatted(id, status, endpointUrl, secretJson, operationsJson);
	}

	static HttpResponse<String> put(int port, String id, String json) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/fhir/App/" + id))
			.header("Content-Type", "application/fhir+json")
			.PUT(HttpRequest.BodyPublishers.ofString(json))
			.build();
		return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	static HttpResponse<String> send(int port, String method, String pathAndQuery, String contentType, String body, String authorization)
		throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/fhir/" + pathAndQuery))
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (contentType != null) {
			builder.header("Content-Type", contentType);
		}
		if (authorization != null) {
			builder.header("Authorization", authorization);
		}
		return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
}
