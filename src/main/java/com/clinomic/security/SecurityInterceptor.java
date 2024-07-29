/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Leipzig
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Leipzig
 * @license All rights reserved.
 * @since 2024-07-19
 */

package com.clinomic.security;

import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import com.clinomic.security.token.AccessTokenValidation;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Interceptor
public class SecurityInterceptor extends AuthorizationInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(SecurityInterceptor.class);
	private static final String DEFAULT_CLIENT_ID = "--";

	@Value("${hapi.fhir.security.enabled}")
	private boolean securityEnabled;

	@Autowired
	private AccessTokenValidation myAccessTokenValidation;

	@Override
	public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {

		logger.info("Accessing Resource : " + theRequestDetails.getRequestPath());

		// in case of security not enabled, we return ok for everything
		if (securityEnabled == false) {
			logger.info("No security rules enabled, permission to access everything.");
			return createAdminRules();
		}

		String token = theRequestDetails.getHeader(HttpHeaders.AUTHORIZATION);
		if (token == null) {
			logger.error("No authentication mechanism provided, missing Authentication headers");
			return buildErrorRules("No authentication mechanism provided, missing Authentication headers");
		}

		if (!token.isEmpty()) {
			List<IAuthRule> rules = buildSmartRules(token);
			return rules;
		}

		theRequestDetails.setAttribute("clientId", DEFAULT_CLIENT_ID);
		// else allow only metadata to be accessed
		return new RuleBuilder().allow().metadata().andThen().denyAll().build();
	}

	// specific rules for SMART
	public List<IAuthRule> buildSmartRules(String token) {

		try {
			myAccessTokenValidation.validateToken(token);
		} catch (Exception e) {
			logger.info("Error validating access token: " + e.getMessage());
			return buildErrorRules("You don't have permission to access this resource");
		}

		return createAdminRules();
	}

	public List<IAuthRule> createAdminRules() {
		return new RuleBuilder().allowAll().build();
	}

	public List<IAuthRule> buildErrorRules(String message) {
		return new RuleBuilder()
			.allow().metadata().andThen()
			.denyAll(message)
			.build();
	}
}
