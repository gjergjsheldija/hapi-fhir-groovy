package com.gjergjsheldija.scripting;

import ca.uhn.fhir.interceptor.api.IInterceptorService;
import com.gjergjsheldija.security.AdminScopeGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScriptingBean {

	@Bean
	public ScriptingSecurityInterceptor scriptingSecurityInterceptor(AdminScopeGuard adminScopeGuard, IInterceptorService interceptorService) {
		ScriptingSecurityInterceptor interceptor = new ScriptingSecurityInterceptor(adminScopeGuard);
		// The JPA interceptor service sees every DAO write, including transaction entries.
		interceptorService.registerInterceptor(interceptor);
		return interceptor;
	}
}
