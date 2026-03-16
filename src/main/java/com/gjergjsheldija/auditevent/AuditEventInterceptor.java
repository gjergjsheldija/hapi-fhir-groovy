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

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.IPreResourceShowDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.storage.interceptor.balp.BalpAuditCaptureInterceptor;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditContextServices;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditEventSink;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;


@Component
@Interceptor
public class AuditEventInterceptor {

	private static final Logger ourLog = LoggerFactory.getLogger(AuditEventInterceptor.class);

	@Autowired
	AuditEventSink theAuditEventSink;

	@Autowired
	AuditEventContextService theAuditEventContextServices;

	Boolean auditEventStatus;

	BalpAuditCaptureInterceptor balpAuditCaptureInterceptor;

	public AuditEventInterceptor(@NotNull IBalpAuditEventSink theAuditEventSink,
										  @NotNull IBalpAuditContextServices theAuditEventContextServices,
										  @Qualifier("auditEventStatus") Boolean auditEventStatus) {
		this.auditEventStatus = auditEventStatus;
		if (auditEventStatus == true) {
			this.balpAuditCaptureInterceptor = new BalpAuditCaptureInterceptor(theAuditEventSink, theAuditEventContextServices);
			ourLog.info("BALP AuditEvent enabled");
		}
	}

	@Hook(Pointcut.STORAGE_PRESHOW_RESOURCES)
	void hookStoragePreShowResources(IPreResourceShowDetails theDetails, ServletRequestDetails theRequestDetails) {
		// we don't want it to be called since we are
		// ignoring READ and VREAD
	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
	public void hookStoragePrecommitResourceCreated(IBaseResource theResource, ServletRequestDetails theRequestDetails) {
		if (auditEventStatus == true)
			this.balpAuditCaptureInterceptor.hookStoragePrecommitResourceCreated(theResource, theRequestDetails);

	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_DELETED)
	public void hookStoragePrecommitResourceDeleted(IBaseResource theResource, ServletRequestDetails theRequestDetails) {
		if (auditEventStatus == true)
			this.balpAuditCaptureInterceptor.hookStoragePrecommitResourceDeleted(theResource, theRequestDetails);

	}

	@Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED)
	public void hookStoragePrecommitResourceUpdated(IBaseResource theOldResource, IBaseResource theResource, ServletRequestDetails theRequestDetails) {
		if (auditEventStatus == true)
			this.balpAuditCaptureInterceptor.hookStoragePrecommitResourceUpdated(theOldResource, theResource, theRequestDetails);

	}
}
