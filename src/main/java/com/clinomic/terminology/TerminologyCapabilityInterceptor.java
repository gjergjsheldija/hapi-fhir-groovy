/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Andrey Zagariya <azagariya@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-13
 */

package com.clinomic.terminology;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import org.hl7.fhir.instance.model.api.IBaseConformance;


@Interceptor
public class TerminologyCapabilityInterceptor {

	/**
	 * set the order to -10 to avoid the ResponseHighlighterInterceptor interfering with the
	 * response generation.
	 *
	 * @param conformance
	 * @return IBaseConformance conformance
	 */
	@Hook(value = Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED, order = -10)
	public IBaseConformance hook(IBaseConformance conformance) {

		return conformance;
	}
}
