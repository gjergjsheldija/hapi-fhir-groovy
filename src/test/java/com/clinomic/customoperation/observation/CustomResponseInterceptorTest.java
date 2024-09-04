/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Andrey Zagariya <azagariya@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-29
 */

package com.clinomic.customoperation.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.servlet.http.HttpServletResponse;

class CustomResponseInterceptorTest {

	@Mock
	private DaoRegistry daoRegistry;

	@Mock
	private IParser fhirParser;

	@Mock
	private RequestDetails requestDetails;

	@Mock
	private HttpServletResponse response;

	@SuppressWarnings("rawtypes")
	@Mock
	private IFhirSystemDao systemDao;

	@Mock
	private PrintWriter printWriter;

	@Mock
	private FhirContext fhirContext;

	@InjectMocks
	private CustomResponseInterceptor interceptor;

	private Parameters parameters;

	@BeforeEach
	void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);

		when(daoRegistry.getSystemDao()).thenReturn(systemDao);

		when(systemDao.getContext()).thenReturn(fhirContext);

		when(fhirContext.newJsonParser()).thenReturn(mock(IParser.class));

		when(daoRegistry.getSystemDao().getContext().newJsonParser()).thenReturn(fhirParser);

		interceptor.init();

		when(response.getWriter()).thenReturn(printWriter);

		parameters = new Parameters();
		when(fhirParser.encodeResourceToString(parameters)).thenReturn("{\"parameter\":[]}");

		when(requestDetails.getOperation()).thenReturn("$group-by-interval");
	}

	@Test
	void testCustomizeGroupByIntervalResponse_NormalSerializationForNonGroupByInterval() throws IOException {
		when(requestDetails.getOperation()).thenReturn("some-other-operation");

		boolean result = interceptor.customizeGroupByIntervalResponse(requestDetails, response, parameters);

		assertTrue(result);
		verify(response, never()).setContentType(anyString());
		verify(response.getWriter(), never()).write(anyString());
	}

	@Test
	void testCustomizeGroupByIntervalResponse_CustomSerializationForGroupByInterval() throws IOException {
		boolean result = interceptor.customizeGroupByIntervalResponse(requestDetails, response, parameters);

		assertFalse(result);
		verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
		verify(response.getWriter()).write("{ }");
	}

	@Test
	void testConvertToCustomJson_ValidJson() throws Exception {
		String parametersJson = "{\"parameter\":[{\"name\":\"300\",\"part\":[{\"name\":\"LOINC|8867-4\",\"part\":[{\"name\":\"2024-08-09T00:00:00.000Z\",\"resource\":{\"some\":\"resource\"}}]}]}]}";
		String expectedJson = "{\n  \"300\" : {\n    \"LOINC|8867-4\" : {\n      \"2024-08-09T00:00:00.000Z\" : {\n        \"some\" : \"resource\"\n      }\n    }\n  }\n}";

		Method method = CustomResponseInterceptor.class.getDeclaredMethod("convertToCustomJson", String.class);
		method.setAccessible(true);
		String result = (String) method.invoke(interceptor, parametersJson);

		ObjectMapper mapper = new ObjectMapper();
		JsonNode resultJsonNode = mapper.readTree(result);
		JsonNode expectedJsonNode = mapper.readTree(expectedJson);

		assertEquals(expectedJsonNode, resultJsonNode);
	}

	@Test
	void testConvertToCustomJson_ErrorHandling() throws Exception {
		String invalidJson = "{invalid-json}";

		Method method = CustomResponseInterceptor.class.getDeclaredMethod("convertToCustomJson", String.class);
		method.setAccessible(true);
		String result = (String) method.invoke(interceptor, invalidJson);

		assertEquals("{}", result);
	}

	@Test
	void testInit() throws Exception {
		Field field = CustomResponseInterceptor.class.getDeclaredField("fhirParser");
		field.setAccessible(true);
		IParser parser = (IParser) field.get(interceptor);

		assertNotNull(parser);
		verify(daoRegistry.getSystemDao().getContext(), times(1)).newJsonParser();
	}
}
