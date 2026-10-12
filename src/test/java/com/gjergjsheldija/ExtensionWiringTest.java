package com.gjergjsheldija;

import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.gjergjsheldija.app.AppProxyFilter;
import com.gjergjsheldija.app.AppRouteRegistry;
import com.gjergjsheldija.app.AppValidationInterceptor;
import com.gjergjsheldija.auditevent.AuditEventInterceptor;
import com.gjergjsheldija.logging.CustomLoggingInterceptor;
import com.gjergjsheldija.scripting.ScriptingProvider;
import com.gjergjsheldija.security.SecurityInterceptor;
import com.gjergjsheldija.terminology.TerminologyCapabilityInterceptor;
import com.gjergjsheldija.validation.CustomRequestValidatingInterceptor;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.filter.OrderedFormContentFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots with the real src/main/resources/application.yaml (not the test one) so a broken
 * or stripped production config fails the build.
 */
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = {Application.class},
	properties = {
		"spring.config.location=file:src/main/resources/application.yaml",
		"spring.datasource.url=jdbc:h2:mem:wiring",
		"management.server.port=0",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.properties.hibernate.dialect=ca.uhn.fhir.jpa.model.dialect.HapiFhirH2Dialect",
		"hapi.fhir.security.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExtensionWiringTest {

	@Autowired
	RestfulServer restfulServer;

	@Autowired
	ApplicationContext applicationContext;

	@Autowired
	IInterceptorService jpaInterceptorService;

	@Test
	void extensionInterceptorsAreRegistered() {
		List<Object> interceptors = restfulServer.getInterceptorService().getAllRegisteredInterceptors();
		for (Class<?> expected : List.of(
			CustomLoggingInterceptor.class,
			AuditEventInterceptor.class,
			SecurityInterceptor.class,
			TerminologyCapabilityInterceptor.class,
			CustomRequestValidatingInterceptor.class)) {
			assertTrue(interceptors.stream().anyMatch(expected::isInstance), "missing interceptor " + expected.getSimpleName());
		}
	}

	@Test
	void scriptingProviderIsRegistered() {
		assertTrue(restfulServer.getPlainProviders().stream().anyMatch(ScriptingProvider.class::isInstance));
	}

	@Test
	void appsAreWired() {
		FilterRegistrationBean<?> registration = applicationContext.getBean("appProxyFilterRegistration", FilterRegistrationBean.class);
		assertTrue(registration.getFilter() instanceof AppProxyFilter);
		assertTrue(registration.getOrder() < OrderedFormContentFilter.DEFAULT_ORDER, "must run before FormContentFilter");
		assertTrue(jpaInterceptorService.getAllRegisteredInterceptors().stream().anyMatch(AppValidationInterceptor.class::isInstance));
		assertTrue(restfulServer.getInterceptorService().getAllRegisteredInterceptors().stream().anyMatch(AppRouteRegistry.class::isInstance));
		assertTrue(jpaInterceptorService.getAllRegisteredInterceptors().stream().anyMatch(AppRouteRegistry.class::isInstance));
	}

	// A stdio MCP transport would read System.in in production (and steals Surefire's fork channel in tests).
	@Test
	void mcpIsServedOnlyOverStreamableHttp() {
		var transports = applicationContext.getBeansOfType(McpServerTransportProviderBase.class).values();
		assertEquals(1, transports.size(), "MCP transports: " + transports);
		assertTrue(transports.iterator().next() instanceof HttpServletStreamableServerTransportProvider, "MCP transports: " + transports);
	}
}
