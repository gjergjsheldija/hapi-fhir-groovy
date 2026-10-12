package com.gjergjsheldija.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the JSON body POSTed to an App (Aidbox-style "operation" envelope).
 */
final class AppEnvelope {

	private AppEnvelope() {
	}

	static byte[] build(ObjectMapper mapper, AppRoute route, Map<String, String> routeParams, HttpServletRequest request,
							  byte[] body, String baseUrl) throws IOException {
		Map<String, Object> req = new LinkedHashMap<>();
		req.put("resource", parseBody(mapper, body));
		// Parsed from the raw query string: getParameterMap() would consume form-encoded bodies.
		req.put("params", parseQuery(request.getQueryString()));
		req.put("route-params", routeParams);
		req.put("headers", headers(request));

		Map<String, Object> app = new LinkedHashMap<>();
		app.put("id", route.appId());
		app.put("resourceType", "App");
		Map<String, Object> operation = new LinkedHashMap<>();
		operation.put("id", route.operationId());
		operation.put("app", app);

		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("type", "operation");
		envelope.put("request", req);
		envelope.put("box", Map.of("base-url", baseUrl));
		envelope.put("operation", operation);
		return mapper.writeValueAsBytes(envelope);
	}

	static Map<String, List<String>> parseQuery(String rawQuery) {
		Map<String, List<String>> params = new LinkedHashMap<>();
		if (rawQuery == null || rawQuery.isEmpty()) {
			return params;
		}
		for (String pair : rawQuery.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
		}
		return params;
	}

	private static Object parseBody(ObjectMapper mapper, byte[] body) {
		if (body.length == 0) {
			return null;
		}
		try {
			return mapper.readTree(body);
		} catch (JsonProcessingException e) {
			return new String(body, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// readTree(byte[]) only throws JsonProcessingException in practice; keep the raw body if not.
			return new String(body, StandardCharsets.UTF_8);
		}
	}

	private static Map<String, String> headers(HttpServletRequest request) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (String name : Collections.list(request.getHeaderNames())) {
			headers.put(name.toLowerCase(Locale.ROOT), String.join(", ", Collections.list(request.getHeaders(name))));
		}
		return headers;
	}
}
