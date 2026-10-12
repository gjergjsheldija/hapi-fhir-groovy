package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppProxyFilterUnitTest {

	@SuppressWarnings("unchecked")
	@Test
	void securityOnWithoutValidatorFailsClosed() throws Exception {
		IFhirResourceDao<App> dao = mock(IFhirResourceDao.class);
		App app = new App().setStatus("active")
			.setEndpoint(new App.AppEndpointComponent().setType("http-rpc").setUrl("http://127.0.0.1:1"));
		App.AppOperationComponent op = new App.AppOperationComponent().setMethod("GET").setPath("$private");
		op.setId("op1");
		app.addOperation(op);
		app.setId(new IdType("App", "a1", "1"));
		app.getMeta().setLastUpdated(new Date(1000));
		IBundleProvider bundle = mock(IBundleProvider.class);
		when(bundle.getAllResources()).thenReturn(List.<IBaseResource>of(app));
		when(dao.search(any(SearchParameterMap.class), any(RequestDetails.class))).thenReturn(bundle);
		AppRouteRegistry registry = new AppRouteRegistry(dao, mock(TaskScheduler.class), Duration.ofHours(1));
		registry.refresh();

		ObjectProvider<AccessTokenValidation> noValidator = mock(ObjectProvider.class);
		when(noValidator.getIfAvailable()).thenReturn(null);
		AppProxyFilter filter = new AppProxyFilter(registry, noValidator, true, "", Duration.ofSeconds(1),
			Duration.ofSeconds(1), FhirContext.forR4(), 1024, 1024);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fhir/$private");
		request.setServletPath("/fhir");
		request.setPathInfo("/$private");
		request.addHeader("Authorization", "Bearer good-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("OperationOutcome"));
		assertNull(chain.getRequest(), "chain must not be invoked");
	}
}
