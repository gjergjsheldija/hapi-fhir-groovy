Feature: Utilities

  @name=utils
  Scenario: utils
    * def sleep =
      """
      function(seconds){
        for(i = 0; i <= seconds; i++)
        {
          java.lang.Thread.sleep(1*1000);
          karate.log(i);
        }
      }
      """
    * def parsePayload =
      """
      function(payload) {
          var Base64 = Java.type('java.util.Base64');
          var decoded = Base64.getDecoder().decode(payload);
          var String = Java.type('java.lang.String');
          return new String(decoded);
      }
      """

  @ignore
  @name=health
  Scenario: health

    Given url `${urlBase}/CapabilityStatement`
    And configure readTimeout = 60000
    And configure connectTimeout = 60000
    When method get
    Then status 200

  @ignore
  @name=expunge
  Scenario: expunge

    Given url `${urlBase}/$expunge`
    And configure readTimeout = 60000
    And configure connectTimeout = 60000
    And header Content-Type = 'application/fhir+json'
    And request
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {
            "name": "expungeEverything",
            "valueBoolean": true
          }
        ]
      }
      """
    When method post
    Then status 200

    Given url `${urlBase}/Patient`
    And header Content-Type = 'application/fhir+json'
    When method get
    Then status 200
    And match response.total == 0

  @ignore
  @name=SendStringToUrl
  Scenario: Take an input string and send it to a given url
    Given url inputUrl
    And header Content-Type = contentType
    And request inputFile
    When method post
    Then status 200