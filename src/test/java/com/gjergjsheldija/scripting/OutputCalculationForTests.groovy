package com.gjergjsheldija.scripting


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.temporal.ChronoUnit

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoEncounter;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component
import com.gjergjsheldija.configuration.Configuration;
import com.gjergjsheldija.scripting.api.CustomScript
import com.gjergjsheldija.scripting.api.Provider

@CustomScript
@Provider
@Component
class OutputCalculationForTests {

    private static final Logger logger = LoggerFactory.getLogger("OutputCalculation");
    public static Calendar currentTimestamp;

    @Autowired
    IFhirResourceDaoObservation<Observation> observationDao;

    @Autowired
    IFhirResourceDaoEncounter<Encounter> encounterDao;

    @Autowired
    IFhirResourceDao<Configuration> myConfigurationDao;

    OutputCalculationForTests() {
        currentTimestamp = Calendar.getInstance();
    }

    @Operation(name = "\$output-calculation", idempotent = true, type = Observation.class)
    Bundle calculatedOutput(@Description(shortDefinition = "The encounter id to calculate output.", example = "3b205f2f-3813-4bff-9945-1729d52b9eb0") @OperationParam(name = "encounter") ReferenceParam encounterRef, @Description(shortDefinition = "The interval to sample data in minutes.") @OperationParam(name = "interval") NumberParam intervalMinutes, @Description(shortDefinition = "The start datetime to search from", example = "'2025-07-24T01:00:00Z'") @OperationParam(name = "dateFrom") DateParam dateFrom, @Description(shortDefinition = "The end datetime to search to", example = "'2025-07-24T03:00:00Z'") @OperationParam(name = "dateTo") DateParam dateTo, RequestDetails requestDetails) {

        IIdType encounterId = new IdType(encounterRef.getResourceType(), encounterRef.getIdPart());
        int intIntervalMinutes = intervalMinutes.getValue().intValue();
        Encounter encounter = encounterDao.read(encounterId, requestDetails);
        org.hl7.fhir.r4.model.Period encounterPeriod = encounter.getPeriod();
        Date resetTime = getResetTime(myConfigurationDao, requestDetails);
        Date calculationStartDate = getCalculationStartDate(dateFrom.getValue(), resetTime, encounterPeriod);
        // if endTo date is beyond the encounter date we restrict the calculation till encounter end date.
        if (encounterPeriod.hasEnd() && encounterPeriod.getEnd().before(dateTo.getValue())) {
            logger.debug("Requested endTo: ${dateTo.getValue()} is after the encounter end date: ${encounterPeriod.getEnd().toString()}, hence calculation only till encounter end date.");
            dateTo.setValue(encounterPeriod.getEnd())
        }
        List<Observation> allObservations = getObservations(encounterRef, observationDao, calculationStartDate, dateTo, requestDetails);
        if (allObservations.isEmpty()) {
            return new Bundle();
        }
        Observation firstObservation = allObservations.getFirst();
        // if calculationStartDate is before the first observation's recorded date then we adjust our calculation start date accordingly.
        if (firstObservation.getEffectiveDateTimeType().getValue().toInstant().isAfter(calculationStartDate.toInstant())) {
            calculationStartDate = firstObservation.getEffectiveDateTimeType().getValue();
            logger.debug("Calculated start date is before the first observation's recorded date, hence adjusting the calculation start date to ${calculationStartDate}.");
        }

        List<Object> listOfObjects = getFilteredObservationsAndMap(allObservations, intIntervalMinutes);
        ArrayList<Observation> filteredObservations = (ArrayList<Observation>) listOfObjects.get(0);
        HashMap<Date, BigDecimal> observationDateValueMap = (HashMap<Date, BigDecimal>) listOfObjects.get(1);

        if (filteredObservations.isEmpty()) {
            return new Bundle();
        }

        Date calculateTill = encounter.getPeriod().getEnd();
        // For `in-progress` encounter end date will be `null` in that case calculation should happen till the current time.
        if (calculateTill == null) {
            Calendar currentTime = (Calendar) currentTimestamp.clone();
            calculateTill = ceilingRoundToNearestMinutes(currentTime.getTime(), intIntervalMinutes);
        } else {
            calculateTill = ceilingRoundToNearestMinutes(calculateTill, intIntervalMinutes);
        }
        Set<Date> intervalDates = generateIntervalDates(calculationStartDate, calculateTill, intIntervalMinutes);
        Set<Date> resetDates = getResetDates(calculationStartDate, calculateTill, resetTime);
        // Adding reset timestamps with the interval timestamps and creating a set with unique timestamps.
        intervalDates.addAll(resetDates);
        List<Date> intervalDateList = new ArrayList<>(intervalDates);
        Collections.sort(intervalDateList);
        Bundle calculatedOutputs = fillOutputCalculation(intervalDateList, resetDates, observationDateValueMap, encounterId, dateFrom.getValue(), dateTo.getValue(), intIntervalMinutes);

        return calculatedOutputs;
    }

