/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-07
 */

package com.clinomic.scripting;

import java.util.List;

public interface IScriptingSvc {
	void loadInterceptor(String interceptorName, String interceptorBody);

	void unloadInterceptor(String interceptorName);

	void unloadInterceptors();

	List<Object> listCustomInterceptors();


}
