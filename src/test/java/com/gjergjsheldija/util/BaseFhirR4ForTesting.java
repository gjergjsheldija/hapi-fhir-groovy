/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-06-06
 */

package com.gjergjsheldija.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import com.gjergjsheldija.configuration.Configuration;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = {Application.class},
	properties = {
		"hapi.fhir.custom-interceptor-classes=com.gjergjsheldija.logging.CustomLoggingInterceptor",
		"hapi.fhir.custom-bean-packages=com.gjergjsheldija.configuration,com.gjergjsheldija.logging,com.gjergjsheldija.scripting,com.gjergjsheldija.dynamic",
		"spring.datasource.url=jdbc:h2:mem:dbr4",
		"hapi.fhir.enable_repository_validating_interceptor=false",
		"hapi.fhir.fhir_version=r4",
		"hapi.fhir.mdm_enabled=false",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.subscription.websocket_enabled=false",
		"hapi.fhir.delete-expunge-enabled=true",
		"hapi.fhir.allow-multiple-delete=true",
		"hapi.fhir.log_request_body=true",
		"hapi.fhir.delete-expunge-enabled=true",
		"hapi.fhir.allow_multiple_delete=true",
		"spring.main.allow-bean-definition-overriding=true"})
public class BaseFhirR4ForTesting {

	public IGenericClient ourClient;
	public FhirContext ourCtx;
	@Autowired
	public ISearchParamRegistry mySearchParamRegistry;
	@Autowired
	public IFhirResourceDao<Configuration> myConfigurationDao;

	@Autowired
	private IFhirResourceDao<Patient> patientResourceDao;

	@LocalServerPort
	public int port;

	public void cleanUp() {
		Parameters parameters = new Parameters();
		parameters.addParameter().setName("expungeEverything").setValue(new BooleanType(true));

		Parameters outcome = ourClient
			.operation()
			.onServer()
			.named("$expunge")
			.withParameters(parameters)
			.execute();

	}
}
