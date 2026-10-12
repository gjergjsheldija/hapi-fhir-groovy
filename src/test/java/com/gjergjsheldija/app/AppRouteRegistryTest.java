package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppRouteRegistryTest extends BaseFhirR4ForTesting {

	@Autowired
	IFhirResourceDao<App> appDao;

	@Autowired
	AppRouteRegistry registry;

	@BeforeEach
	void setUpClient() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
		registry.refresh();
	}

	@AfterEach
	void tearDown() {
		cleanUp();
		registry.refresh();
	}

	private static App app(String id, String status, String url, String path) {
		App app = new App()
			.setStatus(status)
			.setEndpoint(new App.AppEndpointComponent().setType("http-rpc").setUrl(url));
		App.AppOperationComponent op = new App.AppOperationComponent().setMethod("GET").setPath(path);
		op.setId("op1");
		app.addOperation(op);
		app.setId(new IdType("App", id));
		return app;
	}

	/** Simulates a write made on another node: DAO call, no REST request, so no local hook fires. */
	private void writeViaDao(App app) {
		appDao.update(app, new SystemRequestDetails());
	}

	@Test
	void refreshPicksUpWritesFromOtherNodes() {
		writeViaDao(app("a1", "active", "http://127.0.0.1:1", "Patient/$r/{date}"));
		assertTrue(registry.find("GET", List.of("Patient", "$r", "2024-01-01")).isEmpty(), "not visible before refresh");

		assertTrue(registry.refresh());
		AppRouteRegistry.Match match = registry.find("GET", List.of("Patient", "$r", "2024-01-01")).orElseThrow();
		assertEquals("a1", match.route().appId());
		assertEquals("op1", match.route().operationId());
		assertEquals(Map.of("date", "2024-01-01"), match.routeParams());

		assertFalse(registry.refresh(), "unchanged data must not rebuild");
	}

	@Test
	void inactiveAndDeletedAppsAreNotRouted() {
		writeViaDao(app("a1", "inactive", "http://127.0.0.1:1", "$x"));
		registry.refresh();
		assertTrue(registry.find("GET", List.of("$x")).isEmpty());

		writeViaDao(app("a1", "active", "http://127.0.0.1:1", "$x"));
		registry.refresh();
		assertTrue(registry.find("GET", List.of("$x")).isPresent());

		appDao.delete(new IdType("App", "a1"), new SystemRequestDetails());
		registry.refresh();
		assertTrue(registry.find("GET", List.of("$x")).isEmpty());
	}

	@Test
	void mostLiteralRouteWins() {
		writeViaDao(app("generic", "active", "http://127.0.0.1:1", "Patient/$r/{date}"));
		writeViaDao(app("specific", "active", "http://127.0.0.1:2", "Patient/$r/today"));
		registry.refresh();
		assertEquals("specific", registry.find("GET", List.of("Patient", "$r", "today")).orElseThrow().route().appId());
		assertEquals("generic", registry.find("GET", List.of("Patient", "$r", "2024-01-01")).orElseThrow().route().appId());
	}

	@Test
	void restWriteRefreshesImmediately() throws Exception {
		AppJson.put(port, "rest1", AppJson.app("rest1", "active", "http://127.0.0.1:1", null,
			AppJson.op("op1", "POST", "User/$register", true)));
		assertTrue(registry.find("POST", List.of("User", "$register")).isPresent());
	}

	@Test
	void expungeAndRecreateWithSameIdUsesNewEndpoint() {
		writeViaDao(app("same", "active", "http://127.0.0.1:1", "$x"));
		registry.refresh();
		cleanUp(); // $expunge everything: version counter restarts
		writeViaDao(app("same", "active", "http://127.0.0.1:2", "$x"));
		registry.refresh();
		assertEquals("http://127.0.0.1:2", registry.find("GET", List.of("$x")).orElseThrow().route().endpointUrl());
	}

	@Test
	void nullOrBlankMethodSkipsAppOthersStillRoute() {
		App noMethod = app("nomethod", "active", "http://127.0.0.1:1", "$a");
		noMethod.getOperation().get(0).setMethod(null);
		App blankMethod = app("blank", "active", "http://127.0.0.1:1", "$b");
		blankMethod.getOperation().get(0).setMethod(" ");
		App good = app("good", "active", "http://127.0.0.1:2", "$ok");
		Map<String, List<AppRoute>> routes = AppRouteRegistry.buildRoutes(List.of(noMethod, blankMethod, good));
		assertEquals(1, routes.size());
		assertEquals("good", routes.get("GET").get(0).appId());
	}

	@Test
	void invalidStoredAppIsSkippedOthersStillRoute() {
		App bad = app("bad", "active", "http://127.0.0.1:1", "a//b");
		App good = app("good", "active", "http://127.0.0.1:2", "$ok");
		Map<String, List<AppRoute>> routes = AppRouteRegistry.buildRoutes(List.of(bad, good));
		assertEquals(1, routes.get("GET").size());
		assertEquals("good", routes.get("GET").get(0).appId());
	}

	@Test
	void collidingAppsKeepOnlyTheOlderOne() {
		App older = app("older", "active", "http://127.0.0.1:1", "$x/{a}");
		older.getMeta().setLastUpdated(new java.util.Date(1000));
		App newer = app("newer", "active", "http://127.0.0.1:2", "$x/{b}");
		newer.getMeta().setLastUpdated(new java.util.Date(2000));
		Map<String, List<AppRoute>> routes = AppRouteRegistry.buildRoutes(List.of(newer, older));
		assertEquals(1, routes.get("GET").size());
		assertEquals("older", routes.get("GET").get(0).appId());
	}
}
