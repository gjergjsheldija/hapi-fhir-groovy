package com.gjergjsheldija.scripting

import ca.uhn.fhir.rest.annotation.Operation
import ca.uhn.fhir.rest.annotation.OperationParam
import ca.uhn.fhir.rest.api.MethodOutcome
import com.gjergjsheldija.scripting.api.CustomScript
import com.gjergjsheldija.scripting.api.Provider
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.StringType

@CustomScript
@Provider
class HealthCheck {

    @Operation(name = "healthcheck")
    MethodOutcome healthcheck(@OperationParam(name = "name", typeName = "string") StringType name) {
        println("========>")
        println("Incoming parameters : " + name)

        def parameters = new Parameters()

        parameters.addParameter(new Parameters.ParametersParameterComponent()
                .setName("parameter")
                .setValue(name))

        def methodOutcome = new MethodOutcome()
        methodOutcome.setResponseStatusCode(204)
        methodOutcome.setResource(parameters)
        methodOutcome.setResponseHeaders(["Custom-Header": "CustomValue"])

        methodOutcome.setCreated(true)
        println(methodOutcome.getResponseStatusCode())

        return methodOutcome;
    }
}