package com.gjergjsheldija.app;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.tx.NonTransactionalHapiTransactionService;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppRouteRegistryFailureTest {

	private IFhirResourceDao<App> dao;
	private AppRouteRegistry registry;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		dao = mock(IFhirResourceDao.class);
		registry = new AppRouteRegistry(dao, new NonTransactionalHapiTransactionService(), mock(TaskScheduler.class), Duration.ofHours(1));
	}

	private static App validApp() {
		App app = new App()
			.setStatus("active")
			.setEndpoint(new App.AppEndpointComponent().setType("http-rpc").setUrl("http://127.0.0.1:1"));
		App.AppOperationComponent op = new App.AppOperationComponent().setMethod("GET").setPath("$x");
		op.setId("op1");
		app.addOperation(op);
		app.setId(new IdType("App", "a1", "1"));
		app.getMeta().setLastUpdated(new Date(1000));
		return app;
	}

	private void searchReturnsValidAppThenFails() {
		IBundleProvider bundle = mock(IBundleProvider.class);
		when(bundle.getAllResources()).thenReturn(List.<IBaseResource>of(validApp()));
		when(dao.search(any(SearchParameterMap.class), any(RequestDetails.class)))
			.thenReturn(bundle)
			.thenThrow(new InternalErrorException("db down"));
	}

	private static RequestDetails request(String resource, RestOperationTypeEnum type, String operation) {
		RequestDetails rd = mock(RequestDetails.class);
		when(rd.getResourceName()).thenReturn(resource);
		when(rd.getRestOperationType()).thenReturn(type);
		when(rd.getOperation()).thenReturn(operation);
		return rd;
	}

	private static RequestDetails bundleRequest(RestOperationTypeEnum type, Bundle bundle) {
		RequestDetails rd = request(null, type, null);
		when(rd.getResource()).thenReturn(bundle);
		return rd;
	}

	@Test
	void failedRefreshKeepsPreviousSnapshot() {
		searchReturnsValidAppThenFails();
		assertTrue(registry.refresh());
		assertThrows(InternalErrorException.class, registry::refresh);
		assertTrue(registry.find("GET", List.of("$x")).isPresent());
	}

	private void searchReturnsNothing() {
		IBundleProvider empty = mock(IBundleProvider.class);
		when(empty.getAllResources()).thenReturn(List.of());
		when(dao.search(any(SearchParameterMap.class), any(RequestDetails.class))).thenReturn(empty);
	}

	private static RequestDetails expunge(String resource, RestOperationTypeEnum type) {
		return request(resource, type, "$expunge");
	}

	@Test
	void hookNeverThrowsAndKeepsPreviousSnapshot() {
		searchReturnsValidAppThenFails();
		registry.refresh();
		assertDoesNotThrow(() -> registry.onRequestCompleted(expunge(null, RestOperationTypeEnum.EXTENDED_OPERATION_SERVER)));
		assertTrue(registry.find("GET", List.of("$x")).isPresent());
	}

	@Test
	void storageHookNeverThrowsAndKeepsPreviousSnapshot() {
		searchReturnsValidAppThenFails();
		registry.refresh();
		assertDoesNotThrow(() -> registry.onUpdated(validApp(), validApp()));
		assertTrue(registry.find("GET", List.of("$x")).isPresent());
	}

	@Test
	void expungeTriggersRefresh() {
		searchReturnsNothing();
		registry.onRequestCompleted(expunge(null, RestOperationTypeEnum.EXTENDED_OPERATION_SERVER));
		registry.onRequestCompleted(expunge("Patient", RestOperationTypeEnum.EXTENDED_OPERATION_TYPE));
		registry.onRequestCompleted(expunge("Patient", RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE));
		verify(dao, times(3)).search(any(SearchParameterMap.class), any(RequestDetails.class));
	}

	/** App writes, including Bundles, are covered by the storage hooks; the server hook handles only $expunge. */
	@Test
	void serverHookIgnoresEverythingButExpunge() {
		Bundle withApp = new Bundle();
		withApp.addEntry().setResource(validApp()).getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("App/a1");
		registry.onRequestCompleted(request("App", RestOperationTypeEnum.UPDATE, null));
		registry.onRequestCompleted(request("App", RestOperationTypeEnum.READ, null));
		registry.onRequestCompleted(request("Patient", RestOperationTypeEnum.UPDATE, null));
		registry.onRequestCompleted(request(null, RestOperationTypeEnum.EXTENDED_OPERATION_SERVER, "$other"));
		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.TRANSACTION, withApp));
		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.BATCH, withApp));
		verify(dao, never()).search(any(SearchParameterMap.class), any(RequestDetails.class));
	}

	@Test
	void appWriteOutsideTransactionRefreshesAtOnce() {
		searchReturnsNothing();
		registry.onCreated(validApp());
		registry.onUpdated(validApp(), validApp());
		registry.onDeleted(validApp());
		registry.onCreated(new Patient());
		registry.onUpdated(new Patient(), new Patient());
		registry.onDeleted(new Patient());
		verify(dao, times(3)).search(any(SearchParameterMap.class), any(RequestDetails.class));
	}

	@Test
	void appWritesInTransactionRefreshOnceAfterCommit() {
		searchReturnsNothing();
		TransactionSynchronizationManager.initSynchronization();
		try {
			registry.onCreated(validApp());
			registry.onUpdated(validApp(), validApp());
			registry.onDeleted(validApp());
			verify(dao, never()).search(any(SearchParameterMap.class), any(RequestDetails.class));
			List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
			assertEquals(1, syncs.size(), "one synchronization per transaction");
			syncs.forEach(TransactionSynchronization::afterCommit);
			syncs.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
		verify(dao, times(1)).search(any(SearchParameterMap.class), any(RequestDetails.class));
		assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty(), "dedupe key must be unbound");
	}

	@Test
	void rolledBackTransactionDoesNotRefreshAndUnbindsKey() {
		TransactionSynchronizationManager.initSynchronization();
		try {
			registry.onCreated(validApp());
			TransactionSynchronizationManager.getSynchronizations()
				.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
		verify(dao, never()).search(any(SearchParameterMap.class), any(RequestDetails.class));
		assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty(), "dedupe key must be unbound");
	}

	@Test
	void nonAppWritesInTransactionRegisterNothing() {
		TransactionSynchronizationManager.initSynchronization();
		try {
			registry.onCreated(new Patient());
			registry.onUpdated(new Patient(), new Patient());
			registry.onDeleted(new Patient());
			assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
}
