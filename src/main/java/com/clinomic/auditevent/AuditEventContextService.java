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

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.storage.interceptor.balp.IBalpAuditContextServices;
import jakarta.annotation.Nonnull;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class AuditEventContextService implements IBalpAuditContextServices {

	// once we have security in place we need to implement
	// getAgentClientWho
	// getAgentUserWho

	/**
	 * Here we are just hard-coding a simple display name. When security will be implemented
	 * we will use the actual identity of the requesting client.
	 */
	@Nonnull
	@Override
	public Reference getAgentClientWho(RequestDetails theRequestDetails) {
		Reference client = new Reference();
		client.setDisplay("");
		return client;
	}

	/**
	 * Here we are just hard-coding a simple display name. When security will be implemented
	 * we will use the actual identity of the requesting user.
	 */
	@Nonnull
	@Override
	public Reference getAgentUserWho(RequestDetails theRequestDetails) {
		Reference user = new Reference();
		user.setDisplay("");
		return user;
	}
}