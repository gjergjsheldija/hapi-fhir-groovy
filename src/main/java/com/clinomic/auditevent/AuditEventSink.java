/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-06-10
 */
package com.clinomic.auditevent;

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