Feature: Test the custom Configuration resource

  Background:
    * call read('this:utils.feature@name=utils')
    * call read('this:utils.feature@name=expunge')


  Scenario: Create search parameters and validate them for the custom Configuration resource
    * call read('this:utils.feature@name=expunge')
    * call sleep 3

    Given url 'http://localhost:8080/fhir/SearchParameter'
    And header Content-Type = 'application/fhir+json'
    # name
    And request
      """
        {
          "resourceType": "SearchParameter",
          "id": "Configuration-name",
          "url": "https://fhir.mona.icu/SearchParameter/Configuration-name",
          "name": "Configuration-name",
          "status": "active",
          "code": "configuration-name",
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
    Given url 'http://localhost:8080/fhir/SearchParameter'
    And request
      """
        {
          "resourceType": "SearchParameter",
          "id": "Configuration-type",
          "url": "https://fhir.mona.icu/SearchParameter/Configuration-type",
          "name": "Configuration-type",
          "status": "active",
          "code": "configuration-type",
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
    Given url 'http://localhost:8080/fhir/SearchParameter'
    And request
      """
        {
          "resourceType": "SearchParameter",
          "id": "Configuration-status",
          "url": "https://fhir.mona.icu/SearchParameter/Configuration-status",
          "name": "Configuration-status",
          "status": "active",
          "code": "configuration-status",
          "base": [
            "Configuration"
          ],
          "type": "token",
          "expression": "status"
        }
      """
    When method post
    Then status 201

    Given url 'http://localhost:8080/fhir/Configuration'
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


    # search for resources : partial name
    Given url 'http://localhost:8080/fhir/Configuration?configuration-name:contains=stra'
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : exact name
    Given url 'http://localhost:8080/fhir/Configuration?configuration-name:exact=strange'
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : wrong name
    Given url 'http://localhost:8080/fhir/Configuration?configuration-name:exact=aaa'
    When method get
    Then status 200
    And match response.total == 0

    # search for resources : type configuration
    Given url 'http://localhost:8080/fhir/Configuration?configuration-type=configuration'
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : type configuration missing
    Given url 'http://localhost:8080/fhir/Configuration?configuration-type=script'
    When method get
    Then status 200
    And match response.total == 0

    # search for resources : status active
    Given url 'http://localhost:8080/fhir/Configuration?configuration-status=active'
    When method get
    Then status 200
    And match response.entry[0].resource.name == "strange"

    # search for resources : status wrong
    Given url 'http://localhost:8080/fhir/Configuration?configuration-status=weong'
    When method get
    Then status 200
    And match response.total == 0
