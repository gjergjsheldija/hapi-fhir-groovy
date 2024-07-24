/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen 
 * All rights reserved.
 *
 * @author Andrey Zagariya <azagariya@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-07-24
 */

package com.clinomic.customoperation;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ObservationCustomOperationProvider {

	private static final Logger logger = LoggerFactory.getLogger(ObservationCustomOperationProvider.class);

	@Autowired
	private IFhirResourceDaoObservation<Observation> observationDao;

	private final ExecutorService executorService;

	private static final int SECONDS = 60;

	public ObservationCustomOperationProvider() {
		int numberOfThreads = Runtime.getRuntime().availableProcessors();
		this.executorService = Executors.newFixedThreadPool(numberOfThreads * 2);
	}

	@Operation(name = "$group-by-interval", idempotent = true, type = Observation.class)
	public Bundle groupByInterval(@OperationParam(name = "encounter") ReferenceParam encounter,
			@OperationParam(name = "code") TokenParam code, @OperationParam(name = "startDate") DateParam startDate,
			@OperationParam(name = "endDate") DateParam endDate,
			@OperationParam(name = "interval") NumberParam interval, RequestDetails requestDetails) {

		LocalDateTime startDateTime = startDate.getValue().toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
		LocalDateTime endDateTime = endDate.getValue().toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
		int intervalMinutes = interval.getValue().intValue();

		List<CompletableFuture<List<Observation>>> futures = new ArrayList<>();

		while (startDateTime.isBefore(endDateTime)) {
			LocalDateTime intervalEndDateTime = startDateTime.plusMinutes(intervalMinutes);
			String startDateTimeStr = startDateTime.toInstant(ZoneOffset.UTC).toString();
			String intervalEndDateTimeStr = intervalEndDateTime.toInstant(ZoneOffset.UTC).toString();

			DateRangeParam dateRange = new DateRangeParam(
					new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, startDateTimeStr),
					new DateParam(ParamPrefixEnum.LESSTHAN, intervalEndDateTimeStr));

			CompletableFuture<List<Observation>> future = CompletableFuture.supplyAsync(() -> {
				SearchParameterMap params = new SearchParameterMap();
				params.add(Observation.SP_ENCOUNTER, encounter);
				params.add(Observation.SP_CODE, code);
				params.add(Observation.SP_DATE, dateRange);
				params.setCount(intervalMinutes * SECONDS);

				IBundleProvider bundleProvider;
				try {
					bundleProvider = observationDao.search(params, requestDetails);
				} catch (Exception e) {
					logger.error(
							"Error occurred while searching for observations with parameters: encounter={}, code={}, dateRange={}. Error: {}",
							encounter, code, dateRange, e.getMessage(), e);
					return List.of();
				}

				List<IBaseResource> resources = bundleProvider.getResources(0, Integer.MAX_VALUE);
				return resources.stream().filter(Observation.class::isInstance).map(Observation.class::cast)
						.max((o1, o2) -> o1.getEffectiveDateTimeType().getValue()
								.compareTo(o2.getEffectiveDateTimeType().getValue()))
						.map(List::of).orElseGet(List::of);
			}, executorService);

			futures.add(future);

			startDateTime = intervalEndDateTime;
		}

		List<Observation> allObservations = futures.stream().map(CompletableFuture::join).flatMap(List::stream)
				.toList();

		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		allObservations.forEach(observation -> {
			Observation ob = new Observation();
			ob.setId(observation.getId());
			ob.setEffective(observation.getEffective());
			ob.setValue(observation.getValue());
			bundle.addEntry().setResource(ob);
		});
		bundle.setTotal(bundle.getEntry().size());

		return bundle;
	}
}