    /**
     * This method calculates the start date for the output calculation based on the given dateFrom and resetTime.
     * If the dateFrom matches the resetTime, the calculationStartDate is set to the dateFrom.
     * Otherwise, the calculationStartDate is set to the previous day at the resetTime.
     *
     * @param dateFrom The start date for the output calculation.
     * @param resetTime The local time at which the reset occurs.
     * @param encounterPeriod The encounter start date and end date information.
     * @return The calculated start date for the output calculation.
     */
    static Date getCalculationStartDate(Date dateFrom, Date resetTime, org.hl7.fhir.r4.model.Period encounterPeriod) {
        Date calculationStartDate = dateFrom;
        if (dateFrom.getHours() != resetTime.getHours() || dateFrom.getMinutes() != resetTime.getMinutes() || dateFrom.getSeconds() != resetTime.getSeconds()) {
            Calendar calendar = (Calendar) currentTimestamp.clone();
            calendar.setTime(dateFrom);
            // subtract one day to include the previous day's reset time.
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            calendar.set(Calendar.HOUR_OF_DAY, resetTime.getHours());
            calendar.set(Calendar.MINUTE, resetTime.getMinutes());
            calendar.set(Calendar.SECOND, resetTime.getSeconds());
            calculationStartDate = calendar.getTime();
        }
        if (calculationStartDate.before(encounterPeriod.getStart())) {
            calculationStartDate = encounterPeriod.getStart();
        }
        return calculationStartDate;
    }

    /**
     * This function filters and aggregates the Observation resources based on the given interval.
     *
     * @param allObservations A list of all Observation resources to be filtered and aggregated.
     *                           This list should not be null or empty.
     * @param intIntervalMinutes The interval in minutes for which the Observations should be aggregated.
     *                           This value should be a positive integer.
     * @return An ArrayList containing two elements:
     * 1. A list of Observation resources that have been filtered and aggregated based on the given interval.
     * The filtered Observations are those with a Quantity value and a unit of "ml".
     * The aggregated Observations have their values rounded to the nearest interval minutes, and their values
     * aggregated based on the interval.
     * The Observations are sorted by their effective date times in ascending order.
     * 2. A HashMap containing the rounded date as a key and the cumulative value as value.
     * This map is used to store the aggregated values for each interval.
     */
    private static List<Object> getFilteredObservationsAndMap(List<Observation> allObservations, int intIntervalMinutes) {

        ArrayList<Observation> filteredObservations = new ArrayList<>();
        // Create a map to store the rounded date as a key and the cumulative value as value.
        HashMap<Date, BigDecimal> observationDateValueMap = new HashMap<>();
        for (Observation observation : allObservations) {
            if (observation.hasValueQuantity()) {
                Quantity quantity = observation.getValueQuantity();  // Cache the quantity object
                BigDecimal value = quantity.getValue();

                if (value != null && "ml".equalsIgnoreCase(quantity.getUnit())) {
                    filteredObservations.add(observation);
                    Date roundedDate = ceilingRoundToNearestMinutes(observation.getEffectiveDateTimeType().getValue(), intIntervalMinutes);

                    // Case1: If the date already exists, add the current value to the existing value
                    // This makes sure that if two or more outputs are documented in the same interval,
                    // we aggregate those values to get total value documented at the particular interval
                    // i.e. interval=60, document1= 12:20:00, -50 ml, doc2=12:30:00, -40 ml -> result=12:00:00, -90 ml
                    // Case2: If the date does not exist, add the current value to a new entry in the map
                    observationDateValueMap.compute(roundedDate, (date, existingValue) -> (existingValue == null) ? value : existingValue.add(value));
                }
            }
        }
        logger.info("Number of filtered Observations are: ${filteredObservations.size() ?: 0}");
        return Arrays.asList(filteredObservations, observationDateValueMap);
    }

