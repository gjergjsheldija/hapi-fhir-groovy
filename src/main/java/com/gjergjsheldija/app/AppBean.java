package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.JpaResourceDao;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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

	@Bean
	public AppValidationInterceptor appValidationInterceptor(AppRouteRegistry registry, IFhirResourceDao<App> appDao,
																		 ObjectProvider<RestfulServer> restfulServer, IInterceptorService interceptorService) {
		AppValidationInterceptor interceptor = new AppValidationInterceptor(fhirContext, registry, appDao, restfulServer);
		// The JPA interceptor service sees every DAO write, including non-REST ones.
		interceptorService.registerInterceptor(interceptor);
		return interceptor;
	}

	@Bean
	public FilterRegistrationBean<AppProxyFilter> appProxyFilterRegistration(
		AppRouteRegistry registry,
		// Two AccessTokenValidation beans exist in production (the @Component and SecurityBean's
		// myAccessTokenValidation); SecurityInterceptor uses the latter, so must we.
		@Qualifier("myAccessTokenValidation") ObjectProvider<AccessTokenValidation> tokenValidation,
		@Value("${hapi.fhir.security.enabled:false}") boolean securityEnabled,
		@Value("${hapi.fhir.server_address:}") String serverAddress,
		@Value("${hapi.fhir.apps.connect-timeout-ms:5000}") long connectTimeoutMs,
		@Value("${hapi.fhir.apps.request-timeout-ms:30000}") long requestTimeoutMs,
		@Value("${hapi.fhir.apps.max-request-bytes:10485760}") int maxRequestBytes,
		@Value("${hapi.fhir.apps.max-response-bytes:10485760}") int maxResponseBytes) {
		// Created here, not as its own bean, so Spring Boot doesn't also map it to /*.
		AppProxyFilter filter = new AppProxyFilter(registry, tokenValidation, securityEnabled, serverAddress,
			Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(requestTimeoutMs), fhirContext, maxRequestBytes, maxResponseBytes);
		FilterRegistrationBean<AppProxyFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setName("appProxyFilter");
		registration.addUrlPatterns("/fhir/*");
		return registration;
	}
}
