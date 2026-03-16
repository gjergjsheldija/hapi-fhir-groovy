# AuditEvent

## Description

The audit event interceptor implements the [BALP](https://profiles.ihe.net/ITI/BALP/) pattern.
The interceptor is enabled for all operation except for FHIR.READ and FHIR.VREAD.
Depending on future requirements, the AuditEvent can be stored locally or pushed to a remote server.

## Configuration

By default, the AuditEvent is disabled, in order to enable it, the entry `hapi.fhir.audit_event` in the
`application.yaml` must be set to true, or the environment variable `AUDIT_EVENT` must be set to true.

## Example

```json
{
  "resourceType": "AuditEvent",
  "id": "2",
  "meta": {
    "versionId": "1",
    "lastUpdated": "2025-06-14T11:41:13.866+02:00",
    "profile": [
      "https://profiles.ihe.net/ITI/BALP/StructureDefinition/IHE.BasicAudit.PatientCreate"
    ]
  },
  "type": {
    "system": "http://terminology.hl7.org/CodeSystem/audit-event-type",
    "code": "rest",
    "display": "Restful Operation"
  },
  "subtype": [
    {
      "system": "http://hl7.org/fhir/restful-interaction",
      "code": "create",
      "display": "create"
    }
  ],
  "action": "C",
  "recorded": "2025-06-14T11:41:13.865+02:00",
  "outcome": "0",
  "agent": [
    {
      "type": {
        "coding": [
          {
            "system": "http://dicom.nema.org/resources/ontology/DCM",
            "code": "110153",
            "display": "Source Role ID"
          }
        ]
      },
      "who": {
        "display": "127.0.0.1"
      },
      "requestor": false,
      "network": {
        "address": "127.0.0.1",
        "type": "2"
      }
    },
    {
      "type": {
        "coding": [
          {
            "system": "http://dicom.nema.org/resources/ontology/DCM",
            "code": "110152",
            "display": "Destination Role ID"
          }
        ]
      },
      "who": {
        "display": "http://localhost:8080/fhir"
      },
      "requestor": false,
      "network": {
        "address": "http://localhost:8080/fhir"
      }
    },
    {
      "type": {
        "coding": [
          {
            "system": "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
            "code": "IRCP",
            "display": "information recipient"
          }
        ]
      },
      "requestor": true
    }
  ],
  "source": {
    "observer": {
      "display": "http://localhost:8080/fhir"
    }
  },
  "entity": [
    {
      "what": {
        "identifier": {
          "value": "TwefYTHrBTyMdjc0"
        }
      },
      "type": {
        "system": "https://profiles.ihe.net/ITI/BALP/CodeSystem/BasicAuditEntityType",
        "code": "XrequestId"
      }
    },
    {
      "what": {
        "reference": "Patient/1/_history/1"
      },
      "type": {
        "system": "http://terminology.hl7.org/CodeSystem/audit-entity-type",
        "code": "2",
        "display": "System Object"
      },
      "role": {
        "system": "http://terminology.hl7.org/CodeSystem/object-role",
        "code": "4",
        "display": "Domain Resource"
      }
    },
    {
      "what": {
        "reference": "Patient/1/_history/1"
      },
      "type": {
        "system": "http://terminology.hl7.org/CodeSystem/audit-entity-type",
        "code": "1",
        "display": "Person"
      },
      "role": {
        "system": "http://terminology.hl7.org/CodeSystem/object-role",
        "code": "1",
        "display": "Patient"
      }
    }
  ]
}
```
