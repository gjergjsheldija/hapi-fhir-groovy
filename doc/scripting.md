# Scripting

## General overview

Scripting adds the ability to add [Interceptors](https://hapifhir.io/hapi-fhir/docs/interceptors/interceptors.html) or
custom [Providers](https://hapifhir.io/hapi-fhir/docs/server_plain/resource_providers.html) to a running server. The
scripts
are written in [Groovy](https://groovy-lang.org/). They provide an extension mechanism for the server, without having
restart it. Scrips are contained in a single file, which is stored in the `Configuration` resource.

## Storing a script

Scripts are stored in the `Configuration` resource the following way :

```json
{
  "resourceType": "Configuration",
  "name": "HealthCheck",
  // the name should be same as the Class name
  "defaultValue": "",
  // there is no default value in this case 
  "description": "the content of the configuration",
  // general description
  "status": "active",
  // status mus be active
  "type": "script",
  // type must be script
  "body": "<<the script body here>>"
}
```

Scripts look like :

```groovy
package scripting

import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import com.clinomic.scripting.api.CustomScript
import groovy.util.logging.Slf4j

@Slf4j
@Interceptor
@CustomScript
class PatientCreated {
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    void execute(org.hl7.fhir.instance.model.api.IBaseResource theResource) {
        if (theResource.getClass() == org.hl7.fhir.r4.model.Patient.class) {
            if (!theResource?.getIdElement()?.getIdPart()) {
                log.info("No patient id found for : " + theResource.getIdElement());
            }

            println(theResource.getIdElement())
        }
    }
}
```

The server provides the following API to load, unload and list scripts.

### unload

```json
POST Configuration/$unload-script
Content-Type: application/fhir+json

{
"resourceType": "Parameters",
"parameter": [
{
"name": "name",
"valueString": "HealthCheck" // the name of the script
}
]
}
```

### load

```json
POST /Configuration/$load-script
Content-Type: application/fhir+json

{
"resourceType": "Parameters",
"parameter": [
{
"name": "name",
"valueString": "HealthCheck"
}
]
}
```

### list

```json
GET Configuration/$list-scripts
Content-Type: application/fhir+json
```

## Writing scripts

Each script in order to be loaded correctly must use the custom annotation `@CustomScript`.
Scripts can be of two types Interceptors and Providers, that also is done via annotations :

| type        | annotation     | library                                          |
|-------------|----------------|--------------------------------------------------|
| Interceptor | `@Interceptor` | `import ca.uhn.fhir.interceptor.api.Interceptor` |
| Provider    | `@Provider`    | `import com.clinomic.scripting.api.Provider`     |

### Provider

The example below, creates a new endpoint `healthcheck`, which in this case accepts only `POST`.
It also accepts input via `Parameters`, and the parameter is called `name` and is of `StringType`.
The endpoint returns a `Parameters` with the value of the incoming data.
As noted above the Interceptor script uses two annotations `@Provider` and `@CustomScript`.

```groovy
package com.clinomic.scripting

import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.OperationParam
import com.clinomic.scripting.api.CustomScript
import com.clinomic.scripting.api.Provider
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.StringType

@CustomScript
@Provider
class HealthCheck {
    @Operation(name = "healthcheck")
    Parameters healthcheck(@OperationParam(name = "name", typeName = "string") StringType name) {
        println("Incoming parameters : " + name)

        def parameters = new Parameters()

        parameters.addParameter(new Parameters.ParametersParameterComponent()
                .setName("parameter")
                .setValue(name))

        return parameters
    }
}
```

### Interceptors

The example below, adds a new interceptor, that fires on `STORAGE_PRESTORAGE_RESOURCE_CREATED`. It checks that the
incoming
resource is of type `Patient` and in case it is, it logs to the standard server logs.
As noted above the Interceptor script uses two annotations `@Interceptor` and `@CustomScript`.

```groovy
package com.clinomic.scripting

import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import com.clinomic.scripting.api.CustomScript
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
@Interceptor
@CustomScript
class PatientCreated {
    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
    void execute(org.hl7.fhir.instance.model.api.IBaseResource theResource) {
        if (theResource.getClass() == org.hl7.fhir.r4.model.Patient.class) {
            if (!theResource?.getIdElement()?.getIdPart()) {
                log.info("No patient id found for : " + theResource.getIdElement());
            }
        }
    }
}
```

**Note** : `Parameters`, `Operation`, `StringType`, `STORAGE_PRESTORAGE_RESOURCE_CREATED`, `Patient` are documented in
the HAPI documentation [here](https://hapifhir.io/hapi-fhir/docs/)
