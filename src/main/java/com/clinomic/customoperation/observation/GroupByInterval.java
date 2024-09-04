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

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
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

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private IFhirResourceDaoObservation<Observation> myObservationDao;

    @Operation(name = "$group-by-interval", idempotent = true, type = Observation.class)
    public Parameters groupByInterval(
            @Description(shortDefinition = "The encounter to search for", 
            example = "Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0") 
            @OperationParam(name = "encounter") ReferenceParam encounter,
            @Description(shortDefinition = "The multiple systems and codes of the observations", 
            example = "&code=http://loinc.org|8867-4&code=http://snomed.info|55553") 
            @OperationParam(name = "code", min = 0, max = OperationParam.MAX_UNLIMITED) List<TokenParam> codes,
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
		
		String[] systems = codes.stream().map(TokenParam::getSystem).toArray(String[]::new);
		String[] codeValues = codes.stream().map(TokenParam::getValue).toArray(String[]::new);

		List<Object[]> queryResults = executeAggregationQuery(interval.getValue().longValue(), encounter.getIdPart(),
				Date.from(startInstant), Date.from(endInstant), systems, codeValues);

		logger.debug("totalAggregationQueryResults={}", queryResults.size());

		TokenOrListParam uuidParams = new TokenOrListParam();

		Map<String, String> observationIntervalMap = new HashMap<>();
		Map<String, Map<String, String>> observationIntervalMatrix = new HashMap<>();

		for (Object[] result : queryResults) {
			String observationUuid = result[1].toString();
			Instant intervalInstant = ((Date) result[0]).toInstant();
			String intervalGroup = intervalInstant.toString();
			String spSystem = result[2].toString();
			String spCodeValue = result[3].toString();
			String systemValueKey = spSystem + "|" + spCodeValue;

			observationIntervalMatrix.computeIfAbsent(systemValueKey, k -> new HashMap<>())
				.put(intervalGroup,observationUuid);

			observationIntervalMap.put(observationUuid, intervalGroup);
			uuidParams.addOr(new TokenParam(observationUuid));

			logger.debug("systemValueKey={}, observationUuid={}, intervalGroup={}", systemValueKey, observationUuid,
					intervalGroup);
		}

		Parameters parameters = new Parameters();	
		ParametersParameterComponent parameterInterval = new ParametersParameterComponent();
		parameterInterval.setName(String.valueOf(interval.getValue().longValue()));
		parameters.addParameter(parameterInterval);

		if (!uuidParams.getValuesAsQueryTokens().isEmpty()) {
			SearchParameterMap searchCriteria = new SearchParameterMap();
			searchCriteria.add(_ID, uuidParams);
			searchCriteria.setCount(uuidParams.getValuesAsQueryTokens().size());

			List<Observation> allObservations = myObservationDao.searchForResources(searchCriteria, theRequest);
			
			allObservations.forEach(obs -> {
				String observationUuid = obs.getIdElement().getIdPart();
				String intervalGroup = observationIntervalMap.get(observationUuid);
				String systemValueKey = observationIntervalMatrix.entrySet().stream()
						.filter(entry -> entry.getValue().containsValue(observationUuid))
						.map(Map.Entry::getKey)
						.findFirst().orElse(null);

				if (systemValueKey != null) {
					ParametersParameterComponent parameterSystem =  parameterInterval.getPart().stream()
							.filter(p -> p.getName().equals(systemValueKey)).findFirst().orElseGet(() -> {
								ParametersParameterComponent newSystemParam = new ParametersParameterComponent();
								newSystemParam.setName(systemValueKey);
								parameterInterval.addPart(newSystemParam);
								return newSystemParam;
							});

					ZonedDateTime clientTime = Instant.parse(intervalGroup).atZone(clientZoneId);
					String clientFormattedTime = clientTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

					ParametersParameterComponent parameterIntervalGroup = new ParametersParameterComponent();
					parameterIntervalGroup.setName(clientFormattedTime);
					parameterIntervalGroup.setResource(obs);

					parameterSystem.addPart(parameterIntervalGroup);
				}
			});
		}

		return parameters;
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
	 * @return Object[] of interval_group, observation_uuid, sp_system, sp_value
	 */
	@SuppressWarnings("unchecked")
	private List<Object[]> executeAggregationQuery(long interval, String encounterId, Date startDateTime, Date endDateTime, String[] systems,
            String[] codes) {
		 String sql = """
                WITH interval_param AS (
                    SELECT :interval AS interval
                ),
                encounter_res_id AS (
                    SELECT res_id FROM public.hfj_resource WHERE res_type = 'Encounter' AND fhir_id = :encounterId
                ),
                systems_codes AS (
                    SELECT unnest(:systems) AS sp_system, unnest(:codes) AS sp_value
                ),
                calculated_dates AS (
                    SELECT
                        sd.sp_value_high AS date,
                        hr.fhir_id AS observation_uuid,
                        sc.sp_system,
                        sc.sp_value,
                        FLOOR(EXTRACT(EPOCH FROM date_trunc('second', sd.sp_value_high) - date_trunc('day', sd.sp_value_high)) / interval_param.interval) * interval_param.interval AS plain_date_interval
                    FROM
                        public.hfj_spidx_date sd
                    JOIN
                        public.hfj_res_link rl ON sd.res_id = rl.src_resource_id
                    JOIN
                        public.hfj_resource hr ON sd.res_id = hr.res_id
                    JOIN
                        public.hfj_spidx_token st ON sd.res_id = st.res_id
                    JOIN
                        systems_codes sc ON st.sp_system = sc.sp_system AND st.sp_value = sc.sp_value
                    JOIN
                        interval_param ON TRUE
                    WHERE
                        rl.target_resource_id = (SELECT res_id FROM encounter_res_id)
                        AND rl.src_path = 'Observation.encounter'
                        AND sd.sp_value_high >= :startDateTime
                        AND sd.sp_value_low <= :endDateTime
                ),
                grouped_dates AS (
                    SELECT
                        plain_date_interval,
                        MAX(date) AS max_date,
                        observation_uuid,
                        sp_system,
                        sp_value,
                        ROW_NUMBER() OVER (PARTITION BY plain_date_interval, sp_system, sp_value ORDER BY MAX(date) DESC) AS rn
                    FROM
                        calculated_dates
                    GROUP BY
                        plain_date_interval, observation_uuid, sp_system, sp_value
                )
                SELECT
                    date_trunc('day', max_date) + plain_date_interval * INTERVAL '1 second' AS interval_group,
                    observation_uuid,
                    sp_system,
                    sp_value
                FROM
                    grouped_dates
                WHERE
                    rn = 1
                ORDER BY
                    interval_group, sp_system, sp_value;
                """;

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("interval", interval);
		query.setParameter("encounterId", encounterId);
		query.setParameter("startDateTime", startDateTime, TemporalType.TIMESTAMP);
		query.setParameter("endDateTime", endDateTime, TemporalType.TIMESTAMP);
		query.setParameter("systems", systems);
		query.setParameter("codes", codes);

		return query.getResultList();
	}
}
