Feature: Test the custom Configuration resource

  Background:
    * call read('this:../utils.feature@name=utils')

  @name=create_configuration
  Scenario: Create custom Configuration resource
    Given url `${urlBase}/SearchParameter`
    And header Content-Type = 'application/fhir+json'
    # name
    And request
      """
        {
          "resourceType": "SearchParameter",
          "id": "Configuration-name",
          "url": "https://fhir.mona.icu/SearchParameter/Configuration-name",
          "publisher": "Clinomic GmbH",
          "description": "Add a searchparameter for the custom Configuration",
          "name": "name",
          "status": "active",
          "code": "name",
          "base": [
            "Configuration"
          ],
          "type": "string",
          "expression": "name"
        }
      """
    When method post
    Then status 201


    # type
    Given url `${urlBase}/SearchParameter`
    And request
      """
        {
          "resourceType": "SearchParameter",
          "id": "Configuration-type",
          "url": "https://fhir.mona.icu/SearchParameter/Configuration-type",
          "publisher": "Clinomic GmbH",
          "description": "Add a searchparameter for the custom Configuration",
          "name": "type",
          "status": "active",
          "code": "type",
          "base": [
            "Configuration"
          ],
          "type": "token",
          "expression": "type"
        }
      """
    When method post
    Then status 201


    # status
    Given url `${urlBase}/SearchParameter`
    And request
      """
        {
          "resourceType": "SearchParameter",
          "id": "Configuration-status",
          "url": "https://fhir.mona.icu/SearchParameter/Configuration-status",
          "publisher": "Clinomic GmbH",
          "description": "Add a searchparameter for the custom Configuration",
          "name": "status",
          "status": "active",
          "code": "status",
          "base": [
            "Configuration"
          ],
          "type": "token",
          "expression": "status"
        }
      """
    When method post
    Then status 201

    Given url `${urlBase}/Configuration`
    And header Content-Type = 'application/fhir+json'
    # name
    And request
      """
          {
            "resourceType": "Configuration",
            "name": "strange",
            "defaultValue" :"nothing",
            "body": "something",
            "status" : "active",
            "description" : "some long description",
            "type": "configuration"
          }
      """
    When method post
    Then status 201

  Scenario: Custom configuration can be created only with valid data
    Given url `${urlBase}/Configuration`
    And header Content-Type = 'application/fhir+json'
    # wrong status
    And request
      """
          {
            "resourceType": "Configuration",
            "name": "strange",
            "defaultValue" :"nothing",
            "body": "something",
            "status" : "something",
            "description" : "some long description",
            "type": "configuration"
          }
      """
    When method post
    Then status 400

    Given url `${urlBase}/Configuration`
    And header Content-Type = 'application/fhir+json'
    # wrong type
    And request
      """
          {
            "resourceType": "Configuration",
            "name": "strange",
            "defaultValue" :"nothing",
            "body": "something",
            "status" : "active",
            "description" : "some long description",
            "type": "something_else"
          }
      """
    When method post
    Then status 400

  Scenario: Create search parameters and validate them for the custom Configuration resource
    * call read('this:../utils.feature@name=expunge')
    * call sleep 5
    * call read('@name=create_configuration')
      # search for resources : partial name
    Given url `${urlBase}/Configuration?name:contains=stra`
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : exact name
    Given url `${urlBase}/Configuration?name:exact=strange`
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : wrong name
    Given url `${urlBase}/Configuration?name:exact=aaa`
    When method get
    Then status 200
    And match response.total == 0

    # search for resources : type configuration
    Given url `${urlBase}/Configuration?type=configuration`
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : type configuration missing
    Given url `${urlBase}/Configuration?type=script`
    When method get
    Then status 200
    And match response.total == 0

    # search for resources : status active
    Given url `${urlBase}/Configuration?status=active`
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : status wrong
    Given url `${urlBase}/Configuration?status=weong`
    When method get
    Then status 200
    And match response.total == 0

  Scenario: History is enabled for the configuration
    Given url `${urlBase}/Configuration`
    And header Content-Type = 'application/fhir+json'
    # create entry
    And request
      """
          {
            "resourceType": "Configuration",
            "name": "strange",
            "defaultValue" :"nothing",
            "body": "something",
            "status" : "active",
            "description" : "some long description",
            "type": "configuration"
          }
      """
    When method post
    Then status 201

    * def resource_id = response.id

    Given url `${urlBase}/Configuration/${resource_id}`
    And header Content-Type = 'application/fhir+json'
    # update entry
    * def requestBody =
      """
          {
            "resourceType": "Configuration",
            "name": "strange2",
            "defaultValue" :"nothing",
            "body": "something",
            "status" : "active",
            "description" : "some long description",
            "type": "configuration"
          }
      """

    * eval requestBody.id = resource_id

    And request requestBody
    When method put
    Then status 200

    # check history
    Given url `${urlBase}/Configuration/${resource_id}/_history`
    When method get
    Then status 200
    And match response.total == 2
    And match response.entry[0].resource.name == "strange2"
    And match response.entry[1].resource.name == "strange"
