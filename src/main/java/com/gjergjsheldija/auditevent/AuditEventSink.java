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

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditEventSink;
import org.hl7.fhir.r4.model.AuditEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuditEventSink implements IBalpAuditEventSink {

	@Autowired
	private DaoRegistry myDaoRegistry;

	@Override
	public void recordAuditEvent(AuditEvent theAuditEvent) {
		myDaoRegistry.getResourceDao(AuditEvent.class).create(theAuditEvent);
	}
}