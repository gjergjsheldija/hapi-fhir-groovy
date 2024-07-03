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

package com.clinomic.configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Configuration
public class ConfigurationBean {

	@Autowired
	private FhirContext fhirContext;

	@Bean(name = "myConfigurationDao")
	public IFhirResourceDao<com.clinomic.configuration.Configuration> daoConfiguration() {
		ca.uhn.fhir.jpa.dao.BaseHapiFhirResourceDao<com.clinomic.configuration.Configuration> retVal = new ca.uhn.fhir.jpa.dao.JpaResourceDao<com.clinomic.configuration.Configuration>();
		retVal.setResourceType(com.clinomic.configuration.Configuration.class);
		retVal.setContext(fhirContext);
		return retVal;
	}

	@Bean(name = "myConfigurationResourceProvider")
	public ConfigurationResourceProvider resourceProviderConfiguration() {
		ConfigurationResourceProvider retVal = new ConfigurationResourceProvider();
		retVal.setContext(fhirContext);
		retVal.setDao(daoConfiguration());
		return retVal;
	}


	@VisibleForTesting
	public ConfigurationBean setFhirContext(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
		return this;
	}
}
