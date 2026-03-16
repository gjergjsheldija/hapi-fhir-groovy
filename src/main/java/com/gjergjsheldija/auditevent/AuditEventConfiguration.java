/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-06-10
 */

package com.gjergjsheldija.auditevent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditEventConfiguration {

	@Bean
	public AuditEventSink auditEventSink() {
		return new AuditEventSink();
	}


	@Bean
	public AuditEventContextService auditEventContextService() {
		return new AuditEventContextService();
	}

	@Value("${hapi.fhir.audit_event}")
	private Boolean auditEvent;

	@Bean(name = "auditEventStatus")
	public Boolean auditEventStatus() {
		return auditEvent;
	}

}