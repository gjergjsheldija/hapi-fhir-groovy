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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;

@ExtendWith(MockitoExtension.class)
class ObservationCustomOperationProviderTest {

	@Mock
	private IFhirResourceDaoObservation<Observation> observationDao;

	@InjectMocks
	private ObservationCustomOperationProvider operationProvider;

	@Mock
	private RequestDetails requestDetails;

	@Mock
	private IBundleProvider bundleProvider;

	private ReferenceParam encounter;
	private TokenParam code;
	private DateParam startDate;
	private DateParam endDate;
	private NumberParam interval;

	@BeforeEach
	void setup() {
		encounter = new ReferenceParam("Encounter/e3465cac-0e29-4dbe-bd93-0286b904a64f");
		code = new TokenParam("http://loinc.org", "8867-4");
		startDate = new DateParam(null, Date.from(LocalDateTime.of(2024, 7, 22, 0, 0).toInstant(ZoneOffset.UTC)));
		endDate = new DateParam(null, Date.from(LocalDateTime.of(2024, 7, 23, 0, 0).toInstant(ZoneOffset.UTC)));
		interval = new NumberParam(5);
	}

	@Test
	void testGroupByInterval() {
		when(bundleProvider.getResources(0, Integer.MAX_VALUE)).thenReturn(List.of(new Observation()));
		when(observationDao.search(any(), any())).thenReturn(bundleProvider);

		Bundle result = operationProvider.groupByInterval(encounter, code, startDate, endDate, interval, requestDetails);

		assertNotNull(result);
		assertEquals(Bundle.BundleType.SEARCHSET, result.getType());
		assertTrue(result.getEntry().size() > 0);
	}

	@Test
	void testGroupByInterval_EmptyResult() {
		when(bundleProvider.getResources(0, Integer.MAX_VALUE)).thenReturn(List.of());
		when(observationDao.search(any(), any())).thenReturn(bundleProvider);

		Bundle result = operationProvider.groupByInterval(encounter, code, startDate, endDate, interval, requestDetails);

		assertNotNull(result);
		assertEquals(Bundle.BundleType.SEARCHSET, result.getType());
		assertEquals(0, result.getEntry().size());
	}

	@Test
	void testGroupByInterval_ExceptionHandling() {
		when(observationDao.search(any(), any())).thenThrow(new RuntimeException("Oops!"));

		Bundle result = operationProvider.groupByInterval(encounter, code, startDate, endDate, interval, requestDetails);

		assertNotNull(result);
		assertEquals(Bundle.BundleType.SEARCHSET, result.getType());
		assertEquals(0, result.getEntry().size());
	}

	@Test
	void testGroupByInterval_NullInputs() {
		assertThrows(NullPointerException.class, () -> {
			operationProvider.groupByInterval(null, null, null, null, null, requestDetails);
		});
	}
}
