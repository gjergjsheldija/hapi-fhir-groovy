Feature: Test terminology features

  Background:
    * call read('this:../utils.feature@name=utils')

  Scenario: The server returns a valid TerminologyCapabilities
    * call read('this:../utils.feature@name=expunge')
    * call sleep 3
    Given url `${urlBase}/metadata?mode=terminology`
    And header Content-Type = 'application/fhir+json'
    When method get
    Then status 200

    And match response ==
    """
    {
      "resourceType": "TerminologyCapabilities",
      "meta": {
        "profile": [
          "http://hl7.org/fhir/StructureDefinition/TerminologyCapabilities"
        ]
      },
      "version": "7.2.0",
      "name": "HAPI FHIR Server",
      "status": "draft"
    }
    """