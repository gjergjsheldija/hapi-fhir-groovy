package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.ResourceBinding;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.method.BaseMethodBinding;
import ca.uhn.fhir.rest.server.method.OperationMethodBinding;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rejects App writes (REST or DAO) that could not be routed safely. See spec section 1, "Write validation".
 */
@Interceptor
public class AppValidationInterceptor {

	private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
	private static final Set<String> STATUSES = Set.of(App.STATUS_ACTIVE, App.STATUS_INACTIVE);
	private static final Set<String> CUSTOM_TYPES = Set.of("App", "Configuration");
	private static final Set<String> RESERVED_FIRST_SEGMENTS = Set.of("metadata", "_history", "swagger-ui", "api-docs", "_search", ".well-known");

	/** Standard operations an App may never claim, even when the feature is disabled in this deployment. */
	private static final Set<String> RESERVED_OPERATIONS = Set.of(
		"$apply",
		"$apply-codesystem-delta-add",
		"$apply-codesystem-delta-remove",
		"$binary-access-read",
		"$binary-access-write",
		"$care-gaps",
		"$closure",
		"$collect-data",
		"$convert",
		"$data-requirements",
		"$diff",
		"$document",
		"$evaluate-measure",
		"$everything",
		"$expand",
		"$export",
		"$expunge",
		"$graphql",
		"$hapi.fhir.add-package",
		"$import",
		"$install",
		"$lastn",
		"$lookup",
		"$match",
		"$mdm-clear",
		"$mdm-create-link",
		"$mdm-duplicate-golden-resources",
		"$mdm-merge-golden-resources",
		"$mdm-not-duplicate",
		"$mdm-query-links",
		"$mdm-submit",
		"$mdm-update-link",
		"$member-match",
		"$meta",
		"$meta-add",
		"$meta-delete",
		"$package",
		"$populate",
		"$process-message",
		"$reindex",
		"$reindex-terminology",
		"$stats",
		"$submit-data",
		"$subsumes",
		"$translate",
		"$trigger-subscription",
		"$uninstall",
		"$upload-external-code-system",
		"$validate",
		"$validate-code");

	private final FhirContext fhirContext;
	private final AppRouteRegistry registry;
	private final IFhirResourceDao<App> appDao;
	private final ObjectProvider<RestfulServer> restfulServer;

	public AppValidationInterceptor(FhirContext fhirContext, AppRouteRegistry registry, IFhirResourceDao<App> appDao,
											  ObjectProvider<RestfulServer> restfulServer) {
		this.fhirContext = fhirContext;
		this.registry = registry;
		this.appDao = appDao;
		this.restfulServer = restfulServer;
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

	/**
	 * Entries of one transaction cannot see each other through the DAO search, so check Apps in the Bundle against each other.
	 */
	@Hook(Pointcut.STORAGE_TRANSACTION_PROCESSING)
	public void onTransaction(IBaseBundle theInput) {
		if (!(theInput instanceof Bundle bundle)) {
			return;
		}
		List<AppRoute> seen = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (!(entry.getResource() instanceof App app) || !App.STATUS_ACTIVE.equals(app.getStatus())) {
				continue;
			}
			String url = entry.getRequest().getUrl();
			String appId = app.getIdElement().hasIdPart() ? app.getIdElement().getIdPart()
				: (url != null && url.startsWith("App/") ? url.substring(4) : "?" + seen.size());
			// Malformed Apps are rejected per entry by validate(); only compile well-formed routes here.
			for (App.AppOperationComponent op : app.getOperation()) {
				try {
					AppRoute route = new AppRoute(op.getMethod(), AppRoute.parsePath(op.getPath()), appId, op.getId(), op.isPublic(), null, null);
					for (AppRoute other : seen) {
						if (!Objects.equals(other.appId(), appId) && other.collidesWith(route)) {
							errors.add("App " + appId + " operation " + route.operationId() + " collides with App " + other.appId()
								+ " operation " + other.operationId());
						}
					}
					seen.add(route);
				} catch (RuntimeException e) {
					// ignore here; validate() reports it
				}
			}
		}
		if (!errors.isEmpty()) {
			throw new UnprocessableEntityException("Invalid App Bundle: " + String.join("; ", errors));
		}
	}

	void validate(App app) {
		List<String> errors = new ArrayList<>();
		Set<String> serverOps = serverOperationNames();
		String appId = app.getIdElement().getIdPart();

		if (app.getStatus() == null || !STATUSES.contains(app.getStatus())) {
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
			if (op.getMethod() == null || !METHODS.contains(op.getMethod())) {
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
			String shadow = shadowProblem(segments, serverOps);
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
			List<AppRoute> others = new ArrayList<>(registry.routes());
			// The registry is a snapshot; also check stored Apps (same-transaction siblings, other nodes).
			List<App> stored = appDao.search(SearchParameterMap.newSynchronous(), SystemRequestDetails.forAllPartitions())
				.getAllResources().stream()
				.map(App.class::cast)
				.filter(a -> App.STATUS_ACTIVE.equals(a.getStatus()) && !Objects.equals(appId, a.getIdElement().getIdPart()))
				.toList();
			AppRouteRegistry.buildRoutes(stored).values().forEach(others::addAll);
			for (AppRoute mine : ownRoutes) {
				for (AppRoute other : others) {
					if (!Objects.equals(other.appId(), appId) && other.collidesWith(mine)) {
						errors.add("operation " + mine.operationId() + " collides with App " + other.appId()
							+ " operation " + other.operationId());
					}
				}
			}
		}

		if (!errors.isEmpty()) {
			throw new UnprocessableEntityException("Invalid App " + (appId == null ? "(new)" : appId) + ": " + String.join("; ", errors));
		}
	}

	private String shadowProblem(List<String> segments, Set<String> serverOps) {
		String first = segments.get(0);
		if (AppRoute.isParam(first) || RESERVED_FIRST_SEGMENTS.contains(first)) {
			return "path would shadow core FHIR endpoints";
		}
		for (int i = 0; i < Math.min(2, segments.size()); i++) {
			String seg = segments.get(i);
			if (seg.startsWith("$") && (RESERVED_OPERATIONS.contains(seg) || serverOps.contains(seg))) {
				return "path would shadow server operation " + seg;
			}
		}
		boolean isResourceType = fhirContext.getResourceTypes().contains(first) || CUSTOM_TYPES.contains(first);
		if (isResourceType && (segments.size() < 2 || !segments.get(1).startsWith("$"))) {
			return "path would shadow core FHIR endpoints; after a resource type the next segment must start with $";
		}
		return null;
	}

	private Set<String> serverOperationNames() {
		RestfulServer server = restfulServer.getObject();
		Set<String> names = new HashSet<>();
		List<BaseMethodBinding> bindings = new ArrayList<>(server.getServerBindings());
		for (ResourceBinding rb : server.getResourceBindings()) {
			bindings.addAll(rb.getMethodBindings());
		}
		for (BaseMethodBinding b : bindings) {
			if (b instanceof OperationMethodBinding op && op.getName() != null) {
				names.add(op.getName().startsWith("$") ? op.getName() : "$" + op.getName());
			}
		}
		return names;
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
