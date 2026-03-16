/**
 * FHIR Server
 * <p>
 * Copyright (c) 2025, Gjergj Sheldija
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gjergj@sheldija.net>
 * @copyright 2025, Gjergj Sheldija
 * @license All rights reserved.
 * @since 2025-08-07
 */

package scripting

import ca.uhn.fhir.interceptor.api.Hook
import ca.uhn.fhir.interceptor.api.Interceptor
import ca.uhn.fhir.interceptor.api.Pointcut
import com.gjergjsheldija.scripting.api.CustomScript
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