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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.util.OperationOutcomeUtil;
import ca.uhn.fhir.util.UrlUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpHeaders;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


@Component
@Interceptor
public class CustomLoggingInterceptor {
	public static final String PROCESSING = Constants.OO_INFOSTATUS_PROCESSING;
	private static final Logger ourLog = LoggerFactory.getLogger(CustomLoggingInterceptor.class);
	private Class<?>[] myReturnStackTracesForExceptionTypes;

	@Autowired
	LoggingService loggingService;

	Boolean logRequestBodyStatus;

	public CustomLoggingInterceptor(@NotNull LoggingService loggingService, @Qualifier("logRequestBodyStatus") Boolean logRequestBodyStatus) {
		this.loggingService = loggingService;
		this.logRequestBodyStatus = logRequestBodyStatus;
	}

	/**
	 * Unify the exception logging of HAPI
	 *
	 * @param theRequestDetails
	 * @param theException
	 * @param theServletRequest
	 * @throws ServletException
	 */
	@Hook(Pointcut.SERVER_PRE_PROCESS_OUTGOING_EXCEPTION)
	public BaseServerResponseException preProcessOutgoingException(RequestDetails theRequestDetails, Throwable theException, HttpServletRequest theServletRequest) throws ServletException {
		fillMDC(theRequestDetails, theServletRequest);

		BaseServerResponseException retVal;
		if (theException instanceof DataFormatException) {
			// Wrapping the DataFormatException as an InvalidRequestException so that it gets sent back to the client as a 400 response.
			retVal = new InvalidRequestException(theException);
		} else if (!(theException instanceof BaseServerResponseException)) {
			retVal = new InternalErrorException(theException);
		} else {
			retVal = (BaseServerResponseException) theException;
		}

		if (retVal.getOperationOutcome() == null) {
			retVal.setOperationOutcome(this.createOperationOutcome(theException, theRequestDetails.getServer().getFhirContext()));
		}

		return retVal;
	}

	private void fillMDC(RequestDetails theRequestDetails, HttpServletRequest theServletRequest) {
		MDC.put("uri", theServletRequest.getServletPath());
		MDC.put("method", theServletRequest.getMethod());
		MDC.put("remote_ip", theServletRequest.getRemoteAddr());
		MDC.put("request_id", theRequestDetails.getRequestId());
		MDC.put("host", theServletRequest.getRequestURL().toString());
		MDC.put("user_agent", theRequestDetails.getHeader(HttpHeaders.USER_AGENT));
		MDC.put("status", String.valueOf(((ServletRequestDetails) theRequestDetails).getServletResponse().getStatus()));
		MDC.put("bytes_in", String.valueOf(theServletRequest.getContentLengthLong()));
		MDC.put("tenant_id", theRequestDetails.getTenantId());
		MDC.put("compartment", theRequestDetails.getCompartmentName());
		MDC.put("bytes_out", "0");
		MDC.put("latency_human", String.valueOf(theRequestDetails.getRequestStopwatch()));
		MDC.put("latency", String.valueOf(theRequestDetails.getRequestStopwatch().getMillis()));
		MDC.put("client_id", theRequestDetails.getAttribute("clientId") != null ? theRequestDetails.getAttribute("clientId").toString() : "--");
		MDC.put("operation_type", getOperationType(theRequestDetails));
		MDC.put("operation_name", getOperationName(theRequestDetails));
		MDC.put("id_resource_name", getResourceName(theRequestDetails));
		MDC.put("request_parameters", getRequestParameters(theRequestDetails));
		MDC.put("request_body", getRequestBodyFhir(theRequestDetails, theServletRequest));
	}

	private String getOperationType(RequestDetails theRequestDetails) {
		if (theRequestDetails.getRestOperationType() != null) {
			return theRequestDetails.getRestOperationType().getCode();
		}
		return "";
	}

	private String getOperationName(RequestDetails theRequestDetails) {
		if (theRequestDetails.getRestOperationType() != null) {
			switch (theRequestDetails.getRestOperationType()) {
				case EXTENDED_OPERATION_INSTANCE:
				case EXTENDED_OPERATION_SERVER:
				case EXTENDED_OPERATION_TYPE:
					return theRequestDetails.getOperation();
				default:
					return "";
			}
		}
		return "";
	}

	private String getResourceName(RequestDetails theRequestDetails) {
		if (theRequestDetails.getId() != null) {
			return theRequestDetails.getId().getValue();
		}
		if (theRequestDetails.getResourceName() != null) {
			return theRequestDetails.getResourceName();
		}
		return "";
	}

