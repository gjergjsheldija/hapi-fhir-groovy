package com.gjergjsheldija.util;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link BaseFhirR4ForTesting} with security on and fake tokens from {@link TestTokenConfig}.
 * Subclasses must not add context configuration, so they all share one cached context.
 */
@TestPropertySource(properties = {
	"hapi.fhir.security.enabled=true",
	// as in production application.yaml
	"hapi.fhir.custom-provider-classes=com.gjergjsheldija.scripting.ScriptingProvider",
	// own H2 database: this context is cached alongside the default BaseFhirR4ForTesting one
	"spring.datasource.url=jdbc:h2:mem:security-r4"})
@Import(TestTokenConfig.class)
public class SecurityEnabledFhirR4ForTesting extends BaseFhirR4ForTesting {
}
