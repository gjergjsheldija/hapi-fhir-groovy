package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rejects App writes (REST or DAO) that could not be routed safely. See spec section 1, "Write validation".
 */
@Interceptor
public class AppValidationInterceptor {

	private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
	private static final Set<String> STATUSES = Set.of(App.STATUS_ACTIVE, App.STATUS_INACTIVE);
	private static final Set<String> CUSTOM_TYPES = Set.of("App", "Configuration");
	private static final Set<String> RESERVED_FIRST_SEGMENTS = Set.of("metadata", "_history");

	private final FhirContext fhirContext;
	private final AppRouteRegistry registry;

	public AppValidationInterceptor(FhirContext fhirContext, AppRouteRegistry registry) {
		this.fhirContext = fhirContext;
		this.registry = registry;
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void onCreate(IBaseResource theResource) {
		if (theResource instanceof App app) {
			validate(app);
		}
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
	public void onUpdate(IBaseResource theOldResource, IBaseResource theNewResource) {
		if (theNewResource instanceof App app) {
			validate(app);
		}
	}

	void validate(App app) {
		List<String> errors = new ArrayList<>();
		String appId = app.getIdElement().getIdPart();

		if (!STATUSES.contains(app.getStatus())) {
			errors.add("status must be one of " + STATUSES);
		}
		App.AppEndpointComponent endpoint = app.getEndpoint();
		if (endpoint == null) {
			errors.add("endpoint is required");
		} else {
			if (!App.ENDPOINT_TYPE_HTTP_RPC.equals(endpoint.getType())) {
				errors.add("endpoint.type must be http-rpc");
			}
			if (!isHttpUrl(endpoint.getUrl())) {
				errors.add("endpoint.url must be an absolute http(s) URL");
			}
		}
		if (app.getOperation().isEmpty()) {
			errors.add("at least one operation is required");
		}

		Set<String> operationIds = new HashSet<>();
		List<AppRoute> ownRoutes = new ArrayList<>();
		for (App.AppOperationComponent op : app.getOperation()) {
			String opId = op.getId();
			if (opId == null || opId.isBlank()) {
				errors.add("operation id is required");
			} else if (!operationIds.add(opId)) {
				errors.add("duplicate operation id " + opId);
			}
			if (!METHODS.contains(op.getMethod())) {
				errors.add("operation " + opId + ": method must be one of " + METHODS);
				continue;
			}
			List<String> segments;
			try {
				segments = AppRoute.parsePath(op.getPath());
			} catch (IllegalArgumentException e) {
				errors.add("operation " + opId + ": " + e.getMessage());
				continue;
			}
			String shadow = shadowProblem(segments);
			if (shadow != null) {
				errors.add("operation " + opId + ": " + shadow);
				continue;
			}
			AppRoute route = new AppRoute(op.getMethod(), segments, appId, opId, op.isPublic(), null, null);
			for (AppRoute existing : ownRoutes) {
				if (existing.collidesWith(route)) {
					errors.add("operation " + opId + " collides with operation " + existing.operationId());
				}
			}
			ownRoutes.add(route);
		}

		if (App.STATUS_ACTIVE.equals(app.getStatus())) {
			for (AppRoute mine : ownRoutes) {
				for (AppRoute other : registry.routes()) {
					if (!other.appId().equals(appId) && other.collidesWith(mine)) {
						errors.add("operation " + mine.operationId() + " collides with App " + other.appId()
							+ " operation " + other.operationId());
					}
				}
			}
		}

		if (!errors.isEmpty()) {
			throw new UnprocessableEntityException("Invalid App " + appId + ": " + String.join("; ", errors));
		}
	}

	private String shadowProblem(List<String> segments) {
		String first = segments.get(0);
		if (AppRoute.isParam(first) || RESERVED_FIRST_SEGMENTS.contains(first)) {
			return "path would shadow core FHIR endpoints";
		}
		boolean isResourceType = fhirContext.getResourceTypes().contains(first) || CUSTOM_TYPES.contains(first);
		if (isResourceType && (segments.size() < 2 || !segments.get(1).startsWith("$"))) {
			return "path would shadow core FHIR endpoints; after a resource type the next segment must start with $";
		}
		return null;
	}

	private static boolean isHttpUrl(String url) {
		if (url == null) {
			return false;
		}
		try {
			URI uri = new URI(url);
			return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