	private String getRequestParameters(RequestDetails theRequestDetails) {
		StringBuilder b = new StringBuilder();
		for (Map.Entry<String, String[]> next :
			theRequestDetails.getParameters().entrySet()) {
			for (String nextValue : next.getValue()) {
				if (b.length() == 0) {
					b.append('?');
				} else {
					b.append('&');
				}
				b.append(UrlUtil.escapeUrlParam(next.getKey()));
				b.append('=');
				b.append(UrlUtil.escapeUrlParam(nextValue));
			}
		}
		return b.toString();
	}

	private String getRequestBodyFhir(RequestDetails theRequestDetails, HttpServletRequest theServletRequest) {
		if (logRequestBodyStatus == false)
			return "";

		String contentType = theServletRequest.getContentType();
		if (isNotBlank(contentType)) {
			int colonIndex = contentType.indexOf(';');
			if (colonIndex != -1) {
				contentType = contentType.substring(0, colonIndex);
			}
			contentType = contentType.trim();

			EncodingEnum encoding = EncodingEnum.forContentType(contentType);
			if (encoding != null) {
				byte[] requestContents = theRequestDetails.loadRequestContents();
				return new String(requestContents, Constants.CHARSET_UTF8);
			}
		}
		return "";
	}

	@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
	public void logSource() {
		MDC.put("message_source", "REST");
	}

	private IBaseOperationOutcome createOperationOutcome(Throwable theException, FhirContext ctx) throws ServletException {
		IBaseOperationOutcome oo = null;
		if (theException instanceof BaseServerResponseException) {
			oo = ((BaseServerResponseException) theException).getOperationOutcome();
		}

		/*
		 * Generate an OperationOutcome to return, unless the exception throw by the resource provider had one
		 */
		if (oo == null) {
			try {
				oo = OperationOutcomeUtil.newInstance(ctx);

				if (theException instanceof InternalErrorException) {
					ourLog.error("Failure during REST processing", theException);
					populateDetails(ctx, theException, oo);
				} else if (theException instanceof BaseServerResponseException) {
					int statusCode = ((BaseServerResponseException) theException).getStatusCode();

					// No stack traces for non-server internal errors
					if (statusCode < 500) {
						ourLog.warn("Failure during REST processing: {}", theException.toString());
					} else {
						ourLog.warn("Failure during REST processing", theException);
					}

					BaseServerResponseException baseServerResponseException = (BaseServerResponseException) theException;
					populateDetails(ctx, theException, oo);
					if (baseServerResponseException.getAdditionalMessages() != null) {
						for (String next : baseServerResponseException.getAdditionalMessages()) {
							OperationOutcomeUtil.addIssue(ctx, oo, "error", next, null, PROCESSING);
						}
					}
				} else {
					ourLog.error("Failure during REST processing: " + theException.toString(), theException);
					populateDetails(ctx, theException, oo);
				}
			} catch (Exception e1) {
				ourLog.error("Failed to instantiate OperationOutcome resource instance", e1);
				throw new ServletException("Failed to instantiate OperationOutcome resource instance", e1);
			}
		} else {
			ourLog.error("Unknown error during processing", theException);
		}
		return oo;
	}

	private void populateDetails(FhirContext theCtx, Throwable theException, IBaseOperationOutcome theOo) {
		if (this.myReturnStackTracesForExceptionTypes != null) {
			for (Class<?> next : this.myReturnStackTracesForExceptionTypes) {
				if (next.isAssignableFrom(theException.getClass())) {
					String detailsValue = theException.getMessage() + "\n\n" + ExceptionUtils.getStackTrace(theException);
					OperationOutcomeUtil.addIssue(theCtx, theOo, "error", detailsValue, null, PROCESSING);
					return;
				}
			}
		}

		OperationOutcomeUtil.addIssue(theCtx, theOo, "error", theException.getMessage(), null, PROCESSING);
	}

	public CustomLoggingInterceptor setReturnStackTracesForExceptionTypes(Class<?>... theExceptionTypes) {
		myReturnStackTracesForExceptionTypes = theExceptionTypes;
		return this;
	}

	/**
	 * Decorate the successful processing log with additional information
	 *
	 * @param theRequestDetails
	 * @throws JsonProcessingException
	 */
	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
	public void processingCompletedNormally(ServletRequestDetails theRequestDetails) throws JsonProcessingException {
		fillMDC(theRequestDetails, theRequestDetails.getServletRequest());
		loggingService.logMessage(theRequestDetails);

	}

}