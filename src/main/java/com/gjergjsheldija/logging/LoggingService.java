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

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingService {
	private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);

	public void logMessage(ServletRequestDetails theRequestDetails) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		MessageBuilder.LoggedLine line = MessageBuilder.buildMessage(theRequestDetails.getServletRequest(), theRequestDetails);
		logger.info(mapper.writeValueAsString(line));
	}

	public void logMessage(String resourceType, String message, String resourceName) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		MessageBuilder.LoggedLine line = MessageBuilder.buildMessage(resourceType, message, resourceName, null, null);
		logger.info(mapper.writeValueAsString(line));
	}

	private static final class MessageBuilder {
		public static LoggedLine buildMessage(HttpServletRequest myRequest, RequestDetails myRequestDetails) {
			LoggedLine line = new LoggedLine();

			line.setOperationType(myRequestDetails.getRestOperationType() != null ? myRequestDetails.getRestOperationType().getCode() : "");

			if (myRequestDetails.getRestOperationType() != null) {
				switch (myRequestDetails.getRestOperationType()) {
					case EXTENDED_OPERATION_INSTANCE:
					case EXTENDED_OPERATION_SERVER:
					case EXTENDED_OPERATION_TYPE:
						line.setOperationName(myRequestDetails.getOperation());
						break;
					default:
						line.setOperationName("");
				}
			} else {
				line.setOperationName("");
			}

			if (myRequestDetails.getId() != null) {
				line.setIdOrResourceName(myRequestDetails.getId().getValue());
			} else {
				line.setIdOrResourceName(myRequestDetails.getResourceName() != null ? myRequestDetails.getResourceName() : "");
			}

			String reqpath = myRequestDetails.getCompleteUrl();
         int start = reqpath.indexOf("?");
         if (start >= 0) line.setRequestParameters(reqpath.substring(start));

			String contentType;

			contentType = myRequest.getContentType();
			if (StringUtils.isNotBlank(contentType)) {
				int colonIndex = contentType.indexOf(59);
				if (colonIndex != -1) {
					contentType = contentType.substring(0, colonIndex);
				}

				contentType = contentType.trim();
				EncodingEnum encodingEnum = EncodingEnum.forContentType(contentType);
				if (encodingEnum != null) {
					byte[] requestContents = "".getBytes();
					requestContents = myRequestDetails.getRequestContentsIfLoaded();
					line.setRequestBodyFhir(createStringSafely(requestContents));
				}
			} else {
				line.setRequestBodyFhir("");
			}

			return line;
		}

		private static String createStringSafely(byte[] byteArray) {
			if (byteArray == null) {
				return "";
			} else {
				return new String(byteArray, Constants.CHARSET_UTF8);
			}
		}

		public static LoggedLine buildMessage(String type, String message, String resourceName, String operationName, String requestParameters) {
			LoggedLine line = new LoggedLine();
			line.setOperationType(type);
			line.setOperationName(operationName);
			line.setIdOrResourceName(resourceName);
			line.setRequestBodyFhir(message);
			line.setRequestParameters(requestParameters);
			return line;
		}

		private static final class LoggedLine {
			String operationType;
			String operationName;
			String idOrResourceName;
			String requestParameters;
			String requestBodyFhir;

			public String getOperationType() {
				return operationType;
			}

			public void setOperationType(String operationType) {
				this.operationType = operationType;
			}

			public String getOperationName() {
				return operationName;
			}

			public void setOperationName(String operationName) {
				this.operationName = operationName;
			}

			public String getIdOrResourceName() {
				return idOrResourceName;
			}

			public void setIdOrResourceName(String idOrResourceName) {
				this.idOrResourceName = idOrResourceName;
			}

			public String getRequestParameters() {
				return requestParameters;
			}

			public void setRequestParameters(String requestParameters) {
				this.requestParameters = requestParameters;
			}

			public void setRequestBodyFhir(String requestBodyFhir) {
				this.requestBodyFhir = requestBodyFhir;
			}

			public String getRequestBodyFhir() {
				return requestBodyFhir;
			}

		}
	}
}