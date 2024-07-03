/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Leipzig
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Leipzig
 * @license All rights reserved.
 * @since 2024-06-10
 */

package com.clinomic.auditevent;

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