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

package com.gjergjsheldija.configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class ConfigurationBean {

	private static final Logger log = LoggerFactory.getLogger(ConfigurationBean.class);

	ConfigurationBean()
	{
		log.info("ConfigurationBean ctor.");
	}

	@PostConstruct
   public void init()
   {
		log.debug("ConfigurationBean PostConstruct :");
		fhirContext.registerCustomType(com.gjergjsheldija.configuration.Configuration.class);
	}

	@Autowired
	private FhirContext fhirContext;

	@Bean(name = "myConfigurationDao")
	public IFhirResourceDao<com.gjergjsheldija.configuration.Configuration> daoConfiguration() {

		log.info("Creating bean myConfigurationDao");

		ca.uhn.fhir.jpa.dao.BaseHapiFhirResourceDao<com.gjergjsheldija.configuration.Configuration> retVal = new ca.uhn.fhir.jpa.dao.JpaResourceDao<com.gjergjsheldija.configuration.Configuration>();
		retVal.setResourceType(com.gjergjsheldija.configuration.Configuration.class);
		retVal.setContext(fhirContext);
		return retVal;
	}

	@Bean(name = "myConfigurationResourceProvider")
	public ConfigurationResourceProvider resourceProviderConfiguration() {

		log.info("Creating bean myConfigurationResourceProvider");

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
