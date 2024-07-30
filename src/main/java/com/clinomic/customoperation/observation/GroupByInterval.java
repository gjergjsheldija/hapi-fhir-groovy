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

package com.clinomic.customoperation.observation;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class GroupByInterval {

	private static final Logger logger = LoggerFactory.getLogger(GroupByInterval.class);

	private static final ZoneId GMT_ZONE = ZoneId.of("GMT");

	@Autowired
	private IFhirResourceDaoObservation<Observation> myObservationDao;

	@Operation(name = "$group-by-interval", idempotent = true, type = Observation.class)
	public Bundle groupByInterval(
		@Description(shortDefinition = "The encounter to search for")
		@OperationParam(name = "encounter") ReferenceParam encounter,

		@Description(shortDefinition = "The code of the Observation")
		@OperationParam(name = "code") TokenParam code,

		@Description(shortDefinition = "The start date from when to search from")
		@OperationParam(name = "startDate") DateParam startDate,

		@Description(shortDefinition = "The end date from when to search to")
		@OperationParam(name = "endDate") DateParam endDate,

		@Description(shortDefinition = "The interval in minutes")
		@OperationParam(name = "interval") NumberParam interval) {

		SearchParameterMap searchCriteria = new SearchParameterMap();
		searchCriteria.add(Observation.SP_ENCOUNTER, encounter);
		searchCriteria.add(Observation.SP_CODE, code);
		searchCriteria.add(Observation.SP_DATE, new DateRangeParam(startDate, endDate));
		searchCriteria.setLoadSynchronous(false);

		IBundleProvider results = myObservationDao.search(searchCriteria);
		List<LocalDateTime> dateIntervals = generateDateIntervals(startDate, endDate, interval);

		Bundle filteredBundle = new Bundle();
		filteredBundle.setType(Bundle.BundleType.SEARCHSET);

		List<Bundle.BundleEntryComponent> filteredEntries = processResults(results, dateIntervals);

		filteredBundle.setEntry(filteredEntries);
		filteredBundle.setTotal(filteredEntries.size());

		return filteredBundle;
	}

	public List<Bundle.BundleEntryComponent> processResults(IBundleProvider results, List<LocalDateTime> dateIntervals) {
		int pageSize = 1000;
		Map<LocalDateTime, Observation> latestObservationsByInterval = new ConcurrentHashMap<>();

		for (int fromIndex = 0; ; fromIndex += pageSize) {
			List<IBaseResource> resourcesPage = results.getResources(fromIndex, fromIndex + pageSize);
			if (resourcesPage.isEmpty()) break;

			resourcesPage.parallelStream()
				.filter(resource -> resource instanceof Observation)
				.map(resource -> (Observation) resource)
				.forEach(obs -> {
					ZonedDateTime effectiveZonedDateTime = obs.getEffectiveDateTimeType().getValue().toInstant().atZone(GMT_ZONE);
					LocalDateTime effectiveDateTime = effectiveZonedDateTime.toLocalDateTime();

					Optional<LocalDateTime> matchingInterval = dateIntervals.stream()
						.filter(interval -> !effectiveDateTime.isBefore(interval) && effectiveDateTime.isBefore(interval.plusMinutes(getIntervalMinutes(dateIntervals))))
						.findFirst();

					matchingInterval.ifPresent(interval ->
						latestObservationsByInterval.compute(interval, (k, existingObs) ->
							existingObs == null || obs.getEffectiveDateTimeType().getValue().after(existingObs.getEffectiveDateTimeType().getValue())
								? obs
								: existingObs
						)
					);
				});
		}

		return latestObservationsByInterval.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> {
				Bundle.BundleEntryComponent bundleEntry = new Bundle.BundleEntryComponent();
				bundleEntry.setResource(entry.getValue());
				return bundleEntry;
			})
			.collect(Collectors.toList());
	}

	private long getIntervalMinutes(List<LocalDateTime> dateIntervals) {
		if (dateIntervals.size() < 2) {
			return 0;
		}
		return Duration.between(dateIntervals.get(0), dateIntervals.get(1)).toMinutes();
	}


	public static List<LocalDateTime> generateDateIntervals(DateParam dateStart, DateParam dateEnd, NumberParam intervalMinutes) {
		LocalDateTime start = dateStart.getValue().toInstant().atZone(GMT_ZONE).toLocalDateTime();
		LocalDateTime end = dateEnd.getValue().toInstant().atZone(GMT_ZONE).toLocalDateTime();
		long intervalInMinutes = intervalMinutes.getValue().longValue();

		List<LocalDateTime> intervals = new ArrayList<>();
		LocalDateTime current = start;

		while (!current.isAfter(end)) {
			intervals.add(current);
			current = current.plusMinutes(intervalInMinutes);
		}

		return intervals;
	}
}