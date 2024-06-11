package com.clinomic.util;

/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Leipzig
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Leipzig
 * @license All rights reserved.
 * @since 2024-06-06
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import com.clinomic.configuration.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = {Application.class},
	properties = {
		"hapi.fhir.custom-bean-packages=com.clinomic.configuration",
		"spring.datasource.url=jdbc:h2:mem:dbr4",
		"hapi.fhir.enable_repository_validating_interceptor=true",
		"hapi.fhir.fhir_version=r4",
		"hapi.fhir.mdm_enabled=false",
		"hapi.fhir.cr_enabled=false",
		"hapi.fhir.subscription.websocket_enabled=false",
		"spring.main.allow-bean-definition-overriding=true"})
public class BaseFhirR4ForTesting {

	public IGenericClient ourClient;
	public FhirContext ourCtx;
	@Autowired
	public ISearchParamRegistry mySearchParamRegistry;
	@Autowired
	public IFhirResourceDao<Configuration> myConfigurationDao;

	@LocalServerPort
	public int port;

}
