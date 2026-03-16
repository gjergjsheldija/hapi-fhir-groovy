 package com.gjergjsheldija.logging;

 import ca.uhn.fhir.context.FhirContext;
 import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
 import com.gjergjsheldija.util.BaseFhirR4ForTesting;
 import nl.altindag.log.LogCaptor;
 import org.hl7.fhir.r4.model.Patient;
 import org.json.JSONException;
 import org.junit.jupiter.api.AfterEach;
 import org.junit.jupiter.api.BeforeEach;
 import org.junit.jupiter.api.Test;
 import org.skyscreamer.jsonassert.JSONAssert;
 import org.skyscreamer.jsonassert.JSONCompareMode;
 import org.slf4j.MDC;
 import org.springframework.boot.test.web.server.LocalServerPort;
 import org.springframework.test.annotation.DirtiesContext;

 import static org.junit.Assert.assertEquals;
 import static org.junit.Assert.assertFalse;

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

 @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
 public class CustomLoggingInterceptorTest extends BaseFhirR4ForTesting {

 	@LocalServerPort
 	private int port;

 	private LogCaptor logCaptor;

 	@BeforeEach
 	void setUp() {

 		ourCtx = FhirContext.forR4();
 		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
 		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
 		String ourServerBase = "http://localhost:" + port + "/fhir/";
 		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
 		logCaptor = LogCaptor.forName("com.gjergjsheldija.logging.LoggingService");
 		logCaptor.clearLogs();
 	}

 	@Test
 	void testAuditInterceptors() throws JSONException {

 		Patient pat = new Patient();
 		String patId = ourClient.create().resource(pat).execute().getId().getIdPart();

 		Patient readPat = ourClient.read().resource(Patient.class).withId(patId).execute();

 		assertFalse(readPat.getId().isEmpty());

 		String expected = "{\"operationType\":\"read\",\"operationName\":\"\",\"idOrResourceName\":\"Patient/" + patId
 				+ "\",\"requestParameters\":null,\"requestBodyFhir\":\"\"}";

 		try {
 			Thread.sleep(5000);
 		} catch (Exception e) {
 		} // Give the logs a chance to flush.

 		// List<String> logs = logCaptor.getInfoLogs();

 		// JSONAssert.assertEquals(expected, logs.get(1), JSONCompareMode.STRICT);

 	}

 	@Test
 	public void testMessageSourceIsSet() throws JSONException {
 		CustomLoggingInterceptor customLoggingInterceptor = new CustomLoggingInterceptor(new LoggingService(), false);
 		customLoggingInterceptor.logSource();
 		assertEquals("REST", MDC.get("message_source"));
 	}

 	@AfterEach
 	public void after() {
 		cleanUp();
 	}
 }
