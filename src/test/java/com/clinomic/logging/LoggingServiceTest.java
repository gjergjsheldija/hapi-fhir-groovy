/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Leipzig
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Leipzig
 * @license All rights reserved.
 * @since 2024-07-01
 */

package com.clinomic.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.altindag.log.LogCaptor;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.List;

public class LoggingServiceTest {
	LogCaptor logCaptor = LogCaptor.forName("com.clinomic.logging.LoggingService");

	@Test
	public void testProcessingCompletedNormally() throws JsonProcessingException, JSONException {
		LoggingService loggingService = new LoggingService();
		loggingService.logMessage("transaction", "{\"message_key\" : \"message_value\"}", "bundle");
		String expected = "{\"operationType\":\"transaction\",\"operationName\":null,\"idOrResourceName\":\"bundle\",\"requestParameters\":null,\"requestBodyFhir\":\"{\\\"message_key\\\" : \\\"message_value\\\"}\"}";
		List<String> logs = logCaptor.getInfoLogs();
		JSONAssert.assertEquals(expected, logs.get(0), JSONCompareMode.STRICT);
	}
}