/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-07-01
 */

package com.gjergjsheldija.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomLoggingConfiguration {

	@Bean
	public LoggingService loggingService() {
		return new LoggingService();
	}

	@Value("${hapi.fhir.log_request_body}")
	private Boolean logRequestBody;

	@Bean(name = "logRequestBodyStatus")
	public Boolean logRequestBodyStatus() {
		return logRequestBody;
	}
}
