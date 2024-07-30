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

package com.clinomic.customoperation.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

class GroupByIntervalTest {

	@InjectMocks
	GroupByInterval groupByInterval;

	@Mock
	EntityManager entityManager;

	@Mock
	IFhirResourceDaoObservation<Observation> myObservationDao;

	@Mock
	RequestDetails requestDetails;

	@Mock
	Query query;

	ZonedDateTime startZonedDateTime;

	ZonedDateTime endZonedDateTime;

	String startDateStr;

	String endDateStr;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		startZonedDateTime = ZonedDateTime.parse("2024-07-24T01:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		endZonedDateTime = ZonedDateTime.parse("2024-07-24T03:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		startDateStr = startZonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		endDateStr = endZonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	@Test
	void testGroupByInterval() {
		List<Object[]> queryResults = new ArrayList<>();
		queryResults.add(new Object[] { Date.from(startZonedDateTime.toInstant()), "observation1" });

		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.getResultList()).thenReturn(queryResults);

		Observation observation = new Observation();
		observation.setId("observation1");

		List<Observation> observations = List.of(observation);
		when(myObservationDao.searchForResources(any(SearchParameterMap.class), any(RequestDetails.class)))
				.thenReturn(observations);

		Bundle resultBundle = groupByInterval.groupByInterval(
				new ReferenceParam("Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0"),
				new TokenParam("http://loinc.org", "8867-4"),
				new DateParam(startDateStr), 
				new DateParam(endDateStr),
				new NumberParam(3600),
				requestDetails);

		assertNotNull(resultBundle);
		assertEquals(Bundle.BundleType.SEARCHSET, resultBundle.getType());
		assertEquals(1, resultBundle.getEntry().size());

		Observation resultObservation = (Observation) resultBundle.getEntryFirstRep().getResource();
		assertEquals("observation1", resultObservation.getIdElement().getIdPart());

		Extension extension = resultObservation.getMeta().getExtension().get(0);
		assertEquals("https://fhir.mona.icu/StructureDefinition/intervalStartingPoint", extension.getUrl());
		assertEquals(startZonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
				((StringType) extension.getValue()).getValue());
	}

	@Test
	void testParseZonedDateTime() {
		String dateTimeStr = "2024-07-24T01:00:00Z";
		ZonedDateTime result = groupByInterval.parseZonedDateTime(dateTimeStr);
		assertEquals(ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME), result);

		dateTimeStr = "2024-07-24T01:00:00";
		result = groupByInterval.parseZonedDateTime(dateTimeStr);
		assertEquals(ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("UTC"))),
				result);
	}

	@Test
	void testGroupByIntervalWithNoResults() {
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.getResultList()).thenReturn(List.of());

		Bundle resultBundle = groupByInterval.groupByInterval(
				new ReferenceParam("Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0"),
				new TokenParam("http://loinc.org", "8867-4"),
				new DateParam(startDateStr),
				new DateParam(endDateStr),
				new NumberParam(3600),
				requestDetails);

		assertNotNull(resultBundle);
		assertEquals(Bundle.BundleType.SEARCHSET, resultBundle.getType());
		assertTrue(resultBundle.getEntry().isEmpty());
	}

	@Test
	void testGroupByIntervalWithException() {
		when(entityManager.createNativeQuery(anyString())).thenThrow(new RuntimeException("Database error"));

		assertThrows(RuntimeException.class,
				() -> groupByInterval.groupByInterval(
						new ReferenceParam("Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0"),
						new TokenParam("http://loinc.org", "8867-4"),
						new DateParam(startDateStr),
						new DateParam(endDateStr),
						new NumberParam(3600),
						requestDetails));
	}
}
