# Custom Configuration


## Description

The custom configuration is enabled by default via the use of Resource Provider.
It behaves as a standard FHIR resource, that means it also has a `_history` entry attached to it.
The url to access it is `/fhir/Configuration`.
The configuration looks like :

```json
{
    "resourceType": "Configuration",
    "name": "some name",
    "defaultValue" :"no default value",
    "body": "the content of the configuration",
    "status" : "active",
    "description" : "some long description",
    "type": "configuration"
}
```

the status can be :
- active
- inactive

type can be of :
- configuration
- script

## Usage

In order to use it the following SearchParameters must be in place :

**Name**

```json
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
```
 **Type**

```json
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
```

**Status**
```json
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
```
