Feature: Test Custom Configuration Script creation


  Background:
    * call read('this:../utils.feature@name=utils')


  Scenario: Script created
    * call read('this:../utils.feature@name=expunge')
    * call sleep 3
    * def groovyScript = read('this:files/PatientDemo.groovy')

    Given url `${urlBase}/Configuration/415da1b7-38db-462f-a83c-c93d59b07ea3`
    And header Content-Type = 'application/fhir+json'

    # create Script
    * def requestBody =
      """
      {
        "resourceType": "Configuration",
        "id": "415da1b7-38db-462f-a83c-c93d59b07ea3",
        "name": "some name",
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

    * def resource_id = response.id