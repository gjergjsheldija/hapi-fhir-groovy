Feature: Test the AuditEvent generation

  Background:
    * call read('this:../utils.feature@name=utils')

  Scenario: AuditEvent created for Create
    * call read('this:../utils.feature@name=expunge')
    * call sleep 3
    Given url `${urlBase}/Patient`
    And header Content-Type = 'application/fhir+json'

    # create Patient
    And request
      """
        {
          "resourceType": "Patient",
          "name" : [{
              "use" : "official",
              "family" : "Chalmers",
              "given" : ["Peter",
              "James"]
            }]
        }
      """
    When method post
    Then status 201

    * def resource_id = response.id

    # update Patient
    Given url `${urlBase}/Patient/${resource_id}`
    And header Content-Type = 'application/fhir+json'

    * def requestBody =
      """
        {
          "resourceType": "Patient",
          "name" : [{
              "use" : "official",
              "family" : "Chalmers",
              "given" : ["Adam"]
            }]
        }
      """

    * eval requestBody.id = resource_id

    And request requestBody
    When method put
    Then status 200

    # search for the entry
    Given url `${urlBase}/Patient/${resource_id}`
    When method get
    Then status 200

    # delete
    Given url `${urlBase}/Patient/${resource_id}`
    And header Content-Type = 'application/fhir+json'

    When method delete
    Then status 200

    # check AuditEvent
    Given url `${urlBase}/AuditEvent`
    When method get
    Then status 200

    And match response.total == 3
