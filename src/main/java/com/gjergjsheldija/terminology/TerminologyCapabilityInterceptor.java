/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-13
 */

package com.gjergjsheldija.terminology;

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
