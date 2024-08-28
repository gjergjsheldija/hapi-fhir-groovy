/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-26
 */

package com.clinomic.terminology;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.annotation.ChildOrder;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.IRestfulServerDefaults;
import ca.uhn.fhir.rest.server.RestfulServer;
import jakarta.servlet.http.HttpServletRequest;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;

import java.util.List;
import java.util.stream.Collectors;

@ResourceDef(name = "TerminologyCapabilities", profile = "http://hl7.org/fhir/StructureDefinition/TerminologyCapabilities")
@ChildOrder(names = {"url", "version", "name", "title", "status", "experimental", "date", "publisher", "contact", "description", "useContext", "jurisdiction", "purpose", "copyright", "kind", "software", "implementation", "lockedDate", "codeSystem", "expansion", "codeSearch", "validateCode", "translation", "closure"})
public class TerminologyCapabilities extends org.hl7.fhir.r4.model.TerminologyCapabilities implements IBaseConformance {

	private static final long serialVersionUID = 1L;

	private RestfulServer myServer;
	private HttpServletRequest requestDetails;

	public TerminologyCapabilities withDefaults(IRestfulServerDefaults myServer, HttpServletRequest requestDetails) {

		this.myServer = (RestfulServer) myServer;
		this.requestDetails = requestDetails;

		setName(this.myServer.getServerName());
		setStatus(PublicationStatus.DRAFT);
		setTitle(this.myServer.getServletInfo());
		setVersion(this.myServer.getServerVersion());
		setCodeSystem();
		return this;
	}

	private void setCodeSystem() {
		FhirContext fhirContext = this.myServer.getFhirContext();
		String serverBase = this.myServer.getServerAddressStrategy().determineServerBase(null, this.requestDetails);
		IGenericClient client = fhirContext.newRestfulGenericClient(serverBase);

		Bundle results = client
			.search()
			.forResource(CodeSystem.class)
			.returnBundle(Bundle.class)
			.execute();

		List<TerminologyCapabilitiesCodeSystemComponent> components = results.getEntry().stream()
			.map(entry -> {
				CodeSystem codeSystem = (CodeSystem) entry.getResource();
				TerminologyCapabilitiesCodeSystemComponent component = new TerminologyCapabilitiesCodeSystemComponent();
				component.setUri(codeSystem.getUrl());
				component.addVersion().setCode(codeSystem.getVersion());
				return component;
			})
			.collect(Collectors.toList());


		setCodeSystem(components);
	}

}