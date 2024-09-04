/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Andrey Zagariya <azagariya@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-27
 */

package com.clinomic.customoperation.observation;

import java.io.IOException;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Interceptor
public class CustomResponseInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(CustomResponseInterceptor.class);

	private static final String RESOURCE = "resource";

	private static final String PART = "part";

	private static final String NAME = "name";

	private static final String PARAMETER = "parameter";

	private final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private DaoRegistry daoRegistry;

	private IParser fhirParser;

	@PostConstruct
	public void init() {
		fhirParser = daoRegistry.getSystemDao().getContext().newJsonParser();
	}

	@Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
	public boolean customizeGroupByIntervalResponse(RequestDetails theRequestDetails,
			HttpServletResponse theServletResponse, IBaseResource theResource) throws IOException {
		boolean isNormalSerialization = true;

		if ("$group-by-interval".equals(theRequestDetails.getOperation())
				&& theResource instanceof Parameters parameters) {

			String strParameters = fhirParser.encodeResourceToString(parameters);
			String customResponse = convertToCustomJson(strParameters);
			theServletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
			theServletResponse.getWriter().write(customResponse);
			isNormalSerialization = false;
		}

		return isNormalSerialization;
	}

	private String convertToCustomJson(String parameters) {
		try {
			JsonNode rootNode = mapper.readTree(parameters);
			JsonNode parametersNode = rootNode.path(PARAMETER);
			ObjectNode outputNode = mapper.createObjectNode();

			parametersNode.forEach(param -> {
				String interval = param.path(NAME).asText();
				ObjectNode intervalNode = outputNode.withObject(interval);

				param.path(PART).forEach(part -> {
					String code = part.path(NAME).asText();
					ObjectNode codeNode = intervalNode.withObject(code);

					part.path(PART).forEach(observationPart -> {
						String observationTime = observationPart.path(NAME).asText();
						JsonNode observationResource = observationPart.path(RESOURCE);
						codeNode.set(observationTime, observationResource);
					});
				});
			});

			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(outputNode);

		} catch (Exception e) {
			logger.error("Error converting JSON", e);
			return "{}";
		}
	}
}
