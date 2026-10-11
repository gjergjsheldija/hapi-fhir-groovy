package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.JpaResourceDao;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppBean {

	private final FhirContext fhirContext;

	public AppBean(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
		// Register before any DAO or parser touches the type.
		fhirContext.registerCustomType(App.class);
	}

	@Bean(name = "myAppDao")
	public IFhirResourceDao<App> appDao() {
		JpaResourceDao<App> dao = new JpaResourceDao<>();
		dao.setResourceType(App.class);
		dao.setContext(fhirContext);
		return dao;
	}

	@Bean(name = "myAppResourceProvider")
	public AppResourceProvider appResourceProvider(IFhirResourceDao<App> appDao, RestfulServer restfulServer) {
		AppResourceProvider provider = new AppResourceProvider();
		provider.setContext(fhirContext);
		provider.setDao(appDao);
		restfulServer.registerProvider(provider);
		return provider;
	}
}
