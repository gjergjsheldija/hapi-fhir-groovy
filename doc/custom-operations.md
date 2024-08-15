# Custom operations

The server comes with the following custom operations. If not enabled they can be
enabled in the `application.yaml` file.

## $group-by-interval

The operation works on the Observation resource. It returns the value with the biggest date time for the
selected date times in the given intervals.
The documentation can be found below :

```yaml
openapi: 3.0.1
info:
  title: HAPI FHIR Server
  contact: { }
  version: 7.2.0
servers:
  - url: http://localhost:8080/fhir
    description: HAPI FHIR Server
paths:
  /Observation/$group-by-interval:
    get:
      tags:
        - Observation
      summary: "GET: /Observation/$group-by-interval"
      parameters:
        - name: encounter
          in: query
          description: The encounter to search for
          required: false
          schema:
            type: string
        - name: code
          in: query
          description: The code of the Observation
          required: false
          schema:
            type: string
        - name: startDateTime
          in: query
          description: The start datetime to search from
          required: false
          schema:
            anyOf:
              - type: string
                format: date-time
              - type: string
                format: date
        - name: endDateTime
          in: query
          description: The end datetime to search to
          required: false
          schema:
            anyOf:
              - type: string
                format: date-time
              - type: string
                format: date
        - name: interval
          in: query
          description: The interval in seconds
          required: false
          schema:
            type: number
      responses:
        "200":
          description: Success
          content:
            application/fhir+json:
              schema:
                $ref: '#/components/schemas/FHIR-JSON-RESOURCE'
            application/fhir+xml:
              schema:
                $ref: '#/components/schemas/FHIR-XML-RESOURCE'
    post:
      tags:
        - Observation
      summary: "POST: /Observation/$group-by-interval"
      requestBody:
        content:
          application/fhir+json:
            schema:
              title: FHIR Resource
              type: object
            example: |-
              {
                "resourceType": "Parameters",
                "parameter": [ {
                  "name": "encounter",
                  "valueString": "Encounter/3b205f2f-3813-4bff-9945-1729d52b9eb0"
                }, {
                  "name": "code",
                  "valueString": "http://loinc.org|8867-4"
                }, {
                  "name": "startDateTime",
                  "valueString": "2024-07-24T05:00"
                }, {
                  "name": "endDateTime",
                  "valueString": "2024-07-24T10:00"
                }, {
                  "name": "interval",
                  "valueString": "3600"
                } ]
              }
      responses:
        "200":
          description: Success
          content:
            application/fhir+json:
              schema:
                $ref: '#/components/schemas/FHIR-JSON-RESOURCE'
            application/fhir+xml:
              schema:
                $ref: '#/components/schemas/FHIR-XML-RESOURCE'
components:
  schemas:
    FHIR-JSON-RESOURCE:
      type: object
      description: A FHIR resource
    FHIR-XML-RESOURCE:
      type: object
      description: A FHIR resource
```

Notes :
Extra standard FHIR query parameters can also be added like `_elements` and `_sort`