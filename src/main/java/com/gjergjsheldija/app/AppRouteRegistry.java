package com.gjergjsheldija.app;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.tx.IHapiTransactionService;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of routes from active Apps. Rebuilt after each committed App write on this node
 * (before the write's response is sent), after $expunge, and periodically, so changes made on other
 * nodes are picked up within refresh-ms.
 */
@Interceptor
public class AppRouteRegistry {

	private static final Logger ourLog = LoggerFactory.getLogger(AppRouteRegistry.class);
	private static final Set<RestOperationTypeEnum> EXTENDED_OPS = EnumSet.of(
		RestOperationTypeEnum.EXTENDED_OPERATION_SERVER, RestOperationTypeEnum.EXTENDED_OPERATION_TYPE,
		RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE);

	public record Match(AppRoute route, Map<String, String> routeParams) {
	}

	private record Snapshot(Map<String, List<AppRoute>> routesByMethod, Set<String> versionKeys) {
	}

	private final IFhirResourceDao<App> appDao;
	private final IHapiTransactionService txService;
	private final TaskScheduler taskScheduler;
	private final Duration refreshInterval;
	private volatile Snapshot snapshot = new Snapshot(Map.of(), Set.of());

	public AppRouteRegistry(IFhirResourceDao<App> appDao, IHapiTransactionService txService, TaskScheduler taskScheduler,
		Duration refreshInterval) {
		this.appDao = appDao;
		this.txService = txService;
		this.taskScheduler = taskScheduler;
		this.refreshInterval = refreshInterval;
	}

	public Optional<Match> find(String method, List<String> requestSegments) {
		for (AppRoute route : snapshot.routesByMethod().getOrDefault(method, List.of())) {
			Optional<Map<String, String>> params = route.match(requestSegments);
			if (params.isPresent()) {
				return Optional.of(new Match(route, params.get()));
			}
		}
		return Optional.empty();
	}

	public synchronized boolean refresh() {
		SystemRequestDetails request = SystemRequestDetails.forAllPartitions();
		// A new transaction: from afterCommit, joining the finished write's transaction trips Hibernate.
		List<App> apps = txService.withRequest(request).withPropagation(Propagation.REQUIRES_NEW).execute(() ->
			appDao.search(SearchParameterMap.newSynchronous(), request).getAllResources().stream()
				.map(App.class::cast)
				.filter(app -> App.STATUS_ACTIVE.equals(app.getStatus()))
				.toList());
		// lastUpdated is part of the key: after $expunge a re-created App restarts at version 1.
		Set<String> versionKeys = apps.stream()
			.map(app -> app.getIdElement().getIdPart() + "/" + app.getIdElement().getVersionIdPart() + "/"
				+ (app.getMeta().getLastUpdated() == null ? "" : app.getMeta().getLastUpdated().getTime()))
			.collect(Collectors.toUnmodifiableSet());
		if (versionKeys.equals(snapshot.versionKeys())) {
			return false;
		}
		Map<String, List<AppRoute>> routes = buildRoutes(apps);
		snapshot = new Snapshot(routes, versionKeys);
		ourLog.info("App routes rebuilt: {} active apps, {} routes", apps.size(), routes.values().stream().mapToInt(List::size).sum());
		return true;
	}

	static Map<String, List<AppRoute>> buildRoutes(List<App> apps) {
		Map<String, List<AppRoute>> byMethod = new HashMap<>();
		List<App> ordered = apps.stream()
			.sorted(Comparator.comparing((App a) -> a.getMeta().getLastUpdated(), Comparator.nullsLast(Comparator.naturalOrder())))
			.toList();
		List<AppRoute> accepted = new ArrayList<>();
		for (App app : ordered) {
			String appId = app.getIdElement().getIdPart();
			try {
				List<AppRoute> appRoutes = new ArrayList<>();
				for (App.AppOperationComponent op : app.getOperation()) {
					if (op.getMethod() == null || op.getMethod().isBlank()) {
						throw new IllegalArgumentException("operation " + op.getId() + ": method is required");
					}
					appRoutes.add(new AppRoute(op.getMethod(), AppRoute.parsePath(op.getPath()), appId, op.getId(),
						op.isPublic(), app.getEndpoint().getUrl(), app.getEndpoint().getSecret()));
				}
				AppRoute clash = appRoutes.stream()
					.flatMap(route -> accepted.stream().filter(other -> !other.appId().equals(appId) && other.collidesWith(route)))
					.findFirst().orElse(null);
				if (clash != null) {
					ourLog.warn("Skipping App {}: collides with App {} ({} {})", appId, clash.appId(), clash.method(), clash.segments());
					continue;
				}
				accepted.addAll(appRoutes);
				appRoutes.forEach(route -> byMethod.computeIfAbsent(route.method(), k -> new ArrayList<>()).add(route));
			} catch (RuntimeException e) {
				ourLog.warn("Skipping App {}: invalid definition ({}: {})", appId, e.getClass().getSimpleName(), e.getMessage());
			}
		}
		Map<String, List<AppRoute>> sorted = new HashMap<>();
		byMethod.forEach((method, list) -> sorted.put(method, list.stream()
			.sorted(Comparator.comparingLong(AppRoute::literalCount).reversed())
			.toList()));
		return Map.copyOf(sorted);
	}

	/** $expunge removes Apps without firing the storage hooks below. */
	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
	public void onRequestCompleted(RequestDetails theRequestDetails) {
		if (EXTENDED_OPS.contains(theRequestDetails.getRestOperationType()) && "$expunge".equals(theRequestDetails.getOperation())) {
			refreshQuietly();
		}
	}

	/** Storage hooks see every App write (REST, Bundle entry, DAO call) on this node, before the response is sent. */
	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
	public void onCreated(IBaseResource theResource) {
		refreshAfterCommit(theResource);
	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED)
	public void onUpdated(IBaseResource theOldResource, IBaseResource theNewResource) {
		refreshAfterCommit(theNewResource);
	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_DELETED)
	public void onDeleted(IBaseResource theResource) {
		refreshAfterCommit(theResource);
	}

	/** One refresh per transaction, after commit; a rolled-back write is left to the periodic refresh. */
	private void refreshAfterCommit(IBaseResource theResource) {
		if (!(theResource instanceof App)) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			refreshQuietly();
			return;
		}
		// The registry itself is the per-transaction dedupe key.
		if (TransactionSynchronizationManager.hasResource(this)) {
			return;
		}
		TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				refreshQuietly();
			}

			@Override
			public void afterCompletion(int theStatus) {
				TransactionSynchronizationManager.unbindResourceIfPossible(AppRouteRegistry.this);
			}
		});
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		refreshQuietly();
		taskScheduler.scheduleWithFixedDelay(this::refreshQuietly, Instant.now().plus(refreshInterval), refreshInterval);
	}

	private void refreshQuietly() {
		try {
			refresh();
		} catch (RuntimeException e) {
			ourLog.error("App route refresh failed; keeping previous routes", e);
		}
	}
}