    /**
     * This method fills up a Bundle resource with calculated output (Observation) values/resources, based on the given
     * parameters.
     * The function iterates through a list of interval dates, and for each date, it checks if it is a local reset date.
     * If it is, the current output is updated with the corresponding value from the observationDateValueMap.
     * Then, it checks if there is a value for the current date in the observationDateValueMap.
     * If there is, it adds the value to the current outputs.
     * Finally, it creates an Observation resources for the current, and adds it to the Bundle.
     *
     * @param intervalDateList A list of dates representing the intervals within a given date range.
     * @param resetDates A set of dates representing the local reset times within the given date range.
     * @param observationDateValueMap A map containing the rounded date as key and the cumulative value as value.
     * @param encounterId The identifier of the encounter for which the output calculations are being made.
     * @param dateFrom The start date for the output calculation.
     * @param dateTo The end date for the output calculation.
     * @param intIntervalMinutes The interval in minutes for which the Observations should be aggregated.
     *                           This value should be a positive integer.
     * @return A Bundle resource containing the calculated output values for each interval date.
     */
    private static Bundle fillOutputCalculation(List<Date> intervalDateList, Set<Date> resetDates, HashMap<Date, BigDecimal> observationDateValueMap, IIdType encounterId, Date dateFrom, Date dateTo, int intIntervalMinutes) {

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        BigDecimal currentOutput = BigDecimal.ZERO;

        // Filling up Timestamps with calculated values
        for (Date intervalDate : intervalDateList) {
            if (observationDateValueMap.containsKey(intervalDate)) {
                BigDecimal value = observationDateValueMap.get(intervalDate);
                currentOutput = currentOutput.add(value);
            }
            // If intervalDate is the same as the resetTime, then currentOutput should be updated with reset logic.
            if (resetDates.contains(intervalDate)) {
                if (observationDateValueMap.containsKey(intervalDate)) {
                    currentOutput = observationDateValueMap.get(intervalDate);
                } else {
                    currentOutput = BigDecimal.ZERO;
                }
            }
            int minutes = intervalDate.getHours() * 60 + intervalDate.getMinutes();
            if (minutes % intIntervalMinutes == 0) {
                if (intervalDate.equals(dateFrom) || intervalDate.equals(dateTo) || (intervalDate.after(dateFrom) && intervalDate.before(dateTo))) {
                    bundle = addObservationsToBundle(bundle, currentOutput.floatValue(), intervalDate, encounterId);
                }
            }

        }
        return bundle;
    }

    /**
     * This method adds a new Observation resource to the given Bundle, representing the current output value at a specific interval date.
     *
     * @param bundle The Bundle to which the new Observation resource will be added.
     * @param currentOutput The current output value, represented as a float.
     * @param intervalDate The interval date time sampling Date object.
     * @param encounterId The identifier of the encounter for which the output calculations are being made.
     *
     * @return The updated Bundle with the new Observation resource added.
     */
    private static Bundle addObservationsToBundle(Bundle bundle, Float currentOutput, Date intervalDate, IIdType encounterId) {
        Observation currentObservation = createObservation(currentOutput.floatValue(), intervalDate, encounterId, "251840008", "Current Output");

        bundle.addEntry().setResource(currentObservation);
        return bundle;
    }

