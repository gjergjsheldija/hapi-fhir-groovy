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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
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
        ReferenceParam encounter = new ReferenceParam("Encounter", "3b205f2f-3813-4bff-9945-1729d52b9eb0");
        List<TokenParam> codes = Arrays.asList(new TokenParam("http://loinc.org", "8867-4"),
                new TokenParam("http://snomed.info", "55553"));
        DateParam startDateTime = new DateParam("2024-07-24T01:00:00Z");
        DateParam endDateTime = new DateParam("2024-07-24T03:00:00Z");
        NumberParam interval = new NumberParam(3600);

        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(
                Arrays.asList(
                        new Object[]{Date.from(Instant.parse("2024-07-24T01:00:00Z")), "uuid-1", "http://loinc.org", "8867-4"},
                        new Object[]{Date.from(Instant.parse("2024-07-24T02:00:00Z")), "uuid-2", "http://snomed.info", "55553"}
                )
        );

        Observation mockObservation1 = new Observation();
        mockObservation1.setId("uuid-1");
        Observation mockObservation2 = new Observation();
        mockObservation2.setId("uuid-2");

        when(myObservationDao.searchForResources(any(), any())).thenReturn(Arrays.asList(mockObservation1, mockObservation2));

        Parameters result = groupByInterval.groupByInterval(encounter, codes, startDateTime, endDateTime, interval, requestDetails);

        assertEquals(2, result.getParameter().size());

        Parameters.ParametersParameterComponent firstSystemParam = result.getParameter().get(0);
        assertEquals("http://loinc.org|8867-4", firstSystemParam.getName());
        assertEquals(1, firstSystemParam.getPart().size());
        assertEquals(mockObservation1, firstSystemParam.getPart().get(0).getResource());

        Parameters.ParametersParameterComponent secondSystemParam = result.getParameter().get(1);
        assertEquals("http://snomed.info|55553", secondSystemParam.getName());
        assertEquals(1, secondSystemParam.getPart().size());
        assertEquals(mockObservation2, secondSystemParam.getPart().get(0).getResource());
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
		
		List<TokenParam> codes = Arrays.asList(new TokenParam("http://loinc.org", "8867-4"),
                new TokenParam("http://snomed.info", "55553"));

		Parameters result = groupByInterval.groupByInterval(
				new ReferenceParam("Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0"),
				codes,
				new DateParam(startDateStr),
				new DateParam(endDateStr),
				new NumberParam(3600),
				requestDetails);

		assertNotNull(result);
		assertTrue(result.getParameter().isEmpty());
	}

	@Test
	void testGroupByIntervalWithException() {
		when(entityManager.createNativeQuery(anyString())).thenThrow(new RuntimeException("Database error"));

		List<TokenParam> codes = Arrays.asList(new TokenParam("http://loinc.org", "8867-4"),
                new TokenParam("http://snomed.info", "55553"));
		
		assertThrows(RuntimeException.class,
				() -> groupByInterval.groupByInterval(
						new ReferenceParam("Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0"),
						codes,
						new DateParam(startDateStr),
						new DateParam(endDateStr),
						new NumberParam(3600),
						requestDetails));
	}
}
