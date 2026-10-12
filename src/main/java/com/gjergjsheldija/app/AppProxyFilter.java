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
import java.io.UncheckedIOException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
		this.maxRequestBytes = checkLimit("hapi.fhir.apps.max-request-bytes", maxRequestBytes);
		this.maxResponseBytes = checkLimit("hapi.fhir.apps.max-response-bytes", maxResponseBytes);
		this.registry = registry;
		this.tokenValidation = tokenValidation;
		this.corsInterceptor = corsInterceptor;
		this.securityEnabled = securityEnabled;
		this.serverAddress = serverAddress;
		this.requestTimeout = requestTimeout;
		this.fhirContext = fhirContext;
		// HTTP/1.1: no h2c upgrade attempt against plain-http Apps.
		this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(connectTimeout).build();
	}

	/** The caps are read with readNBytes(limit + 1), so Integer.MAX_VALUE would overflow. */
	private static int checkLimit(String key, int value) {
		if (value <= 0 || value == Integer.MAX_VALUE) {
			throw new IllegalArgumentException(key + " must be between 1 and " + (Integer.MAX_VALUE - 1) + ", was " + value);
		}
		return value;
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

	private void forward(AppRouteRegistry.Match match, HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {
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

		// HttpRequest.timeout stops applying once headers arrive, so one deadline covers headers + body.
		AtomicReference<InputStream> bodyStream = new AtomicReference<>();
		CompletableFuture<AppReply> future = httpClient.sendAsync(outbound.build(), HttpResponse.BodyHandlers.ofInputStream())
			.thenApply(r -> {
				bodyStream.set(r.body());
				return readCapped(r);
			});
		AppReply reply;
		try {
			reply = future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			future.cancel(true);
			closeQuietly(bodyStream.get());
			writeError(response, 504, "App " + route.appId() + " did not respond in time");
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			closeQuietly(bodyStream.get());
			writeError(response, 502, "Interrupted while calling App " + route.appId());
			return;
		} catch (ExecutionException e) {
			Throwable cause = e.getCause() instanceof UncheckedIOException u ? u.getCause() : e.getCause();
			if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
				writeError(response, 504, "App " + route.appId() + " did not respond in time");
			} else if (cause instanceof IOException) {
				writeError(response, 502, "App " + route.appId() + " is unreachable");
			} else {
				throw new ServletException("Calling App " + route.appId() + " failed", cause);
			}
			return;
		}

		if (reply.body() == null) {
			writeError(response, 502, "App " + route.appId() + " response exceeds hapi.fhir.apps.max-response-bytes");
			return;
		}
		response.setStatus(reply.status());
		reply.contentType().ifPresent(response::setContentType);
		response.getOutputStream().write(reply.body());
	}

	/** body is null when the App's response is over max-response-bytes. */
	private record AppReply(int status, Optional<String> contentType, byte[] body) {
	}

	private AppReply readCapped(HttpResponse<InputStream> appResponse) {
		try (InputStream in = appResponse.body()) {
			byte[] appBody = in.readNBytes(maxResponseBytes + 1);
			return new AppReply(appResponse.statusCode(), appResponse.headers().firstValue("Content-Type"),
				appBody.length > maxResponseBytes ? null : appBody);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void closeQuietly(InputStream in) {
		if (in == null) {
			return;
		}
		try {
			in.close();
		} catch (IOException e) {
			ourLog.debug("Closing timed-out App response stream failed: {}", e.getMessage());
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
