package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Routes requests that match an active App operation to that App; everything else continues to HAPI.
 */
public class AppProxyFilter extends OncePerRequestFilter {

	private static final Logger ourLog = LoggerFactory.getLogger(AppProxyFilter.class);
	private static final List<String> MDC_KEYS = List.of("uri", "method", "status", "latency", "operation_name", "id_resource_name");

	private final AppRouteRegistry registry;
	private final ObjectProvider<AccessTokenValidation> tokenValidation;
	private final ObjectProvider<CorsInterceptor> corsInterceptor;
	private final boolean securityEnabled;
	private final String serverAddress;
	private final Duration requestTimeout;
	private final HttpClient httpClient;
	private final FhirContext fhirContext;
	private final int maxRequestBytes;
	private final int maxResponseBytes;
	private final ObjectMapper mapper = new ObjectMapper();

	public AppProxyFilter(AppRouteRegistry registry, ObjectProvider<AccessTokenValidation> tokenValidation,
								 ObjectProvider<CorsInterceptor> corsInterceptor, boolean securityEnabled, String serverAddress, Duration connectTimeout, Duration requestTimeout,
								 FhirContext fhirContext, int maxRequestBytes, int maxResponseBytes) {
		this.maxRequestBytes = maxRequestBytes;
		this.maxResponseBytes = maxResponseBytes;
		this.registry = registry;
		this.tokenValidation = tokenValidation;
		this.corsInterceptor = corsInterceptor;
		this.securityEnabled = securityEnabled;
		this.serverAddress = serverAddress;
		this.requestTimeout = requestTimeout;
		this.fhirContext = fhirContext;
		this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		Optional<AppRouteRegistry.Match> match = registry.find(request.getMethod(), AppRoute.splitRequestPath(request.getPathInfo()));
		if (match.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		long start = System.nanoTime();
		AppRoute route = match.get().route();
		try {
			// App responses bypass HAPI's CorsInterceptor, so apply the same CORS config here.
			// On rejection the processor has already written the 403.
			CorsInterceptor cors = corsInterceptor.getIfAvailable();
			if (cors != null && !new DefaultCorsProcessor().processRequest(cors.getConfig(), request, response)) {
				return;
			}
			if (securityEnabled && !route.isPublic() && !isAuthorized(request)) {
				writeError(response, 401, "Missing or invalid access token");
				return;
			}
			forward(match.get(), request, response);
		} finally {
			log(request, response, route, start);
		}
	}

	private boolean isAuthorized(HttpServletRequest request) {
		if (Collections.list(request.getHeaders("Authorization")).size() > 1) {
			return false;
		}
		String authorization = request.getHeader("Authorization");
		AccessTokenValidation validator = tokenValidation.getIfAvailable();
		if (validator == null) {
			ourLog.error("hapi.fhir.security.enabled=true but no AccessTokenValidation bean; rejecting App call");
			return false;
		}
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return false;
		}
		try {
			validator.validateToken(authorization);
			return true;
		} catch (RuntimeException e) {
			ourLog.info("Rejected App call: {}", e.getMessage());
			return false;
		}
	}

	private void forward(AppRouteRegistry.Match match, HttpServletRequest request, HttpServletResponse response) throws IOException {
		AppRoute route = match.route();
		if (request.getContentLengthLong() > maxRequestBytes) {
			writeError(response, 413, "Request body exceeds hapi.fhir.apps.max-request-bytes");
			return;
		}
		byte[] body = request.getInputStream().readNBytes(maxRequestBytes + 1);
		if (body.length > maxRequestBytes) {
			writeError(response, 413, "Request body exceeds hapi.fhir.apps.max-request-bytes");
			return;
		}
		byte[] envelope;
		try {
			envelope = AppEnvelope.build(mapper, route, match.routeParams(), request, body, baseUrl(request));
		} catch (IllegalArgumentException e) {
			// URLDecoder rejects malformed percent-encoding in the query string.
			writeError(response, 400, "Malformed query string");
			return;
		}

		HttpRequest.Builder outbound = HttpRequest.newBuilder(URI.create(route.endpointUrl()))
			.timeout(requestTimeout)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofByteArray(envelope));
		if (route.secret() != null && !route.secret().isEmpty()) {
			String credentials = route.appId() + ":" + route.secret();
			outbound.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
		}

		HttpResponse<InputStream> appResponse;
		try {
			appResponse = httpClient.send(outbound.build(), HttpResponse.BodyHandlers.ofInputStream());
		} catch (HttpConnectTimeoutException e) {
			writeError(response, 502, "App " + route.appId() + " is unreachable");
			return;
		} catch (HttpTimeoutException e) {
			writeError(response, 504, "App " + route.appId() + " did not respond in time");
			return;
		} catch (IOException e) {
			writeError(response, 502, "App " + route.appId() + " is unreachable");
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			writeError(response, 502, "Interrupted while calling App " + route.appId());
			return;
		}

		try (InputStream in = appResponse.body()) {
			byte[] appBody = in.readNBytes(maxResponseBytes + 1);
			if (appBody.length > maxResponseBytes) {
				writeError(response, 502, "App " + route.appId() + " response exceeds hapi.fhir.apps.max-response-bytes");
				return;
			}
			response.setStatus(appResponse.statusCode());
			appResponse.headers().firstValue("Content-Type").ifPresent(response::setContentType);
			response.getOutputStream().write(appBody);
		}
	}

	private String baseUrl(HttpServletRequest request) {
		if (serverAddress != null && !serverAddress.isBlank()) {
			return serverAddress;
		}
		String port = (request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort();
		return request.getScheme() + "://" + request.getServerName() + port + request.getContextPath() + request.getServletPath();
	}

	private void writeError(HttpServletResponse response, int status, String message) throws IOException {
		OperationOutcome outcome = new OperationOutcome();
		outcome.addIssue()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(status == 401 ? OperationOutcome.IssueType.LOGIN
					: status == 400 || status == 413 ? OperationOutcome.IssueType.INVALID : OperationOutcome.IssueType.TRANSIENT)
			.setDiagnostics(message);
		response.setStatus(status);
		response.setContentType("application/fhir+json");
		response.getOutputStream().write(fhirContext.newJsonParser().encodeResourceToString(outcome).getBytes(StandardCharsets.UTF_8));
	}

	private void log(HttpServletRequest request, HttpServletResponse response, AppRoute route, long startNanos) {
		try {
			// Route template, not the raw URI: the URI may carry patient identifiers.
			MDC.put("uri", "/fhir/" + String.join("/", route.segments()));
			MDC.put("method", request.getMethod());
			MDC.put("status", String.valueOf(response.getStatus()));
			MDC.put("latency", String.valueOf((System.nanoTime() - startNanos) / 1_000_000));
			MDC.put("operation_name", route.operationId());
			MDC.put("id_resource_name", route.appId());
			ourLog.info("App call {}#{} -> {}", route.appId(), route.operationId(), response.getStatus());
		} finally {
			MDC_KEYS.forEach(MDC::remove);
		}
	}
}
