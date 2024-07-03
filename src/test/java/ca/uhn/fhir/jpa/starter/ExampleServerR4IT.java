package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.cr.config.RepositoryConfig;
import ca.uhn.fhir.jpa.searchparam.config.NicknameServiceConfig;
import ca.uhn.fhir.jpa.starter.cr.CrProperties;
import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = {
		Application.class,
		NicknameServiceConfig.class,
		RepositoryConfig.class
	}, properties = {
	"spring.profiles.include=storageSettingsTest",
	"spring.datasource.url=jdbc:h2:mem:dbr4",
	"hapi.fhir.enable_repository_validating_interceptor=true",
	"hapi.fhir.fhir_version=r4",
	//"hapi.fhir.subscription.websocket_enabled=true",
	//"hapi.fhir.mdm_enabled=true",
	"hapi.fhir.cr.enabled=true",
	"hapi.fhir.cr.caregaps_section_author=Organization/alphora-author",
	"hapi.fhir.cr.caregaps_reporter=Organization/alphora",
	"hapi.fhir.implementationguides.dk-core.name=hl7.fhir.dk.core",
	"hapi.fhir.implementationguides.dk-core.version=1.1.0",
	"hapi.fhir.auto_create_placeholder_reference_targets=true",
	// Override is currently required when using MDM as the construction of the MDM
	// beans are ambiguous as they are constructed multiple places. This is evident
	// when running in a spring boot environment
	"spring.main.allow-bean-definition-overriding=true"})
class ExampleServerR4IT implements IServerSupport {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ExampleServerR4IT.class);
	private IGenericClient ourClient;
	private FhirContext ourCtx;

	@Autowired
	private CrProperties crProperties;

	@LocalServerPort
	private int port;

	@Test
	@Order(0)
	void testCreateAndRead() {
		String methodName = "testCreateAndRead";
		ourLog.info("Entering " + methodName + "()...");

		Patient pt = new Patient();
		pt.setActive(true);
		pt.getBirthDateElement().setValueAsString("2020-01-01");
		pt.addIdentifier().setSystem("http://foo").setValue("12345");
		pt.addName().setFamily(methodName);
		IIdType id = ourClient.create().resource(pt).execute().getId();

		Patient pt2 = ourClient.read().resource(Patient.class).withId(id).execute();
		assertEquals(methodName, pt2.getName().get(0).getFamily());

	}

	private org.hl7.fhir.r4.model.Bundle loadBundle(String theLocation, FhirContext theCtx, IGenericClient theClient) throws IOException {
		String json = stringFromResource(theLocation);
		org.hl7.fhir.r4.model.Bundle bundle = (org.hl7.fhir.r4.model.Bundle) theCtx.newJsonParser().parseResource(json);
		org.hl7.fhir.r4.model.Bundle result = theClient.transaction().withBundle(bundle).execute();
		return result;
	}

	@Test
	void testBatchPutWithIdenticalTags() {
		String batchPuts = "{\n" +
			"\t\"resourceType\": \"Bundle\",\n" +
			"\t\"id\": \"patients\",\n" +
			"\t\"type\": \"batch\",\n" +
			"\t\"entry\": [\n" +
			"\t\t{\n" +
			"\t\t\t\"request\": {\n" +
			"\t\t\t\t\"method\": \"PUT\",\n" +
			"\t\t\t\t\"url\": \"Patient/pat-1\"\n" +
			"\t\t\t},\n" +
			"\t\t\t\"resource\": {\n" +
			"\t\t\t\t\"resourceType\": \"Patient\",\n" +
			"\t\t\t\t\"id\": \"pat-1\",\n" +
			"\t\t\t\t\"meta\": {\n" +
			"\t\t\t\t\t\"tag\": [\n" +
			"\t\t\t\t\t\t{\n" +
			"\t\t\t\t\t\t\t\"system\": \"http://mysystem.org\",\n" +
			"\t\t\t\t\t\t\t\"code\": \"value2\"\n" +
			"\t\t\t\t\t\t}\n" +
			"\t\t\t\t\t]\n" +
			"\t\t\t\t}\n" +
			"\t\t\t},\n" +
			"\t\t\t\"fullUrl\": \"/Patient/pat-1\"\n" +
			"\t\t},\n" +
			"\t\t{\n" +
			"\t\t\t\"request\": {\n" +
			"\t\t\t\t\"method\": \"PUT\",\n" +
			"\t\t\t\t\"url\": \"Patient/pat-2\"\n" +
			"\t\t\t},\n" +
			"\t\t\t\"resource\": {\n" +
			"\t\t\t\t\"resourceType\": \"Patient\",\n" +
			"\t\t\t\t\"id\": \"pat-2\",\n" +
			"\t\t\t\t\"meta\": {\n" +
			"\t\t\t\t\t\"tag\": [\n" +
			"\t\t\t\t\t\t{\n" +
			"\t\t\t\t\t\t\t\"system\": \"http://mysystem.org\",\n" +
			"\t\t\t\t\t\t\t\"code\": \"value2\"\n" +
			"\t\t\t\t\t\t}\n" +
			"\t\t\t\t\t]\n" +
			"\t\t\t\t}\n" +
			"\t\t\t},\n" +
			"\t\t\t\"fullUrl\": \"/Patient/pat-2\"\n" +
			"\t\t}\n" +
			"\t]\n" +
			"}";
		Bundle bundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, batchPuts);
		ourClient.transaction().withBundle(bundle).execute();
	}


	private int activeSubscriptionCount() {
		return ourClient.search().forResource(Subscription.class).where(Subscription.STATUS.exactly().code("active"))
			.cacheControl(new CacheControlDirective().setNoCache(true)).returnBundle(Bundle.class).execute().getEntry()
			.size();
	}

	@BeforeEach
	void beforeEach() {

		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		String ourServerBase = "http://localhost:" + port + "/fhir/";
		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
		//await().atMost(2, TimeUnit.MINUTES).until(() -> {
		//	sleep(1000); // execute below function every 1 second
		//	return activeSubscriptionCount() == 2; // 2 subscription based on mdm-rules.json
		//});
	}
}
