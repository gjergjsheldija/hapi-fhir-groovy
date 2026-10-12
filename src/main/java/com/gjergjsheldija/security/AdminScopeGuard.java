package com.gjergjsheldija.security;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * With security on, only REST callers holding the admin scope ({@code hapi.fhir.apps.admin-scope}) pass;
 * internal (system) calls are trusted. With security off everything passes (open server by design).
 */
public class AdminScopeGuard {

	private final boolean securityEnabled;
	private final ObjectProvider<AccessTokenValidation> tokenValidation;
	private final String adminScope;

	public AdminScopeGuard(boolean securityEnabled, ObjectProvider<AccessTokenValidation> tokenValidation, String adminScope) {
		if (adminScope == null || adminScope.isBlank()) {
			throw new IllegalArgumentException("hapi.fhir.apps.admin-scope must not be blank");
		}
		this.securityEnabled = securityEnabled;
		this.tokenValidation = tokenValidation;
		this.adminScope = adminScope;
	}

	/**
	 * @param what what the caller is doing, for the error message: "&lt;what&gt; requires scope &lt;scope&gt;"
	 */
	public void requireAdmin(RequestDetails theRequestDetails, String what) {
		if (!securityEnabled || theRequestDetails == null || theRequestDetails instanceof SystemRequestDetails) {
			return;
		}
		ForbiddenOperationException forbidden = new ForbiddenOperationException(what + " requires scope " + adminScope);
		String authorization = theRequestDetails.getHeader(Constants.HEADER_AUTHORIZATION);
		AccessTokenValidation validator = tokenValidation == null ? null : tokenValidation.getIfAvailable();
		if (validator == null || authorization == null || !authorization.startsWith(Constants.HEADER_AUTHORIZATION_VALPREFIX_BEARER)) {
			throw forbidden;
		}
		Jwt jwt;
		try {
			jwt = validator.validateToken(authorization);
		} catch (AuthenticationException e) {
			throw forbidden;
		}
		if (!hasScope(jwt)) {
			throw forbidden;
		}
	}

	private boolean hasScope(Jwt jwt) {
		Object scope = jwt.getClaim("scope");
		if (scope instanceof String s && List.of(s.split("\\s+")).contains(adminScope)) {
			return true;
		}
		Object scp = jwt.getClaim("scp");
		if (scp instanceof String s && List.of(s.split("\\s+")).contains(adminScope)) {
			return true;
		}
		if (scp instanceof Collection<?> c && c.contains(adminScope)) {
			return true;
		}
		return jwt.getClaim("realm_access") instanceof Map<?, ?> realm
			&& realm.get("roles") instanceof Collection<?> roles && roles.contains(adminScope);
	}
}
