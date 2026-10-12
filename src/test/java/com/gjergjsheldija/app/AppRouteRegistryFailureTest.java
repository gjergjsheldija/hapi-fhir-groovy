package com.gjergjsheldija.app;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
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

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
		registry = new AppRouteRegistry(dao, mock(TaskScheduler.class), Duration.ofHours(1));
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

	@Test
	void hookNeverThrowsAndKeepsPreviousSnapshot() {
		searchReturnsValidAppThenFails();
		registry.refresh();
		assertDoesNotThrow(() -> registry.onRequestCompleted(request("App", RestOperationTypeEnum.UPDATE, null)));
		assertTrue(registry.find("GET", List.of("$x")).isPresent());
	}

	@Test
	void transactionBatchAndExpungeTriggerRefresh() {
		IBundleProvider empty = mock(IBundleProvider.class);
		when(empty.getAllResources()).thenReturn(List.of());
		when(dao.search(any(SearchParameterMap.class), any(RequestDetails.class))).thenReturn(empty);

		Bundle withApp = new Bundle();
		withApp.addEntry().setResource(validApp()).getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("App/a1");
		Bundle deletesApp = new Bundle();
		deletesApp.addEntry().getRequest().setMethod(Bundle.HTTPVerb.DELETE).setUrl("App/a1");
		Bundle conditionalApp = new Bundle();
		conditionalApp.addEntry().getRequest().setMethod(Bundle.HTTPVerb.DELETE).setUrl("App?status=inactive");

		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.TRANSACTION, withApp));
		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.BATCH, deletesApp));
		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.TRANSACTION, conditionalApp));
		registry.onRequestCompleted(request(null, RestOperationTypeEnum.EXTENDED_OPERATION_SERVER, "$expunge"));
		registry.onRequestCompleted(request("Patient", RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "$expunge"));
		registry.onRequestCompleted(request("Patient", RestOperationTypeEnum.EXTENDED_OPERATION_INSTANCE, "$expunge"));
		verify(dao, times(6)).search(any(SearchParameterMap.class), any(RequestDetails.class));
	}

	@Test
	void bundlesWithoutAppEntriesDoNotRefresh() {
		Bundle patientsOnly = new Bundle();
		patientsOnly.addEntry().setResource(new Patient()).getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");
		patientsOnly.addEntry().getRequest().setMethod(Bundle.HTTPVerb.DELETE).setUrl("Appointment/1");
		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.TRANSACTION, patientsOnly));
		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.BATCH, patientsOnly));
		registry.onRequestCompleted(bundleRequest(RestOperationTypeEnum.TRANSACTION, null));
		verify(dao, never()).search(any(SearchParameterMap.class), any(RequestDetails.class));
	}

	@Test
	void unrelatedRequestsDoNotRefresh() {
		registry.onRequestCompleted(request("App", RestOperationTypeEnum.READ, null));
		registry.onRequestCompleted(request("Patient", RestOperationTypeEnum.UPDATE, null));
		registry.onRequestCompleted(request(null, RestOperationTypeEnum.EXTENDED_OPERATION_SERVER, "$other"));
		verify(dao, never()).search(any(SearchParameterMap.class), any(RequestDetails.class));
	}
}
