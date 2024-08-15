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

        return parameters;
    }
}