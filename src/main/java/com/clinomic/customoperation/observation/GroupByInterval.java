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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.NumberParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TemporalType;

@Component
public class GroupByInterval {

	private static final Logger logger = LoggerFactory.getLogger(GroupByInterval.class);

	private static final String _ID = "_id";

	private static final String URL_INTERVAL_STARTING_POINT = "https://fhir.mona.icu/StructureDefinition/intervalStartingPoint";

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private IFhirResourceDaoObservation<Observation> myObservationDao;

    @Operation(name = "$group-by-interval", idempotent = true, type = Observation.class)
    public Bundle groupByInterval(
            @Description(shortDefinition = "The encounter to search for", 
            example = "Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0") 
            @OperationParam(name = "encounter") ReferenceParam encounter,
            @Description(shortDefinition = "The system and the code of the observation", 
            example = "http://loinc.org|8867-4") 
            @OperationParam(name = "code") TokenParam code,
            @Description(shortDefinition = "The start datetime to search from", 
            example = "'2024-07-24T01:00:00Z'") 
            @OperationParam(name = "startDateTime") DateParam startDateTime,
            @Description(shortDefinition = "The end datetime to search to", 
            example = "'2024-07-24T03:00:00Z'") 
            @OperationParam(name = "endDateTime") DateParam endDateTime,
            @Description(shortDefinition = "The interval to group by in seconds") 
            @OperationParam(name = "interval") NumberParam interval,
            RequestDetails theRequest) {

		ZonedDateTime startZonedDateTime = parseZonedDateTime(startDateTime.getValueAsString());
		ZonedDateTime endZonedDateTime = parseZonedDateTime(endDateTime.getValueAsString());

		ZoneId clientZoneId = startZonedDateTime.getZone();

		Instant startInstant = startZonedDateTime.toInstant();
		Instant endInstant = endZonedDateTime.toInstant();

		List<Object[]> queryResults = executeAggregationQuery(interval.getValue().longValue(), encounter.getIdPart(),
				Date.from(startInstant), Date.from(endInstant), code.getSystem(), code.getValue());
		logger.debug("totalAggregationQueryResults={}", queryResults.size());

		TokenOrListParam uuidParams = new TokenOrListParam();

		Map<String, String> observationIntervalMap = new HashMap<>();

		for (Object[] result : queryResults) {
			String observationUuid = result[1].toString();
			Instant intervalInstant = ((Date) result[0]).toInstant();
			String intervalGroup = intervalInstant.toString();
			observationIntervalMap.put(observationUuid, intervalGroup);
			uuidParams.addOr(new TokenParam(observationUuid));
			logger.debug("observationUuid={}, intervalGroup={}", observationUuid, intervalGroup);
		}

		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);

		if (!uuidParams.getValuesAsQueryTokens().isEmpty()) {
			SearchParameterMap searchCriteria = new SearchParameterMap();
			searchCriteria.add(_ID, uuidParams);
			searchCriteria.setCount(uuidParams.getValuesAsQueryTokens().size());

			List<Observation> allObservations = myObservationDao.searchForResources(searchCriteria, theRequest);

			allObservations.forEach(obs -> {
				String intervalGroup = observationIntervalMap.get(obs.getIdElement().getIdPart());
				Instant intervalInstant = Instant.parse(intervalGroup);
				ZonedDateTime clientTime = intervalInstant.atZone(clientZoneId);
				String clientFormattedTime = clientTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
				obs.getMeta()
						.addExtension(new Extension(URL_INTERVAL_STARTING_POINT, new StringType(clientFormattedTime)));
				bundle.addEntry().setResource(obs);
			});
		}

		bundle.setTotal(bundle.getEntry().size());

		return bundle;
	}

	ZonedDateTime parseZonedDateTime(String dateTimeStr) {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

		try {
			return ZonedDateTime.parse(dateTimeStr, formatter);
		} catch (Exception e) {
			return ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("UTC")));
		}
	}

	/**
	 * Aggregates by the datetime interval in seconds
	 * 
	 * @return Object[] of interval_group, observation_uuid
	 */
	@SuppressWarnings("unchecked")
	private List<Object[]> executeAggregationQuery(long interval, String encounterId, Date startDateTime, Date endDateTime, String system, String code) {
        String sql = "WITH interval_param AS (" +
                     "    SELECT :interval AS interval" +
                     "), encounter_res_id AS (" +
                     "    SELECT res_id FROM public.hfj_resource WHERE res_type = 'Encounter' AND fhir_id = :encounterId " +
                     "), calculated_dates AS (" +
                     "    SELECT" +
                     "        sd.sp_value_high AS date," +
                     "        hr.fhir_id AS observation_uuid," +
                     "        FLOOR(EXTRACT(EPOCH FROM date_trunc('second', sd.sp_value_high) - date_trunc('day', sd.sp_value_high)) / interval_param.interval) * interval_param.interval AS plain_date_interval" +
                     "    FROM" +
                     "        public.hfj_spidx_date sd" +
                     "    JOIN" +
                     "        public.hfj_res_link rl ON sd.res_id = rl.src_resource_id" +
                     "    JOIN" +
                     "        public.hfj_resource hr ON sd.res_id = hr.res_id" +
                     "    JOIN" +
                     "        public.hfj_spidx_token st ON sd.res_id = st.res_id" +
                     "    JOIN" +
                     "        interval_param ON TRUE" +
                     "    WHERE" +
                     "        rl.target_resource_id = (SELECT res_id FROM encounter_res_id)" +
                     "        AND rl.src_path = 'Observation.encounter'" +
                     "        AND sd.sp_value_high >= :startDateTime" +
                     "        AND sd.sp_value_low <= :endDateTime" +
                     "        AND st.sp_name = 'code'" +
                     "        AND st.sp_system = :system" +
                     "        AND st.sp_value = :code" +
                     "), grouped_dates AS (" +
                     "    SELECT" +
                     "        plain_date_interval," +
                     "        MAX(date) AS max_date," +
                     "        observation_uuid," +
                     "        ROW_NUMBER() OVER (PARTITION BY plain_date_interval ORDER BY MAX(date) DESC) AS rn" +
                     "    FROM" +
                     "        calculated_dates" +
                     "    GROUP BY" +
                     "        plain_date_interval, observation_uuid" +
                     ") " +
                     "SELECT" +
                     "    date_trunc('day', max_date) + plain_date_interval * INTERVAL '1 second' AS interval_group," +
                     "    observation_uuid " +
                     "FROM" +
                     "    grouped_dates " +
                     "WHERE" +
                     "    rn = 1 " +
                     "ORDER BY" +
                     "    interval_group";

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("interval", interval);
		query.setParameter("encounterId", encounterId);
		query.setParameter("startDateTime", startDateTime, TemporalType.TIMESTAMP);
		query.setParameter("endDateTime", endDateTime, TemporalType.TIMESTAMP);
		query.setParameter("system", system);
		query.setParameter("code", code);

		return query.getResultList();
	}
}
