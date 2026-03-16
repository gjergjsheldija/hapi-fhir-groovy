package com.gjergjsheldija.terminology;

import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.TerminologyCapabilities;

import ca.uhn.fhir.model.api.annotation.ResourceDef;

@ResourceDef(name = "TerminologyCapabilities", profile = "http://hl7.org/fhir/StructureDefinition/TerminologyCapabilities")
public class TerminologyCapabilitiesConformance extends TerminologyCapabilities implements IBaseConformance {

	private static final long serialVersionUID = -8505437973314964323L;
}
