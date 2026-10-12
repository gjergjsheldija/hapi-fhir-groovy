package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.JpaResourceDao;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;

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

	@Bean
	public AppRouteRegistry appRouteRegistry(
		IFhirResourceDao<App> appDao,
		RestfulServer restfulServer,
		@Qualifier("taskScheduler") TaskScheduler taskScheduler,
		@Value("${hapi.fhir.apps.refresh-ms:30000}") long refreshMs) {
		AppRouteRegistry registry = new AppRouteRegistry(appDao, taskScheduler, Duration.ofMillis(refreshMs));
		// SERVER_* pointcuts fire on the RestfulServer's own interceptor service.
		restfulServer.registerInterceptor(registry);
		return registry;
	}
}