    /**
     * This method creates a new Observation resource with the given parameters.
     *
     * @param value The value of the observation, represented as a float.
     * @param intervalDate The interval date time sampling Date object.
     * @return A new Observation resource with the specified value, unit, coding, and effectiveDateTime.
     */
    private static Observation createObservation(Float value, Date intervalDate, IIdType encounterId, String snomedCode, String displayName) {
        Coding categoryCoding = new Coding().setSystem("https://fhir.mona.icu/CodeSystem/mona-observation-category").setCode("output").setDisplay("Output");
        Coding codeCoding = new Coding().setSystem("http://snomed.info/sct").setCode(snomedCode).setDisplay(displayName);
        Observation obs = (Observation) new Observation().setId(UUID.randomUUID().toString());
        return obs.setValue(new Quantity().setValue(value).setUnit("ml")).setCategory(Arrays.asList(new CodeableConcept().setCoding(Arrays.asList(categoryCoding)))).setEffective(new DateTimeType(convertToUTC(intervalDate).toInstant().toString())).setCode(new CodeableConcept().setCoding(Arrays.asList(codeCoding))).setEncounter(new Reference(encounterId)).setStatus(Observation.ObservationStatus.FINAL);
    }
    /**
     * This method converts a given date to Coordinated Universal Time (UTC).
     *
     * @param intervalDate The original date to be converted to UTC.
     * @return The converted date in UTC.
     */
    static Date convertToUTC(Date intervalDate) {
        Calendar calendar = (Calendar) currentTimestamp.clone();
        calendar.setTime(intervalDate);
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date utcDate = calendar.getTime();
        return utcDate;
    }

    /**
     * This method rounds up a given date to the nearest specified interval in minutes.
     *
     * @param date The original date to be rounded up.
     * @param minutes The interval in minutes to which the date should be rounded up.
     * @return The rounded-up date, with the seconds set to 0 and the minutes rounded up to the nearest multiple of the specified interval.
     */
    static Date ceilingRoundToNearestMinutes(Date date, int minutes) {
        date.setMinutes(date.getMinutes() - date.getMinutes() % minutes);
        Instant instant = date.toInstant();
        Instant truncatedInstant = instant.truncatedTo(ChronoUnit.MINUTES);
        date = Date.from(truncatedInstant);
        return date;
    }

    /**
     * This method generates a set of dates representing the intervals within a given date range.
     * Each sample is separated by the specified interval in minutes.
     *
     * @param startDate The start date of the range.
     * @param endDate The end date of the range.
     * @param intervalInMinutes The interval in minutes between each date in the set.
     * @return A set of dates representing the intervals within the given date range.
     */
    static Set<Date> generateIntervalDates(Date startDate, Date endDate, int intervalInMinutes) {
        Set<Date> intervalsDates = new HashSet<>();
        long intervalInMillis = intervalInMinutes * 60 * 1000;

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        startDate = calendar.getTime();

        // Iterate through the date range, adding each interval date to the set
        for (long time = startDate.getTime(); time <= endDate.getTime(); time += intervalInMillis) {
            intervalsDates.add(new Date(time));
        }

        return intervalsDates;
    }

