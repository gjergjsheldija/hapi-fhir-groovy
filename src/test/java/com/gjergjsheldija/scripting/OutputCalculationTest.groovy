/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-10-01
 */

package com.gjergjsheldija.scripting

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoEncounter
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoObservation
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap
import ca.uhn.fhir.rest.api.server.IBundleProvider
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.DateParam
import ca.uhn.fhir.rest.param.NumberParam
import ca.uhn.fhir.rest.param.ReferenceParam
import groovy.json.JsonSlurper
import org.hl7.fhir.instance.model.api.IIdType
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Observation
import scripts.OutputCalculation
import spock.lang.Specification

class OutputCalculationTest extends Specification {

    OutputCalculation outputCalculation
    IFhirResourceDaoObservation observationDao
    IFhirResourceDaoEncounter encounterDao
    IFhirResourceDao configurationDao
    JsonSlurper jsonSlurper
    FhirContext fhirContext
    def parser

    def setup() {
        outputCalculation = new OutputCalculation()
        observationDao = Mock(IFhirResourceDaoObservation)
        encounterDao = Mock(IFhirResourceDaoEncounter)
        configurationDao = Mock(IFhirResourceDao)
        jsonSlurper = new JsonSlurper()
        fhirContext = FhirContext.forR4()
        parser = fhirContext.newJsonParser()

        outputCalculation.observationDao = observationDao
        outputCalculation.encounterDao = encounterDao
        outputCalculation.myConfigurationDao = configurationDao
    }

    def "test calculatedOutput method"() {
        given:
        def encounterJson = new File("src/test/resources/scripts/output_calculation/encounter.json").text
        def observationsJson = new File("src/test/resources/scripts/output_calculation/observations.json").text
        def configurationJson = new File("src/test/resources/scripts/output_calculation/configuration.json").text
        def expectedOutputJson = new File("src/test/resources/scripts/output_calculation/expected_output.json").text

        def encounter = parser.parseResource(Encounter.class, encounterJson)
        def observations = parser.parseResource(Bundle.class, observationsJson).getEntry().collect { it.getResource() as Observation }
        def configurationResource = parser.parseResource(com.gjergjsheldija.configuration.Configuration.class, configurationJson)
        def expectedOutput = parser.parseResource(Bundle.class, expectedOutputJson)

        def encounterRef = new ReferenceParam("Encounter", "test-encounter-id")
        def intervalMinutes = new NumberParam(60)
        def dateFrom = new DateParam("2025-07-24T01:00:00Z")
        def dateTo = new DateParam("2025-07-24T03:00:00Z")
        def requestDetails = Mock(RequestDetails)

        encounterDao.read(_ as IIdType, _ as RequestDetails) >> encounter
        observationDao.searchForResources(_ as SearchParameterMap, _ as RequestDetails) >> observations

        // Mock IBundleProvider for configuration search
        def mockBundleProvider = Mock(IBundleProvider)
        mockBundleProvider.getAllResources() >> [configurationResource]
        configurationDao.search(_ as SearchParameterMap, _ as RequestDetails) >> mockBundleProvider


        when:
        def result = outputCalculation.calculatedOutput(encounterRef, intervalMinutes, dateFrom, dateTo, requestDetails)
        // convert to json parser.encodeResourceToString(result);

        then:
        result.getEntry().size() == expectedOutput.getEntry().size()
        result.getEntry().eachWithIndex { entry, index ->
            def resultObs = entry.getResource() as Observation
            def expectedObs = expectedOutput.getEntry()[index].getResource() as Observation
            assert resultObs.getEffective().dateTimeValue().getValue() == expectedObs.getEffective().dateTimeValue().getValue()
            assert resultObs.getValueQuantity().getValue() == expectedObs.getValueQuantity().getValue()
        }
    }
}