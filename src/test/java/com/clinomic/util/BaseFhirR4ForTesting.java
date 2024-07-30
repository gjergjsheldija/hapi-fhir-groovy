/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-06-06
 */

package com.clinomic.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import com.clinomic.configuration.Configuration;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = {Application.class},
	properties = {
		"hapi.fhir.custom-interceptor-classes=com.clinomic.logging.CustomLoggingInterceptor",
		"hapi.fhir.custom-bean-packages=com.clinomic.configuration,com.clinomic.logging",
		"spring.datasource.url=jdbc:h2:mem:dbr4",
		"hapi.fhir.enable_repository_validating_interceptor=false",
		"hapi.fhir.fhir_version=r4",
		"hapi.fhir.mdm_enabled=false",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.subscription.websocket_enabled=false",
		"hapi.fhir.log_request_body=true",
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

}
