package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.tx.NonTransactionalHapiTransactionService;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.cors.CorsConfiguration;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppProxyFilterUnitTest {

	@SuppressWarnings("unchecked")
	private static AppRouteRegistry registryWith(String url, String path, boolean isPublic) {
		IFhirResourceDao<App> dao = mock(IFhirResourceDao.class);
		App app = new App().setStatus("active")
			.setEndpoint(new App.AppEndpointComponent().setType("http-rpc").setUrl(url));
		App.AppOperationComponent op = new App.AppOperationComponent().setMethod("GET").setPath(path).setPublic(isPublic);
		op.setId("op1");
		app.addOperation(op);
		app.setId(new IdType("App", "a1", "1"));
		app.getMeta().setLastUpdated(new Date(1000));
		IBundleProvider bundle = mock(IBundleProvider.class);
		when(bundle.getAllResources()).thenReturn(List.<IBaseResource>of(app));
		when(dao.search(any(SearchParameterMap.class), any(RequestDetails.class))).thenReturn(bundle);
		AppRouteRegistry registry = new AppRouteRegistry(dao, new NonTransactionalHapiTransactionService(), mock(TaskScheduler.class), Duration.ofHours(1));
		registry.refresh();
		return registry;
	}

	@SuppressWarnings("unchecked")
	private static <T> ObjectProvider<T> provider(T value) {
		ObjectProvider<T> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(value);
		return provider;
	}

	private static MockHttpServletRequest get(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fhir/" + path);
		request.setServletPath("/fhir");
		request.setPathInfo("/" + path);
		return request;
	}

	@Test
	void securityOnWithoutValidatorFailsClosed() throws Exception {
		AppProxyFilter filter = new AppProxyFilter(registryWith("http://127.0.0.1:1", "$private", false), provider(null),
			provider(null), true, "", Duration.ofSeconds(1), Duration.ofSeconds(1), FhirContext.forR4(), 1024, 1024);

		MockHttpServletRequest request = get("$private");
		request.addHeader("Authorization", "Bearer good-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("OperationOutcome"));
		assertNull(chain.getRequest(), "chain must not be invoked");
	}

	private static CorsInterceptor corsAllowing(String origin) {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOrigin(origin);
		config.addAllowedMethod("GET");
		return new CorsInterceptor(config);
	}

	@Test
	void allowedOriginGetsCorsHeadersOnAppResponse() throws Exception {
		try (StubApp stub = new StubApp()) {
			stub.headers.put("Access-Control-Allow-Origin", "*");
			AppProxyFilter filter = new AppProxyFilter(registryWith(stub.url(), "$public", true), provider(null),
				provider(corsAllowing("https://app.example")), false, "", Duration.ofSeconds(1), Duration.ofSeconds(1),
				FhirContext.forR4(), 1024, 1024);
			MockHttpServletRequest request = get("$public");
			request.addHeader("Origin", "https://app.example");
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilter(request, response, new MockFilterChain());

			assertEquals(200, response.getStatus());
			assertEquals("https://app.example", response.getHeader("Access-Control-Allow-Origin"));
			assertEquals(1, stub.calls.get());
		}
	}

	@Test
	void disallowedOriginIsRejectedBeforeCallingApp() throws Exception {
		try (StubApp stub = new StubApp()) {
			AppProxyFilter filter = new AppProxyFilter(registryWith(stub.url(), "$public", true), provider(null),
				provider(corsAllowing("https://app.example")), false, "", Duration.ofSeconds(1), Duration.ofSeconds(1),
				FhirContext.forR4(), 1024, 1024);
			MockHttpServletRequest request = get("$public");
			request.addHeader("Origin", "https://evil.example");
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockFilterChain chain = new MockFilterChain();

			filter.doFilter(request, response, chain);

			assertEquals(403, response.getStatus());
			assertNull(response.getHeader("Access-Control-Allow-Origin"));
			assertEquals(0, stub.calls.get());
			assertNull(chain.getRequest(), "chain must not be invoked");
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, Integer.MAX_VALUE})
	void rejectsUnsafeBodyLimits(int limit) {
		AppRouteRegistry registry = registryWith("http://127.0.0.1:1", "$x", true);
		IllegalArgumentException request = assertThrows(IllegalArgumentException.class, () -> new AppProxyFilter(registry,
			provider(null), provider(null), false, "", Duration.ofSeconds(1), Duration.ofSeconds(1), FhirContext.forR4(), limit, 1024));
		assertTrue(request.getMessage().contains("hapi.fhir.apps.max-request-bytes"), request.getMessage());
		IllegalArgumentException response = assertThrows(IllegalArgumentException.class, () -> new AppProxyFilter(registry,
			provider(null), provider(null), false, "", Duration.ofSeconds(1), Duration.ofSeconds(1), FhirContext.forR4(), 1024, limit));
		assertTrue(response.getMessage().contains("hapi.fhir.apps.max-response-bytes"), response.getMessage());
	}
}
