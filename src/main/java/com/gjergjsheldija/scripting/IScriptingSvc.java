/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-07
 */

package com.gjergjsheldija.scripting;

import java.util.List;

public interface IScriptingSvc {
	void loadCustomScript(String interceptorName, String interceptorBody);

	void unloadCustomScript(String interceptorName);

	List<Object> listCustomScripts();

}
