package com.gjergjsheldija;

import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.gjergjsheldija.auditevent.AuditEventInterceptor;
import com.gjergjsheldija.logging.CustomLoggingInterceptor;
import com.gjergjsheldija.scripting.ScriptingProvider;
import com.gjergjsheldija.security.SecurityInterceptor;
import com.gjergjsheldija.terminology.TerminologyCapabilityInterceptor;
import com.gjergjsheldija.validation.CustomRequestValidatingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

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
		"management.server.port=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExtensionWiringTest {

	@Autowired
	RestfulServer restfulServer;

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
}
