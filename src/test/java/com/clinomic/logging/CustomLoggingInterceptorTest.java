/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-07-01
 */

package com.clinomic.logging;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.clinomic.util.BaseFhirR4ForTesting;
import nl.altindag.log.LogCaptor;
import org.hl7.fhir.r4.model.Patient;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.MDC;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CustomLoggingInterceptorTest extends BaseFhirR4ForTesting {

	@LocalServerPort
	private int port;

	private LogCaptor logCaptor;


	@BeforeEach
	void setUp() {
		logCaptor = LogCaptor.forName("com.clinomic.logging.LoggingService");

		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		String ourServerBase = "http://localhost:" + port + "/fhir/";
		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
	}

	@Test
	void testAuditInterceptors() throws JSONException {

		Patient pat = new Patient();
		String patId = ourClient.create().resource(pat).execute().getId().getIdPart();

		Patient readPat = ourClient.read().resource(Patient.class).withId(patId).execute();

		assertFalse(readPat.getId().isEmpty());

		List<String> logs = logCaptor.getInfoLogs();
		String expected = "{\"operationType\":\"read\",\"operationName\":\"\",\"idOrResourceName\":\"Patient/" + patId + "\",\"requestParameters\":null,\"requestBodyFhir\":\"\"}";
		JSONAssert.assertEquals(expected, logs.get(1), JSONCompareMode.STRICT);

	}

	@Test
	public void testMessageSourceIsSet() throws JSONException {
		CustomLoggingInterceptor customLoggingInterceptor = new CustomLoggingInterceptor(new LoggingService(), false);
		customLoggingInterceptor.logSource();
		assertEquals("REST", MDC.get("message_source"));
	}

}
