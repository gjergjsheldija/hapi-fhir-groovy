 /**
  * FHIR Server
  * <p>
  * Copyright (c) 2025, Gjergj Sheldija
  * All rights reserved.
  *
  * @author Gjergj Sheldija <gjergj@sheldija.net>
  * @copyright 2025, Gjergj Sheldija
  * @license All rights reserved.
  * @since 2025-07-01
  */

 package com.gjergjsheldija.logging;

 import com.fasterxml.jackson.core.JsonProcessingException;
 import org.json.JSONException;
 import org.junit.jupiter.api.Test;
 import nl.altindag.log.LogCaptor;
 import org.skyscreamer.jsonassert.JSONAssert;
 import org.skyscreamer.jsonassert.JSONCompareMode;

 import java.util.List;

 public class LoggingServiceTest {
 	 LogCaptor logCaptor = LogCaptor.forName("com.gjergjsheldija.logging.LoggingService");

 	@Test
 	public void testProcessingCompletedNormally() throws JsonProcessingException, JSONException {
 		LoggingService loggingService = new LoggingService();
 		loggingService.logMessage("transaction", "{\"message_key\" : \"message_value\"}", "bundle");
 		String expected = "{\"operationType\":\"transaction\",\"operationName\":null,\"idOrResourceName\":\"bundle\",\"requestParameters\":null,\"requestBodyFhir\":\"{\\\"message_key\\\" : \\\"message_value\\\"}\"}";
 		List<String> logs = logCaptor.getInfoLogs();
 		JSONAssert.assertEquals(expected, logs.get(0), JSONCompareMode.STRICT);
 	}
 }