    /**
     * Retrieves a list of Observation resources based on the given encounter reference and category.
     *
     * @param encounterRef The ReferenceParam representing the encounter for which observations are to be retrieved.
     * @param observationDao The IFhirResourceDaoObservation for accessing Observation resources.
     * @param calculationStartDate The start date of the range for retrieving observations.
     * @param dateTo The end date of the range for retrieving observations.
     * @param requestDetails The RequestDetails object containing additional request details.
     * @return A list of Observation resources that match the given encounter reference and category.
     * The list is sorted by the 'date' field in ascending order.
     */
    static List<Observation> getObservations(ReferenceParam encounterRef, IFhirResourceDaoObservation<Observation> observationDao, Date calculationStartDate, DateParam dateTo, RequestDetails requestDetails) {
        TokenParam category = new TokenParam("https://fhir.mona.icu/CodeSystem/mona-observation-category", "output");

        SearchParameterMap params = new SearchParameterMap();
        params.add(Observation.SP_ENCOUNTER, encounterRef);
        params.add(Observation.SP_CATEGORY, category);
        params.add(Observation.SP_DATE, new DateParam("ge" + calculationStartDate.toInstant()));
        params.add(Observation.SP_DATE, new DateParam("le" + dateTo.getValue().toInstant()));
        params.setSort(new SortSpec("date"));
        params.setLoadSynchronous(true);

        List<Observation> allObservations = observationDao.searchForResources(params, requestDetails);
        logger.info("Found number of observations: ${allObservations.size() ?: 0}");
        return allObservations;
    }

    /**
     * This method retrieves the local reset time from the application's configuration.
     * If the reset time is not found in the configuration, it defaults to 08:00:00.
     *
     * @param myConfigurationDao The IFhirResourceDao for accessing the application's configuration.
     * @param requestDetails The RequestDetails object containing additional request details.
     * @return The local reset time as a Date object.
     */
    static Date getResetTime(IFhirResourceDao<Configuration> myConfigurationDao, RequestDetails requestDetails) {

        SearchParameterMap configParams = new SearchParameterMap();
        configParams.add(Configuration.SP_NAME, new StringParam("resetTime"));
        List<IBaseResource> config = myConfigurationDao.search(configParams, requestDetails).getAllResources();

        Date time = getDefaultResetTime();
        if (config.size() > 0) {
            Configuration configuration = (Configuration) config.get(0);
            String resetTime = configuration.getBody().getValueAsString();
            try {
                time = new SimpleDateFormat("HH:mm:ss").parse(resetTime);
            } catch (ParseException e) {
                logger.error("Failed to parse the reset time: ", e);
            }
        }
        logger.debug("Reset time for the output calculation is ${time.getHours()}:${time.getMinutes()}:${time.getSeconds()}");

        return time;
    }


    /**
     * This method retrieves the default local reset time, which is 08:00:00.
     *
     * @return The default local reset time as a Date object.
     */
    static Date getDefaultResetTime() {
        Calendar calendar = (Calendar) currentTimestamp.clone();
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date time = calendar.getTime();
        return time;
    }

    /**
     * This method generates a set of dates representing the local reset times within a given date range.
     *
     * @param startDate The start date of the range.
     * @param endDate The end date of the range.
     * @param resetTime The local time at which the reset occurs.
     * @return A set of dates representing the local reset times within the given date range.
     */
    static Set<Date> getResetDates(Date startDate, Date endDate, Date resetTime) {
        Set<Date> localResetDateTime = new HashSet<>();
        Calendar currentDate = (Calendar) currentTimestamp.clone();
        currentDate.setTime(startDate);
        Calendar resetCalendar = (Calendar) currentTimestamp.clone();
        resetCalendar.setTime(resetTime);

        while (currentDate.getTime().before(endDate)) {
            if (currentDate.getTime().after(startDate) || currentDate.getTime().equals(startDate)) {
                currentDate.set(Calendar.HOUR_OF_DAY, resetCalendar.get(Calendar.HOUR_OF_DAY));
                currentDate.set(Calendar.MINUTE, resetCalendar.get(Calendar.MINUTE));
                currentDate.set(Calendar.SECOND, resetCalendar.get(Calendar.SECOND));
                localResetDateTime.add(currentDate.getTime());
            }
            currentDate.add(Calendar.DAY_OF_MONTH, 1);
        }
        logger.info("Number of generated reset times: ${localResetDateTime?.size() ?: 0}")
        return localResetDateTime;
    }
}
