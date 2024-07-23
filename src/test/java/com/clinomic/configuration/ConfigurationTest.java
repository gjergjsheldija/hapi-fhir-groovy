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

package com.clinomic.configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.clinomic.util.BaseFhirR4ForTesting;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.SearchParameter;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


public class ConfigurationTest extends BaseFhirR4ForTesting {

	@Autowired
	ConfigurationResourceProvider configurationResourceProvider;

	@BeforeEach
	void beforeEach() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		String ourServerBase = "http://localhost:" + port + "/fhir/";
		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);


		SearchParameter searchParameterName = new SearchParameter();
		searchParameterName.setCode("name")
			.addBase("Configuration")
			.setStatus(Enumerations.PublicationStatus.ACTIVE)
			.setType(Enumerations.SearchParamType.STRING)
			.setExpression("name");

		ourClient.create().resource(searchParameterName).execute();

		SearchParameter searchParameterType = new SearchParameter();
		searchParameterType.setCode("type")
			.addBase("Configuration")
			.setStatus(Enumerations.PublicationStatus.ACTIVE)
			.setType(Enumerations.SearchParamType.STRING)
			.setExpression("type");

		ourClient.create().resource(searchParameterType).execute();

		SearchParameter searchParameterStatus = new SearchParameter();
		searchParameterStatus.setCode("status")
			.addBase("Configuration")
			.setStatus(Enumerations.PublicationStatus.ACTIVE)
			.setType(Enumerations.SearchParamType.STRING)
			.setExpression("status");

		ourClient.create().resource(searchParameterStatus).execute();

		mySearchParamRegistry.forceRefresh();
	}


	@Test
	void testCreateAndUpdate() {
		Configuration c = new Configuration();
		c.setName("test_name");
		c.setStatus(Configuration.ConfigurationStatus.ACTIVE);
		c.setType(Configuration.ConfigurationType.SCRIPT);
		c.setBody(new StringType("some body"));
		MethodOutcome result = ourClient.create().resource(c).execute();


		Bundle searchResult = ourClient.search()
			.forResource(Configuration.class)
			.returnBundle(Bundle.class)
			.execute();

		assert (searchResult.getEntry().size() == 1);
		assert ((Configuration) searchResult.getEntry().get(0).getResource()).getName().equals("test_name");

		c.setName("test_name_new");
		result = ourClient.update(result.getId().getIdPart(), c);

		Bundle searchResultUpdate = ourClient.search()
			.forResource(Configuration.class)
			.returnBundle(Bundle.class)
			.execute();

		assert (searchResultUpdate.getEntry().size() == 1);
		assert ((Configuration) searchResultUpdate.getEntry().get(0).getResource()).getName().equals("test_name_new");

	}
}
