/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen 
 * All rights reserved.
 *
 * @author Andrey Zagariya <azagariya@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-07-24
 */

package com.clinomic.customoperation;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.when;

class ObservationCustomOperationProviderTest {

	@InjectMocks
	private ObservationCustomOperationProvider provider;

	@Mock
	private IBundleProvider bundleProvider;

	@Mock
	private IFhirResourceDaoObservation<Observation> myObservationDao;


	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testProcessResults() {
		List<IBaseResource> observations = createMockObservations();
		ObservationCustomOperationProvider customOperationProvider = new ObservationCustomOperationProvider();

		when(myObservationDao.search(any(SearchParameterMap.class))).thenReturn(bundleProvider);

		when(bundleProvider.getResources(anyInt(), anyInt()))
			.thenReturn(observations)
			.thenReturn(Collections.emptyList());

		Set<String> noResults = customOperationProvider.generateDateIntervals(
			new DateParam("2024-01-01T00:00"),
			new DateParam("2024-01-01T01:00"),
			new NumberParam(10));

		assertEquals(customOperationProvider.processResults(bundleProvider, noResults).size(), 0);

		when(bundleProvider.getResources(anyInt(), anyInt()))
			.thenReturn(observations)
			.thenReturn(Collections.emptyList());

		Set<String> singleResults = customOperationProvider.generateDateIntervals(
			new DateParam("2024-01-01T11:00"),
			new DateParam("2024-01-01T11:05"),
			new NumberParam(5));

		assertEquals(customOperationProvider.processResults(bundleProvider, singleResults).size(), 1);

		when(bundleProvider.getResources(anyInt(), anyInt()))
			.thenReturn(observations)
			.thenReturn(Collections.emptyList());

		Set<String> twoResults = customOperationProvider.generateDateIntervals(
			new DateParam("2024-01-01T11:00"),
			new DateParam("2024-01-01T11:15"),
			new NumberParam(5));

		assertEquals(customOperationProvider.processResults(bundleProvider, twoResults).size(), 2);
	}

	private List<IBaseResource> createMockObservations() {
		List<IBaseResource> resources = new ArrayList<>();
		Observation obs1 = new Observation();
		obs1.setEffective(new DateTimeType(new Date(2024 - 1900, 0, 1, 11, 0)));
		obs1.setId("Observation/1");
		resources.add(obs1);

		Observation obs2 = new Observation();
		obs2.setEffective(new DateTimeType(new Date(2024 - 1900, 0, 1, 11, 15)));
		obs2.setId("Observation/2");
		resources.add(obs2);

		return resources;
	}

	@Test
	void testGenerateDateIntervals() {
		DateParam startDate = new DateParam("2023-01-01T00:00");
		DateParam endDate = new DateParam("2023-01-01T02:00");
		NumberParam interval = new NumberParam(60); // 60 minutes

		// Use reflection to access the private method
		java.lang.reflect.Method method;
		try {
			method = ObservationCustomOperationProvider.class.getDeclaredMethod("generateDateIntervals", DateParam.class, DateParam.class, NumberParam.class);
			method.setAccessible(true);
			@SuppressWarnings("unchecked")
			Set<String> result = (Set<String>) method.invoke(provider, startDate, endDate, interval);

			assertEquals(3, result.size());
			assertTrue(result.contains("2023-01-01T00:00"));
			assertTrue(result.contains("2023-01-01T01:00"));
			assertTrue(result.contains("2023-01-01T02:00"));
		} catch (Exception e) {
			fail("Exception occurred: " + e.getMessage());
		}
	}
}