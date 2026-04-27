package com.gjergjsheldija.dynamic;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DynamicResourceTest extends BaseFhirR4ForTesting {

    protected static IGenericClient ourClient;
    protected static FhirContext ourCtx;

    @Autowired
    private FhirContext myFhirContext;

    @Autowired
    private DynamicResourceRegistry dynamicResourceRegistry;

    @BeforeEach
    void beforeEach() {
        ourCtx = myFhirContext;
        ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        String ourServerBase = "http://localhost:" + port + "/fhir/";
        ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
    }

    @Test
    void testBeanExists() {
        assertNotNull(dynamicResourceRegistry);
    }

    @Test
    void testDynamicResourceCreated() throws Exception {
        // The resource "CustomResource" should have been registered by DynamicResourceRegistry
        // since we added custom-resources.yaml to the classpath (src/main/resources)
        
        // We use a generic resource representation to test it
        String resourceName = "CustomResource";
        
        // Retrieve the class from FhirContext since it was registered dynamically
        Class<? extends IBaseResource> customResClass = (Class<? extends IBaseResource>) ourCtx.getResourceDefinition(resourceName).getImplementingClass();
        
        IBaseResource instance = customResClass.getDeclaredConstructor().newInstance();
        
        // Use reflection to set fields since we don't have the class at compile time
        instance.getClass().getMethod("setTitle", String.class).invoke(instance, "Dynamic Title");
        instance.getClass().getMethod("setContent", String.class).invoke(instance, "Dynamic Content");

        MethodOutcome outcome = ourClient.create().resource(instance).execute();
        assertNotNull(outcome.getId());

        // Search for it
        Bundle results = ourClient.search()
                .forResource(resourceName)
                .returnBundle(Bundle.class)
                .execute();

        assertEquals(1, results.getEntry().size());
        IBaseResource found = results.getEntry().get(0).getResource();
        
        String foundTitle = (String) found.getClass().getMethod("getTitle").invoke(found);
        assertEquals("Dynamic Title", foundTitle);
    }
}
