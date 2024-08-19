Feature: Test Custom Configuration Script creation

  Background:
    * call read('this:../utils.feature@name=utils')

  Scenario: Interceptor Script created
    * call read('this:utils.feature@name=expunge')
    * call sleep 3
    * call read('this:custom-configuration.feature@name=create_configuration')

    * json groovyScript = read('this:files/PatientDemo.groovy')

    Given url `${urlBase}/Configuration/415da1b7-38db-462f-a83c-c93d59b07ea3`
    And header Content-Type = 'application/fhir+json'

    # create Script
    * def requestBody =
      """
      {
        "resourceType": "Configuration",
        "id": "415da1b7-38db-462f-a83c-c93d59b07ea3",
        "name": "PatientCreated",
        "defaultValue" :"no default value",
        "description": "the content of the configuration",
        "status" : "active",
        "type": "script",
      }
      """
    * eval requestBody.body = groovyScript

    And request requestBody
    When method Put
    Then status 201

  Scenario: Interceptor script loaded

    Given url `${urlBase}/Configuration/$load-script`
    And header Content-Type = 'application/fhir+json'

    And request
    """
    {
      "resourceType": "Parameters",
      "parameter": [
        {
          "name": "name",
          "valueString": "PatientCreated"
        }
      ]
    }
    """

    When method Post
    Then status 200
    And match response.issue[0].diagnostics == "Script loaded : PatientCreated"

  Scenario: Provider script is listed

    Given url `${urlBase}/Configuration/$list-scripts`
    And header Content-Type = 'application/fhir+json'

    When method Get
    Then status 200
    And match karate.toString(response.issue[0].diagnostics) contains "scripting.PatientCreated"

  Scenario: Interceptor Script unloaded
    Given url `${urlBase}/Configuration/$unload-script`
    And header Content-Type = 'application/fhir+json'

    And request
    """
    {
      "resourceType": "Parameters",
      "parameter": [
        {
          "name": "name",
          "valueString": "PatientCreated"
        }
      ]
    }
    """

    When method Post
    Then status 200
    And match response.issue[0].diagnostics == "Script unloaded : PatientCreated"

  Scenario: Provider Script created
    * call read('this:utils.feature@name=expunge')
    * call sleep 3
    * call read('this:custom-configuration.feature@name=create_configuration')

    * json groovyScript = read('this:files/HealthCheck.groovy')

    Given url `${urlBase}/Configuration/415da1b7-38db-462f-a83c-c93d59b07ea4`
    And header Content-Type = 'application/fhir+json'

    # create Script
    * def requestBody =
      """
      {
        "resourceType": "Configuration",
        "id": "415da1b7-38db-462f-a83c-c93d59b07ea4",
        "name": "HealthCheck",
        "defaultValue" :"no default value",
        "description": "the content of the configuration",
        "status" : "active",
        "type": "script",
      }
      """
    * eval requestBody.body = groovyScript

    And request requestBody
    When method Put
    Then status 201

  Scenario: Provider script loaded

    Given url `${urlBase}/Configuration/$load-script`
    And header Content-Type = 'application/fhir+json'

    And request
    """
    {
      "resourceType": "Parameters",
      "parameter": [
        {
          "name": "name",
          "valueString": "HealthCheck"
        }
      ]
    }
    """

    When method Post
    Then status 200
    And match response.issue[0].diagnostics == "Script loaded : HealthCheck"

  Scenario: Call Provider script

    Given url `${urlBase}/$healthcheck`
    And header Content-Type = 'application/fhir+json'

    And request
    """
    {
      "resourceType": "Parameters",
      "parameter": [
        {
          "name": "name",
          "valueString": "test string"
        }
      ]
    }
    """

    When method Post
    Then status 200
    And match response.parameter[0].valueString == "test string"

  Scenario: Provider Script unloaded
    Given url `${urlBase}/Configuration/$unload-script`
    And header Content-Type = 'application/fhir+json'

    And request
    """
    {
      "resourceType": "Parameters",
      "parameter": [
        {
          "name": "name",
          "valueString": "HealthCheck"
        }
      ]
    }
    """

    When method Post
    Then status 200
    And match response.issue[0].diagnostics == "Script unloaded : HealthCheck"

    Given url `${urlBase}/$healthcheck`
    And header Content-Type = 'application/fhir+json'

    And request
    """
    {
      "resourceType": "Parameters",
      "parameter": [
        {
          "name": "name",
          "valueString": "test string"
        }
      ]
    }
    """

    When method Post
    Then status 400
    And match response.issue[0].diagnostics == "Invalid request: The FHIR endpoint on this server does not know how to handle POST operation[$healthcheck] with parameters [[]]"