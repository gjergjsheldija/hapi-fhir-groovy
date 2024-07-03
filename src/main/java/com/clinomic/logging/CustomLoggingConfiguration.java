/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Leipzig
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Leipzig
 * @license All rights reserved.
 * @since 2024-07-01
 */

package com.clinomic.logging;

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
