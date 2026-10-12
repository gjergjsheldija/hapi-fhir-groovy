package com.gjergjsheldija.scripting;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.gjergjsheldija.configuration.Configuration;
import com.gjergjsheldija.security.AdminScopeGuard;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Configuration resources hold Groovy scripts that run with full server rights (and settings that change server
 * behaviour), so with security on only admins may create, update or delete them - REST or transaction alike.
 */
@Interceptor
public class ScriptingSecurityInterceptor {

	private static final String WHAT = "Writing Configuration resources";

	private final AdminScopeGuard adminScopeGuard;

	public ScriptingSecurityInterceptor(AdminScopeGuard adminScopeGuard) {
		this.adminScopeGuard = adminScopeGuard;
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void onCreate(IBaseResource theResource, RequestDetails theRequestDetails) {
		if (theResource instanceof Configuration) {
			adminScopeGuard.requireAdmin(theRequestDetails, WHAT);
		}
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
	public void onUpdate(IBaseResource theOldResource, IBaseResource theNewResource, RequestDetails theRequestDetails) {
		if (theNewResource instanceof Configuration) {
			adminScopeGuard.requireAdmin(theRequestDetails, WHAT);
		}
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_DELETED)
	public void onDelete(IBaseResource theResource, RequestDetails theRequestDetails) {
		if (theResource instanceof Configuration) {
			adminScopeGuard.requireAdmin(theRequestDetails, WHAT);
		}
	}
}
