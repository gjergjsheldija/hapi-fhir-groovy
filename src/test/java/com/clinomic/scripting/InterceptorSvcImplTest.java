/**
 * FHIR Server
 * <p>
 * Copyright (c) 2024, Clinomic GmbH, Aachen
 * All rights reserved.
 *
 * @author Gjergj Sheldija <gsheldija@clinomic.ai>
 * @copyright 2024, Clinomic GmbH, Aachen
 * @license All rights reserved.
 * @since 2024-08-07
 */

package com.clinomic.scripting;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.clinomic.configuration.Configuration;
import com.clinomic.util.BaseFhirR4ForTesting;
import com.google.common.io.Resources;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.SearchParameter;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class InterceptorSvcImplTest extends BaseFhirR4ForTesting {

	@Autowired
	InterceptorSvcImpl interceptorSvcImpl;

	@Autowired
	IInterceptorService interceptorService;

	@BeforeEach
	void beforeEach() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
		String ourServerBase = "http://localhost:" + port + "/fhir/";
		ourClient = ourCtx.newRestfulGenericClient(ourServerBase);


		SearchParameter searchParameterName = new SearchParameter();
		searchParameterName.setCode("name")
			.addBase("ClioConfiguration")
			.setStatus(Enumerations.PublicationStatus.ACTIVE)
			.setType(Enumerations.SearchParamType.STRING)
			.setExpression("name");

		ourClient.create().resource(searchParameterName).execute();

		SearchParameter searchParameterType = new SearchParameter();
		searchParameterType.setCode("type")
			.addBase("ClioConfiguration")
			.setStatus(Enumerations.PublicationStatus.ACTIVE)
			.setType(Enumerations.SearchParamType.STRING)
			.setExpression("type");

		ourClient.create().resource(searchParameterType).execute();

		SearchParameter searchParameterStatus = new SearchParameter();
		searchParameterStatus.setCode("status")
			.addBase("ClioConfiguration")
			.setStatus(Enumerations.PublicationStatus.ACTIVE)
			.setType(Enumerations.SearchParamType.STRING)
			.setExpression("status");

		ourClient.create().resource(searchParameterStatus).execute();

		mySearchParamRegistry.forceRefresh();
	}

	@SneakyThrows
	@Test
	void testLoadUnloadScriptingInterceptors() {
		loadSourceCode();
		interceptorSvcImpl.loadCustomScript("PatientCreated", "package scripting\n" +
			"\n" +
			"import ca.uhn.fhir.interceptor.api.Hook\n" +
			"import ca.uhn.fhir.interceptor.api.Interceptor\n" +
			"import ca.uhn.fhir.interceptor.api.Pointcut\n" +
			"import com.clinomic.scripting.api.CustomScript\n" +
			"\n" +
			"@Interceptor\n" +
			"@CustomScript\n" +
			"class PatientCreated {\n" +
			"    @Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)\n" +
			"    void execute() {\n" +
			"        print(\"test\");\n" +
			"    }\n" +
			"}");

		List<Object> loadedInterceptors = interceptorService.getAllRegisteredInterceptors();
		assert (loadedInterceptors.size() == 2);

		interceptorSvcImpl.unloadCustomScript("PatientCreated");
		loadedInterceptors = interceptorService.getAllRegisteredInterceptors();
		assert (loadedInterceptors.size() == 1);
	}

	void loadSourceCode() throws IOException {
		InputStream source = Resources.getResource("scripting/PatientDemo.groovy").openStream();

		Configuration c = new Configuration();
		c.setName("test_script");
		c.setStatus(Configuration.ConfigurationStatus.ACTIVE);
		c.setType(Configuration.ConfigurationType.SCRIPT);
		c.setBody(new StringType(IOUtils.toString(source)));

		ourClient.create().resource(c).execute();
	}
}
