# Aidbox-style Apps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let external services in any language serve custom endpoints under `/fhir/...` by registering an `App` resource; the server routes matching requests to the service with an Aidbox-like JSON envelope and returns the response unchanged.

**Architecture:** A custom JPA-backed FHIR resource `App` holds operations (method + path template) and an endpoint. `AppRouteRegistry` keeps an immutable, periodically refreshed route table built from active Apps. `AppProxyFilter`, a servlet filter in front of the HAPI servlet, matches requests against it, checks the JWT unless the operation is public, POSTs the envelope to the app with the JDK `HttpClient`, and copies the response back. Non-matching requests fall through to HAPI untouched.

**Tech Stack:** Java 17, Spring Boot 3.5.9, HAPI FHIR 8.8.0 (JPA server, R4 structures), Jackson, `java.net.http.HttpClient`, JUnit 5, `com.sun.net.httpserver.HttpServer` for stubs, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-11-aidbox-style-apps-design.md`

## Global Constraints

- Compile target Java 17 (`<java.version>17</java.version>`); tabs for indentation (`.editorconfig`: `indent_style = tab`).
- No new runtime or test dependencies.
- New code in package `com.gjergjsheldija.app`; tests mirror it under `src/test/java/com/gjergjsheldija/app`.
- Constructor injection with `private final` fields in all new classes; one SLF4J logger per class named after the class; no `System.out` / `printStackTrace()`.
- Never log request/response bodies, headers, tokens, or `endpoint.secret`.
- Errors returned to clients are FHIR `OperationOutcome` JSON (`application/fhir+json`).
- Endpoint type: `http-rpc` only. Methods: `GET POST PUT DELETE PATCH`. Status: `active | inactive`.
- Config keys and defaults: `hapi.fhir.apps.refresh-ms: 30000`, `hapi.fhir.apps.connect-timeout-ms: 5000`, `hapi.fhir.apps.request-timeout-ms: 30000`.
- Proxy error statuses: unreachable/IO → 502, request timeout → 504, missing/invalid token on non-public op → 401, invalid App write → 422.
- Commits, PRs and issues carry **no AI attribution** (no `Co-Authored-By`, no "Generated with"). Commit style: imperative summary + `(#<issue>)`.
- Changes to existing tests go in their own commit with the reason in the message; never weaken a test to pass.

## Review Focus

- **Expunge-and-recreate with the same id**: after `$expunge`, re-creating `App/x` restarts at version 1, so a route table keyed on `(id, versionId)` alone would keep serving the old endpoint. Expected: the new endpoint is used. → Registry keys on `(id, versionId, lastUpdated)`; test in Task 5.
- **Form-encoded POST bodies**: calling `request.getParameterMap()` on `application/x-www-form-urlencoded` consumes the body, so the app would receive an empty `resource`. Expected: body forwarded intact, query params parsed from the query string only. → Test in Task 7.
- **Repeated query params** (`?code=a&code=b`): expected both values forwarded. → Envelope uses arrays; test in Task 7.
- **App writes with request validation enabled**: `CustomRequestValidatingInterceptor` validates everything except `Configuration`; an `App` body would be rejected as an unknown type once wiring is restored. Expected: `App` writes accepted. → Skip `App` there; test in Task 3.
- **One invalid stored App** (written before validation existed, or by an old node): expected the other Apps keep routing. → Registry skips the bad App with a warning; test in Task 5.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `.github/workflows/ci.yml` | Run `mvn -B verify` on PRs and `main` | 1 |
| `src/test/java/com/gjergjsheldija/scripting/OutputCalculationUnitTest.java` | Disable 3 known-broken tests | 1 |
| `src/main/resources/application.yaml` | Restore env bindings + extension wiring; `hapi.fhir.apps.*` keys | 2, 5, 7 |
| `src/test/java/com/gjergjsheldija/ExtensionWiringTest.java` | Boots with the real `application.yaml`; asserts extensions registered | 2 |
| `src/main/java/com/gjergjsheldija/app/App.java` | Custom resource model | 3 |
| `src/main/java/com/gjergjsheldija/app/AppResourceProvider.java` | REST CRUD + search for `App` | 3 |
| `src/main/java/com/gjergjsheldija/app/AppBean.java` | Registers type, DAO, provider, registry, validation, filter | 3, 5, 6, 7 |
| `src/main/java/com/gjergjsheldija/validation/CustomRequestValidatingInterceptor.java` | Also skip `App` | 3 |
| `src/main/java/com/gjergjsheldija/app/AppRoute.java` | Path template parse/match/collision (pure) | 4 |
| `src/main/java/com/gjergjsheldija/app/AppRouteRegistry.java` | Route snapshot, refresh, lookup | 5 |
| `src/main/java/com/gjergjsheldija/app/AppValidationInterceptor.java` | 422 on invalid App writes | 6 |
| `src/main/java/com/gjergjsheldija/app/AppEnvelope.java` | Build the JSON envelope (pure) | 7 |
| `src/main/java/com/gjergjsheldija/app/AppProxyFilter.java` | Match, auth, forward, copy response | 7 |
| `src/test/java/com/gjergjsheldija/util/BaseFhirR4ForTesting.java` | Add `com.gjergjsheldija.app` + app test props | 3 |
| `src/test/java/com/gjergjsheldija/app/*` | Tests + `StubApp` + `AppJson` helpers | 3–7 |
| `doc/apps.md` | Feature documentation | 7 |

---

### Task 1: CI workflow and a green baseline

`main` currently fails `mvn verify`: `OutputCalculationUnitTest` has 3 tests whose calls and assertions were commented out in `700f787` (they were not working), leaving Mockito stubs that trip `UnnecessaryStubbingException`. Owner decision: disable them and track the fix separately.

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `src/test/java/com/gjergjsheldija/scripting/OutputCalculationUnitTest.java` (the three `@Test` methods `testInProgressEncounterCalculations`, `testStartFromAndDateToAreBeforeEncounterStart`, `testStartFromAndDateToAreAfterEncounterEnd`)

**Interfaces:**
- Consumes: nothing.
- Produces: a required status check named `verify` for later PRs.

- [ ] **Step 1: Open the follow-up issue for the broken tests**

```bash
gh issue create --title "Fix OutputCalculationUnitTest (assertions commented out in 700f787)" \
  --label bug \
  --body "The three tests in OutputCalculationUnitTest had their calls/assertions commented out in 700f787 because they were failing. They are now @Disabled so CI can run. Restore the assertions and fix OutputCalculation (or the fixtures) so they pass."
```

Note the issue number as `<N>`.

- [ ] **Step 2: Confirm the failure locally**

Run: `mvn -B test -Dtest=OutputCalculationUnitTest`
Expected: `Tests run: 3, Failures: 0, Errors: 3` with `UnnecessaryStubbingException`.

- [ ] **Step 3: Disable the three tests**

Add the import `import org.junit.jupiter.api.Disabled;` (alphabetical position among `org.junit.jupiter.api.*` imports) and annotate each of the three test methods:

```java
	@Disabled("Assertions commented out in 700f787 because they fail; tracked in #<N>")
	@Test
	void testInProgressEncounterCalculations() {
```

(Same annotation on `testStartFromAndDateToAreBeforeEncounterStart` and `testStartFromAndDateToAreAfterEncounterEnd`; keep each method's existing signature and body.)

- [ ] **Step 4: Verify**

Run: `mvn -B test -Dtest=OutputCalculationUnitTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 3`.

- [ ] **Step 5: Commit the test change on its own**

```bash
git add src/test/java/com/gjergjsheldija/scripting/OutputCalculationUnitTest.java
git commit -m "Disable broken OutputCalculationUnitTest cases until fixed (#<N>)

Their assertions were commented out in 700f787 because they fail; the
leftover stubs make Mockito error. Disabled so CI can run; #<N> tracks
restoring them."
```

- [ ] **Step 6: Add the CI workflow**

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - name: Build and test
        run: mvn -B verify
```

- [ ] **Step 7: Run the full build locally**

Run: `mvn -B verify`
Expected: `BUILD SUCCESS`. If any other test fails, stop: that is a pre-existing failure — record it in the PR and label the issue `needs-human`.

- [ ] **Step 8: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "Add CI workflow running mvn verify (#<this issue>)"
```

- [ ] **Step 9: Owner action (not automatable by agents)**

After this PR merges, the owner enables branch protection on `main` requiring the `verify` check. Agents must not merge any later PR until this is confirmed.

---

### Task 2: Restore env bindings and extension wiring

Commits `10b7250..700f787` replaced `application.yaml` with the upstream version, dropping every `${ENV}` binding (the server now ignores `.env`, including `SQL_URL` — production would run on in-memory H2) and the `custom-*` wiring (so no `com.gjergjsheldija` extension loads). Restore both, keeping H2 as the local default.

**Files:**
- Modify: `src/main/resources/application.yaml`
- Create: `src/test/java/com/gjergjsheldija/ExtensionWiringTest.java`

**Interfaces:**
- Consumes: Task 1 CI.
- Produces: `hapi.fhir.custom-bean-packages` includes `com.gjergjsheldija`; the "Z. Extensions" YAML block that Tasks 5 and 7 add `apps:` keys to.

- [ ] **Step 1: Write the failing smoke test**

`src/test/java/com/gjergjsheldija/ExtensionWiringTest.java`:

```java
package com.gjergjsheldija;

import ca.uhn.fhir.jpa.starter.Application;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.gjergjsheldija.auditevent.AuditEventInterceptor;
import com.gjergjsheldija.logging.CustomLoggingInterceptor;
import com.gjergjsheldija.scripting.ScriptingProvider;
import com.gjergjsheldija.security.SecurityInterceptor;
import com.gjergjsheldija.terminology.TerminologyCapabilityInterceptor;
import com.gjergjsheldija.validation.CustomRequestValidatingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots with the real src/main/resources/application.yaml (not the test one) so a broken
 * or stripped production config fails the build.
 */
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	classes = {Application.class},
	properties = {
		"spring.config.location=file:src/main/resources/application.yaml",
		"spring.datasource.url=jdbc:h2:mem:wiring",
		"management.server.port=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExtensionWiringTest {

	@Autowired
	RestfulServer restfulServer;

	@Test
	void extensionInterceptorsAreRegistered() {
		List<Object> interceptors = restfulServer.getInterceptorService().getAllRegisteredInterceptors();
		for (Class<?> expected : List.of(
			CustomLoggingInterceptor.class,
			AuditEventInterceptor.class,
			SecurityInterceptor.class,
			TerminologyCapabilityInterceptor.class,
			CustomRequestValidatingInterceptor.class)) {
			assertTrue(interceptors.stream().anyMatch(expected::isInstance), "missing interceptor " + expected.getSimpleName());
		}
	}

	@Test
	void scriptingProviderIsRegistered() {
		assertTrue(restfulServer.getPlainProviders().stream().anyMatch(ScriptingProvider.class::isInstance));
	}
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -B test -Dtest=ExtensionWiringTest`
Expected: FAIL — `missing interceptor CustomLoggingInterceptor` (nothing is wired).

- [ ] **Step 3: Restore datasource env bindings**

In `src/main/resources/application.yaml` replace

```yaml
    url: jdbc:h2:mem:test_mem
    username: sa
    password: null
    driver-class-name: org.h2.Driver
```

with

```yaml
    url: ${SQL_URL:jdbc:h2:mem:test_mem}
    username: ${SQL_USERNAME:sa}
    password: ${SQL_PASSWORD:}
    driver-class-name: ${SQL_DRIVER:org.h2.Driver}
```

and replace

```yaml
        dialect: ca.uhn.fhir.jpa.model.dialect.HapiFhirH2Dialect
```

with

```yaml
        dialect: ${SQL_DIALECT:ca.uhn.fhir.jpa.model.dialect.HapiFhirH2Dialect}
```

- [ ] **Step 4: Restore Clinical Reasoning toggle**

Replace

```yaml
      enabled: false                            # exposes Clinical Reasoning operation endpoints
```

with

```yaml
      enabled: ${CLINICAL_RESEARCH_ENABLED:false}   # exposes Clinical Reasoning operation endpoints
```

- [ ] **Step 5: Restore wiring and extension config**

Replace

```yaml
    # custom-bean-packages:
    # custom-interceptor-classes:
    # custom-provider-classes:
```

with

```yaml
    custom-bean-packages: ca.uhn.fhir.jpa.starter, com.gjergjsheldija

    # classes listed here will be fetched from the Spring context when combined with 'custom-bean-packages',
    # or will be instantiated via reflection using an no-arg constructor; then registered with the server
    custom-interceptor-classes:
      - com.gjergjsheldija.logging.CustomLoggingInterceptor
      - ca.uhn.fhir.jpa.subscription.util.SubscriptionDebugLogInterceptor
      - com.gjergjsheldija.auditevent.AuditEventInterceptor
      - com.gjergjsheldija.security.SecurityInterceptor
      - com.gjergjsheldija.terminology.TerminologyCapabilityInterceptor
      - com.gjergjsheldija.validation.CustomRequestValidatingInterceptor

    custom-provider-classes:
      - com.gjergjsheldija.scripting.ScriptingProvider

    # -------------------------------------------------------------------------------
    # Z. Extensions (com.gjergjsheldija) — values come from .env in docker-compose
    # -------------------------------------------------------------------------------
    validation:
      requests_enabled: ${REQUEST_VALIDATION:false}
      responses_enabled: ${RESPONSE_VALIDATION:false}
    subscription:
      resthook_enabled: ${SUBSCRIPTION_REST_ENABLED:false}
      websocket_enabled: ${SUBSCRIPTION_WEBSOCKET_ENABLED:false}
    cache_type: ${CACHE_TYPE:DATABASE}                                            # INFINISPAN (case-insensitive) stores search results in Infinispan
    retain_cached_searches_mins: ${RETAIN_CACHED_SEARCHES_MINS:0}
    reuse_cached_search_results_millis: ${REUSE_CACHED_SEARCH_RESULTS_MILLIS:0}   # 0 turns off search result reuse
    audit_event: ${AUDIT_EVENT:false}
    log_request_body: ${LOG_REQUEST_BODY:false}
    dao-scheduling-enabled: ${JOB_SCHEDULING:true}
    security:
      enabled: ${SECURITY_ENABLED:false}
      server: ${SECURITY_SERVER:http://localhost:8181/realms/clinomic}
      jwks: ${SECURITY_JWKS:http://localhost:8181/realms/clinomic/protocol/openid-connect/certs}
```

- [ ] **Step 6: Restore the Infinispan block**

Append at the end of the file (top level, no indentation):

```yaml

searchcache:
  infinispan:
    create.cache: ${INFINISPAN_CREATE_CACHE:false}
    cache.name: ${INFINISPAN_CACHE_NAME:SearchResultsCache}
    servers: ${INFINISPAN_ADDRESS:127.0.0.1}:${INFINISPAN_PORT:11222}
    protocol_version: PROTOCOL_VERSION_23
    enable_security: ${INFINISPAN_SECURITY_ENABLED:false}
    username: ${INFINISPAN_USER:admin}
    password: ${INFINISPAN_PASSWORD:password}
```

- [ ] **Step 7: Check no env var from `.env` is unbound**

Run:

```bash
for v in $(cut -d= -f1 .env | grep -E '^[A-Z_]+$'); do grep -q "\${$v" src/main/resources/application.yaml || echo "unbound: $v"; done
```

Expected output only: `APPLICATION_NAME`, `SERVER_ADDRESS`, `OPENSEARCH_ENABLED`, `OPENSEARCH_URL`, `OPENSEARCH_USERNAME`, `OPENSEARCH_PASSWORD`, `SUBSCRIPTION_EMAIL_ENABLED`, `JAVA_OPTS`, `DOCKER_REGISTRY` (none of these were bound before `700f787` either; they are consumed by compose/Makefile or unused).

- [ ] **Step 8: Run the smoke test**

Run: `mvn -B test -Dtest=ExtensionWiringTest`
Expected: PASS. If the context fails to start, fix the cause in config (missing key/bean), not by removing an extension from the list.

- [ ] **Step 9: Run the full build**

Run: `mvn -B verify`
Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/application.yaml src/test/java/com/gjergjsheldija/ExtensionWiringTest.java
git commit -m "Restore env bindings and extension wiring dropped in 700f787 (#<issue>)"
```

PR body must state the runtime impact: the server again honours `.env` (Postgres in compose), and JWT security, BALP audit, logging, terminology, validation and scripting are active again per their env flags.

---

### Task 3: `App` resource with REST CRUD

**Files:**
- Create: `src/main/java/com/gjergjsheldija/app/App.java`
- Create: `src/main/java/com/gjergjsheldija/app/AppResourceProvider.java`
- Create: `src/main/java/com/gjergjsheldija/app/AppBean.java`
- Modify: `src/main/java/com/gjergjsheldija/validation/CustomRequestValidatingInterceptor.java` (the `Configuration` skip)
- Modify: `src/test/java/com/gjergjsheldija/util/BaseFhirR4ForTesting.java` (properties)
- Create: `src/test/java/com/gjergjsheldija/app/AppJson.java`
- Create: `src/test/java/com/gjergjsheldija/app/AppResourceTest.java`
- Modify: `src/test/java/com/gjergjsheldija/validation/CustomRequestValidatingInterceptorTest.java`

**Interfaces:**
- Consumes: Task 2 wiring (`custom-bean-packages` includes `com.gjergjsheldija`).
- Produces:
  - `App` with `String getStatus()`, `App setStatus(String)`, `AppEndpointComponent getEndpoint()`, `App setEndpoint(AppEndpointComponent)`, `List<AppOperationComponent> getOperation()`, `App addOperation(AppOperationComponent)`; constants `App.STATUS_ACTIVE = "active"`, `App.STATUS_INACTIVE = "inactive"`, `App.ENDPOINT_TYPE_HTTP_RPC = "http-rpc"`.
  - `App.AppEndpointComponent`: `String getType()`, `String getUrl()`, `String getSecret()` (null when absent) and fluent setters `setType/setUrl/setSecret(String)`.
  - `App.AppOperationComponent`: `String getId()` (the FHIR element id = operation id), `String getMethod()`, `String getPath()`, `boolean isPublic()` and fluent setters `setId(String)` (inherited), `setMethod/setPath(String)`, `setPublic(boolean)`.
  - Spring bean `IFhirResourceDao<App>` named `myAppDao`.
  - Test helper `AppJson.app(String id, String status, String endpointUrl, String secret, String operationsJson)` and `AppJson.op(String id, String method, String path, boolean isPublic)`; `AppJson.put(int port, String id, String json)` returns `HttpResponse<String>`.

- [ ] **Step 1: Add the app package and test properties to the test base**

In `src/test/java/com/gjergjsheldija/util/BaseFhirR4ForTesting.java` change the `custom-bean-packages` property and add two properties (keep all other properties as they are):

```java
		"hapi.fhir.custom-bean-packages=com.gjergjsheldija.configuration,com.gjergjsheldija.logging,com.gjergjsheldija.scripting,com.gjergjsheldija.app",
		"hapi.fhir.apps.refresh-ms=3600000",
		"hapi.fhir.apps.request-timeout-ms=2000",
```

(`refresh-ms` is set high so the periodic refresh never races a test; tests call `refresh()` explicitly.)

- [ ] **Step 2: Create the JSON/HTTP test helper**

`src/test/java/com/gjergjsheldija/app/AppJson.java`:

```java
package com.gjergjsheldija.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

final class AppJson {

	static final HttpClient CLIENT = HttpClient.newHttpClient();

	private AppJson() {
	}

	static String op(String id, String method, String path, boolean isPublic) {
		return """
			{ "id": "%s", "method": "%s", "path": "%s", "public": %s }""".formatted(id, method, path, isPublic);
	}

	static String app(String id, String status, String endpointUrl, String secret, String operationsJson) {
		String secretJson = secret == null ? "" : ", \"secret\": \"%s\"".formatted(secret);
		return """
			{
			  "resourceType": "App",
			  "id": "%s",
			  "status": "%s",
			  "endpoint": { "type": "http-rpc", "url": "%s"%s },
			  "operation": [ %s ]
			}""".formatted(id, status, endpointUrl, secretJson, operationsJson);
	}

	static HttpResponse<String> put(int port, String id, String json) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/fhir/App/" + id))
			.header("Content-Type", "application/fhir+json")
			.PUT(HttpRequest.BodyPublishers.ofString(json))
			.build();
		return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	static HttpResponse<String> send(int port, String method, String pathAndQuery, String contentType, String body, String authorization)
		throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/fhir/" + pathAndQuery))
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		if (contentType != null) {
			builder.header("Content-Type", contentType);
		}
		if (authorization != null) {
			builder.header("Authorization", authorization);
		}
		return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
}
```

- [ ] **Step 3: Write the failing round-trip test**

`src/test/java/com/gjergjsheldija/app/AppResourceTest.java`:

```java
package com.gjergjsheldija.app;

import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppResourceTest extends BaseFhirR4ForTesting {

	@BeforeEach
	void setUpClient() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	@Test
	void putReadAndSearchRoundTrip() throws Exception {
		String json = AppJson.app("myorg.myapp", "active", "http://127.0.0.1:9999", "s3cr3t",
			AppJson.op("daily-patient-report", "GET", "Patient/$daily-report/{date}", false) + ","
				+ AppJson.op("register-user", "POST", "User/$register", true));

		HttpResponse<String> put = AppJson.put(port, "myorg.myapp", json);
		assertEquals(201, put.statusCode(), put.body());

		HttpResponse<String> read = AppJson.send(port, "GET", "App/myorg.myapp", null, null, null);
		assertEquals(200, read.statusCode(), read.body());
		App app = ourCtx.newJsonParser().parseResource(App.class, read.body());
		assertEquals("active", app.getStatus());
		assertEquals("http-rpc", app.getEndpoint().getType());
		assertEquals("http://127.0.0.1:9999", app.getEndpoint().getUrl());
		assertEquals("s3cr3t", app.getEndpoint().getSecret());
		assertEquals(2, app.getOperation().size());
		assertEquals("daily-patient-report", app.getOperation().get(0).getId());
		assertEquals("GET", app.getOperation().get(0).getMethod());
		assertEquals("Patient/$daily-report/{date}", app.getOperation().get(0).getPath());
		assertEquals(false, app.getOperation().get(0).isPublic());
		assertEquals(true, app.getOperation().get(1).isPublic());

		HttpResponse<String> search = AppJson.send(port, "GET", "App", null, null, null);
		assertEquals(200, search.statusCode(), search.body());
		assertTrue(search.body().contains("\"total\":1") || search.body().contains("\"total\": 1"), search.body());
	}
}
```

(`ourCtx` must know the custom type to parse `App`; `App` carries `@ResourceDef`, so `parseResource(App.class, ...)` registers it on first use.)

- [ ] **Step 4: Run it to verify it fails**

Run: `mvn -B test -Dtest=AppResourceTest`
Expected: compilation FAIL — `cannot find symbol: class App`.

- [ ] **Step 5: Write the resource model**

`src/main/java/com/gjergjsheldija/app/App.java`:

```java
package com.gjergjsheldija.app;

import ca.uhn.fhir.model.api.annotation.Block;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.util.ElementUtil;
import org.hl7.fhir.r4.model.BackboneElement;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UriType;

import java.util.ArrayList;
import java.util.List;

/**
 * An external service that serves custom endpoints. Operations are routed by
 * {@link AppProxyFilter}; see doc/apps.md.
 */
@ResourceDef(name = "App", profile = "http://fhir.demo/StructureDefinition/App", id = "App")
public class App extends DomainResource {

	public static final String STATUS_ACTIVE = "active";
	public static final String STATUS_INACTIVE = "inactive";
	public static final String ENDPOINT_TYPE_HTTP_RPC = "http-rpc";

	@Child(name = "status", type = {CodeType.class}, order = 0, min = 1, max = 1, summary = true)
	@Description(shortDefinition = "active | inactive")
	protected CodeType status;

	@Child(name = "endpoint", order = 1, min = 1, max = 1, summary = true)
	@Description(shortDefinition = "How the server reaches the app")
	protected AppEndpointComponent endpoint;

	@Child(name = "operation", order = 2, min = 1, max = Child.MAX_UNLIMITED, summary = true)
	@Description(shortDefinition = "Endpoints the app serves")
	protected List<AppOperationComponent> operation;

	public String getStatus() {
		return status == null ? null : status.getValue();
	}

	public App setStatus(String value) {
		this.status = value == null ? null : new CodeType(value);
		return this;
	}

	public AppEndpointComponent getEndpoint() {
		return endpoint;
	}

	public App setEndpoint(AppEndpointComponent value) {
		this.endpoint = value;
		return this;
	}

	public List<AppOperationComponent> getOperation() {
		if (operation == null) {
			operation = new ArrayList<>();
		}
		return operation;
	}

	public App addOperation(AppOperationComponent value) {
		getOperation().add(value);
		return this;
	}

	@Override
	public App copy() {
		App dst = new App();
		copyValues(dst);
		dst.status = status == null ? null : status.copy();
		dst.endpoint = endpoint == null ? null : endpoint.copy();
		if (operation != null) {
			dst.operation = new ArrayList<>();
			for (AppOperationComponent next : operation) {
				dst.operation.add(next.copy());
			}
		}
		return dst;
	}

	@Override
	public ResourceType getResourceType() {
		// Custom types have no ResourceType constant; Configuration uses Basic the same way.
		return ResourceType.Basic;
	}

	@Override
	public String fhirType() {
		return "App";
	}

	@Override
	public boolean isEmpty() {
		return super.isEmpty() && ElementUtil.isEmpty(status, endpoint, operation);
	}

	@Block
	public static class AppEndpointComponent extends BackboneElement {

		@Child(name = "type", type = {CodeType.class}, order = 0, min = 1, max = 1)
		@Description(shortDefinition = "http-rpc")
		protected CodeType type;

		@Child(name = "url", type = {UriType.class}, order = 1, min = 1, max = 1)
		@Description(shortDefinition = "Base URL the server POSTs operation envelopes to")
		protected UriType url;

		@Child(name = "secret", type = {StringType.class}, order = 2, min = 0, max = 1)
		@Description(shortDefinition = "Password for Basic auth (user = App id) on calls to the app")
		protected StringType secret;

		public String getType() {
			return type == null ? null : type.getValue();
		}

		public AppEndpointComponent setType(String value) {
			this.type = value == null ? null : new CodeType(value);
			return this;
		}

		public String getUrl() {
			return url == null ? null : url.getValue();
		}

		public AppEndpointComponent setUrl(String value) {
			this.url = value == null ? null : new UriType(value);
			return this;
		}

		public String getSecret() {
			return secret == null ? null : secret.getValue();
		}

		public AppEndpointComponent setSecret(String value) {
			this.secret = value == null ? null : new StringType(value);
			return this;
		}

		@Override
		public AppEndpointComponent copy() {
			AppEndpointComponent dst = new AppEndpointComponent();
			copyValues(dst);
			dst.type = type == null ? null : type.copy();
			dst.url = url == null ? null : url.copy();
			dst.secret = secret == null ? null : secret.copy();
			return dst;
		}

		@Override
		public boolean isEmpty() {
			return super.isEmpty() && ElementUtil.isEmpty(type, url, secret);
		}
	}

	/**
	 * The operation id is the FHIR element id ({@code "id"} in JSON), inherited from Element.
	 */
	@Block
	public static class AppOperationComponent extends BackboneElement {

		@Child(name = "method", type = {CodeType.class}, order = 0, min = 1, max = 1)
		@Description(shortDefinition = "GET | POST | PUT | DELETE | PATCH")
		protected CodeType method;

		@Child(name = "path", type = {StringType.class}, order = 1, min = 1, max = 1)
		@Description(shortDefinition = "Path under the FHIR base, e.g. Patient/$daily-report/{date}")
		protected StringType path;

		@Child(name = "public", type = {BooleanType.class}, order = 2, min = 0, max = 1)
		@Description(shortDefinition = "If true, no access token is required")
		protected BooleanType isPublic;

		public String getMethod() {
			return method == null ? null : method.getValue();
		}

		public AppOperationComponent setMethod(String value) {
			this.method = value == null ? null : new CodeType(value);
			return this;
		}

		public String getPath() {
			return path == null ? null : path.getValue();
		}

		public AppOperationComponent setPath(String value) {
			this.path = value == null ? null : new StringType(value);
			return this;
		}

		public boolean isPublic() {
			return isPublic != null && Boolean.TRUE.equals(isPublic.getValue());
		}

		public AppOperationComponent setPublic(boolean value) {
			this.isPublic = new BooleanType(value);
			return this;
		}

		@Override
		public AppOperationComponent copy() {
			AppOperationComponent dst = new AppOperationComponent();
			copyValues(dst);
			dst.method = method == null ? null : method.copy();
			dst.path = path == null ? null : path.copy();
			dst.isPublic = isPublic == null ? null : isPublic.copy();
			return dst;
		}

		@Override
		public boolean isEmpty() {
			return super.isEmpty() && ElementUtil.isEmpty(method, path, isPublic);
		}
	}
}
```

- [ ] **Step 6: Write the resource provider**

`src/main/java/com/gjergjsheldija/app/AppResourceProvider.java`:

```java
package com.gjergjsheldija.app;

import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Count;
import ca.uhn.fhir.rest.annotation.Offset;
import ca.uhn.fhir.rest.annotation.RawParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * Read/create/update/delete/patch come from {@link BaseJpaResourceProvider}; search is declared here.
 */
public class AppResourceProvider extends BaseJpaResourceProvider<App> {

	@Override
	public Class<App> getResourceType() {
		return App.class;
	}

	@Search(allowUnknownParams = true)
	public IBundleProvider search(
		HttpServletRequest theServletRequest,
		HttpServletResponse theServletResponse,
		RequestDetails theRequestDetails,
		@RawParam Map<String, List<String>> theRawParams,
		@Count Integer theCount,
		@Offset Integer theOffset) {
		startRequest(theServletRequest);
		try {
			SearchParameterMap map = new SearchParameterMap();
			map.setCount(theCount);
			map.setOffset(theOffset);
			map.setLoadSynchronous(true);
			getDao().translateRawParameters(theRawParams, map);
			return getDao().search(map, theRequestDetails, theServletResponse);
		} finally {
			endRequest(theServletRequest);
		}
	}
}
```

- [ ] **Step 7: Write the bean configuration**

`src/main/java/com/gjergjsheldija/app/AppBean.java`:

```java
package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.dao.JpaResourceDao;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppBean {

	private final FhirContext fhirContext;

	public AppBean(FhirContext fhirContext) {
		this.fhirContext = fhirContext;
		// Register before any DAO or parser touches the type.
		fhirContext.registerCustomType(App.class);
	}

	@Bean(name = "myAppDao")
	public IFhirResourceDao<App> appDao() {
		JpaResourceDao<App> dao = new JpaResourceDao<>();
		dao.setResourceType(App.class);
		dao.setContext(fhirContext);
		return dao;
	}

	@Bean(name = "myAppResourceProvider")
	public AppResourceProvider appResourceProvider(IFhirResourceDao<App> appDao, RestfulServer restfulServer) {
		AppResourceProvider provider = new AppResourceProvider();
		provider.setContext(fhirContext);
		provider.setDao(appDao);
		restfulServer.registerProvider(provider);
		return provider;
	}
}
```

- [ ] **Step 8: Run the round-trip test**

Run: `mvn -B test -Dtest=AppResourceTest`
Expected: PASS. If `PUT` returns 400 "Unknown resource type 'App'", the provider isn't registered — check the `myAppResourceProvider` bean ran (log) and that `com.gjergjsheldija.app` is in `custom-bean-packages`.

- [ ] **Step 9: Write the failing validating-interceptor test**

Read `src/test/java/com/gjergjsheldija/validation/CustomRequestValidatingInterceptorTest.java` first and follow its setup. Add a test that calls `incomingRequestPostProcessed` with `RequestDetails#getResourceName()` returning `"App"` and asserts it returns `true` without delegating to validation (mirror the existing `Configuration` test case in that file, changing only the resource name to `"App"` and the method name to `skipsValidationForApp`).

Run: `mvn -B test -Dtest=CustomRequestValidatingInterceptorTest`
Expected: the new test FAILS (App is validated).

- [ ] **Step 10: Skip `App` in the validating interceptor**

In `CustomRequestValidatingInterceptor.incomingRequestPostProcessed` replace

```java
		if (Configuration.class.getSimpleName().equals(theRequestDetails.getResourceName())) {
			logger.trace("Skipping validation for Configuration resource");
			return true;
		}
```

with

```java
		String resourceName = theRequestDetails.getResourceName();
		if (Configuration.class.getSimpleName().equals(resourceName) || App.class.getSimpleName().equals(resourceName)) {
			logger.trace("Skipping validation for custom resource {}", resourceName);
			return true;
		}
```

and add `import com.gjergjsheldija.app.App;`.

- [ ] **Step 11: Run tests**

Run: `mvn -B test -Dtest='AppResourceTest,CustomRequestValidatingInterceptorTest,ExtensionWiringTest'`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/gjergjsheldija/app src/main/java/com/gjergjsheldija/validation/CustomRequestValidatingInterceptor.java \
  src/test/java/com/gjergjsheldija/app src/test/java/com/gjergjsheldija/util/BaseFhirR4ForTesting.java \
  src/test/java/com/gjergjsheldija/validation/CustomRequestValidatingInterceptorTest.java
git commit -m "Add App custom resource with REST CRUD (#<issue>)"
```

---

### Task 4: `AppRoute` — path templates

**Files:**
- Create: `src/main/java/com/gjergjsheldija/app/AppRoute.java`
- Create: `src/test/java/com/gjergjsheldija/app/AppRouteTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `public record AppRoute(String method, List<String> segments, String appId, String operationId, boolean isPublic, String endpointUrl, String secret)`
  - `static List<String> parsePath(String path)` — throws `IllegalArgumentException` with a human-readable message.
  - `static boolean isParam(String segment)`
  - `static List<String> splitRequestPath(String pathInfo)` — `"/a/b"` → `[a, b]`; `null`/`""`/`"/"` → empty list.
  - `long literalCount()`
  - `Optional<Map<String, String>> match(List<String> requestSegments)`
  - `boolean collidesWith(AppRoute other)`
  - `toString()` omits `secret`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/gjergjsheldija/app/AppRouteTest.java`:

```java
package com.gjergjsheldija.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppRouteTest {

	private static AppRoute route(String method, String path) {
		return new AppRoute(method, AppRoute.parsePath(path), "app", "op", false, "http://x", "s3cr3t");
	}

	@Test
	void parsesLiteralAndParamSegments() {
		assertEquals(List.of("Patient", "$daily-report", "{date}"), AppRoute.parsePath("Patient/$daily-report/{date}"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "/Patient/$x", "Patient//$x", "Patient/$x/", "a/{}", "a/{b", "a/b}", "a/x{b}", "a/{b c}"})
	void rejectsMalformedPaths(String path) {
		assertThrows(IllegalArgumentException.class, () -> AppRoute.parsePath(path));
	}

	@Test
	void rejectsNullPath() {
		assertThrows(IllegalArgumentException.class, () -> AppRoute.parsePath(null));
	}

	@Test
	void rejectsDuplicateParamNames() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AppRoute.parsePath("$x/{a}/{a}"));
		assertTrue(e.getMessage().contains("duplicate"), e.getMessage());
	}

	@Test
	void matchesLiteralPath() {
		assertEquals(Optional.of(Map.of()), route("POST", "User/$register").match(List.of("User", "$register")));
	}

	@Test
	void matchExtractsRouteParams() {
		Optional<Map<String, String>> params = route("GET", "Patient/$daily-report/{date}")
			.match(List.of("Patient", "$daily-report", "2024-01-01"));
		assertEquals(Optional.of(Map.of("date", "2024-01-01")), params);
	}

	@Test
	void doesNotMatchDifferentLiteralOrLength() {
		AppRoute r = route("GET", "Patient/$daily-report/{date}");
		assertTrue(r.match(List.of("Patient", "$other", "2024-01-01")).isEmpty());
		assertTrue(r.match(List.of("Patient", "$daily-report")).isEmpty());
		assertTrue(r.match(List.of("Patient", "$daily-report", "2024-01-01", "extra")).isEmpty());
	}

	@Test
	void splitsRequestPath() {
		assertEquals(List.of("Patient", "$x", "1"), AppRoute.splitRequestPath("/Patient/$x/1"));
		assertEquals(List.of(), AppRoute.splitRequestPath(null));
		assertEquals(List.of(), AppRoute.splitRequestPath(""));
		assertEquals(List.of(), AppRoute.splitRequestPath("/"));
		// trailing slash yields an empty last segment, which never matches a parsed path
		assertEquals(List.of("a", ""), AppRoute.splitRequestPath("/a/"));
	}

	@Test
	void literalCountCountsNonParams() {
		assertEquals(2, route("GET", "Patient/$daily-report/{date}").literalCount());
	}

	@Test
	void collisionRequiresSameMethodAndShape() {
		AppRoute a = route("GET", "Patient/$r/{date}");
		assertTrue(a.collidesWith(route("GET", "Patient/$r/{day}")));
		assertFalse(a.collidesWith(route("POST", "Patient/$r/{date}")));
		assertFalse(a.collidesWith(route("GET", "Patient/$r/today")));
		assertFalse(a.collidesWith(route("GET", "Patient/$s/{date}")));
		assertFalse(a.collidesWith(route("GET", "Patient/$r")));
	}

	@Test
	void toStringDoesNotLeakSecret() {
		assertFalse(route("GET", "$x").toString().contains("s3cr3t"));
	}
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -B test -Dtest=AppRouteTest`
Expected: compilation FAIL — `cannot find symbol: class AppRoute`.

- [ ] **Step 3: Implement**

`src/main/java/com/gjergjsheldija/app/AppRoute.java`:

```java
package com.gjergjsheldija.app;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One App operation compiled for routing. Segments are literals or {@code {name}} params.
 */
public record AppRoute(String method, List<String> segments, String appId, String operationId, boolean isPublic,
							  String endpointUrl, String secret) {

	private static final Pattern PARAM = Pattern.compile("\\{[A-Za-z0-9_-]+}");

	public AppRoute {
		segments = List.copyOf(segments);
	}

	public static List<String> parsePath(String path) {
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("path is empty");
		}
		if (path.startsWith("/")) {
			throw new IllegalArgumentException("path must not start with '/': " + path);
		}
		List<String> segments = List.of(path.split("/", -1));
		Set<String> paramNames = new HashSet<>();
		for (String segment : segments) {
			if (segment.isEmpty()) {
				throw new IllegalArgumentException("path has an empty segment: " + path);
			}
			if (isParam(segment)) {
				if (!paramNames.add(segment)) {
					throw new IllegalArgumentException("duplicate route param " + segment + " in " + path);
				}
			} else if (segment.contains("{") || segment.contains("}")) {
				throw new IllegalArgumentException("malformed route param '" + segment + "' in " + path);
			}
		}
		return segments;
	}

	public static boolean isParam(String segment) {
		return PARAM.matcher(segment).matches();
	}

	public static List<String> splitRequestPath(String pathInfo) {
		if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
			return List.of();
		}
		String path = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
		return List.of(path.split("/", -1));
	}

	public long literalCount() {
		return segments.stream().filter(s -> !isParam(s)).count();
	}

	public Optional<Map<String, String>> match(List<String> requestSegments) {
		if (requestSegments.size() != segments.size()) {
			return Optional.empty();
		}
		Map<String, String> params = new LinkedHashMap<>();
		for (int i = 0; i < segments.size(); i++) {
			String segment = segments.get(i);
			String actual = requestSegments.get(i);
			if (isParam(segment)) {
				params.put(segment.substring(1, segment.length() - 1), actual);
			} else if (!segment.equals(actual)) {
				return Optional.empty();
			}
		}
		return Optional.of(params);
	}

	/**
	 * True when both routes would match exactly the same requests: same method, same length,
	 * equal literals and params in the same positions.
	 */
	public boolean collidesWith(AppRoute other) {
		if (!method.equals(other.method) || segments.size() != other.segments.size()) {
			return false;
		}
		for (int i = 0; i < segments.size(); i++) {
			String a = segments.get(i);
			String b = other.segments.get(i);
			if (isParam(a) != isParam(b) || (!isParam(a) && !a.equals(b))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return "AppRoute[" + method + " " + String.join("/", segments) + " -> " + appId + "#" + operationId
			+ (isPublic ? " public" : "") + "]";
	}
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -B test -Dtest=AppRouteTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gjergjsheldija/app/AppRoute.java src/test/java/com/gjergjsheldija/app/AppRouteTest.java
git commit -m "Add AppRoute path template parsing and matching (#<issue>)"
```

---

### Task 5: `AppRouteRegistry` — route table and refresh

**Files:**
- Create: `src/main/java/com/gjergjsheldija/app/AppRouteRegistry.java`
- Modify: `src/main/java/com/gjergjsheldija/app/AppBean.java` (add bean)
- Modify: `src/main/resources/application.yaml` (add `apps.refresh-ms` to the "Z. Extensions" block)
- Create: `src/test/java/com/gjergjsheldija/app/AppRouteRegistryTest.java`

**Interfaces:**
- Consumes: `App` (Task 3), `AppRoute` (Task 4), bean `IFhirResourceDao<App>`.
- Produces:
  - `public record Match(AppRoute route, Map<String, String> routeParams)` nested in `AppRouteRegistry`.
  - `Optional<AppRouteRegistry.Match> find(String method, List<String> requestSegments)`
  - `List<AppRoute> routes()` — all current routes.
  - `boolean refresh()` — returns true if the snapshot was rebuilt; propagates exceptions.
  - Hook `@Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)` on REST writes to `App`; registered on the `RestfulServer` interceptor service.
  - On `ApplicationReadyEvent`: initial refresh + periodic refresh every `hapi.fhir.apps.refresh-ms` via the Spring `TaskScheduler` bean.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/gjergjsheldija/app/AppRouteRegistryTest.java`:

```java
package com.gjergjsheldija.app;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

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
}
```

The "one invalid stored App" Review Focus case can't be produced through the DAO once Task 6's validation exists, so it is covered by a unit test of the build step: add to the same file

```java
	@Test
	void invalidStoredAppIsSkippedOthersStillRoute() {
		App bad = app("bad", "active", "http://127.0.0.1:1", "a//b");
		App good = app("good", "active", "http://127.0.0.1:2", "$ok");
		Map<String, List<AppRoute>> routes = AppRouteRegistry.buildRoutes(List.of(bad, good));
		assertEquals(1, routes.get("GET").size());
		assertEquals("good", routes.get("GET").get(0).appId());
	}
```

(`app(...)` sets ids via `setId(new IdType("App", id))`, so `getIdElement().getIdPart()` works without persisting.)

- [ ] **Step 2: Run to verify failure**

Run: `mvn -B test -Dtest=AppRouteRegistryTest`
Expected: compilation FAIL — `cannot find symbol: class AppRouteRegistry`.

- [ ] **Step 3: Implement the registry**

`src/main/java/com/gjergjsheldija/app/AppRouteRegistry.java`:

```java
package com.gjergjsheldija.app;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of routes from active Apps. Rebuilt on local REST writes to App and
 * periodically, so changes made on other nodes are picked up within refresh-ms.
 */
@Interceptor
public class AppRouteRegistry {

	private static final Logger ourLog = LoggerFactory.getLogger(AppRouteRegistry.class);
	private static final Set<RestOperationTypeEnum> WRITES = EnumSet.of(
		RestOperationTypeEnum.CREATE, RestOperationTypeEnum.UPDATE, RestOperationTypeEnum.DELETE, RestOperationTypeEnum.PATCH);

	public record Match(AppRoute route, Map<String, String> routeParams) {
	}

	private record Snapshot(Map<String, List<AppRoute>> routesByMethod, Set<String> versionKeys) {
	}

	private final IFhirResourceDao<App> appDao;
	private final TaskScheduler taskScheduler;
	private final Duration refreshInterval;
	private volatile Snapshot snapshot = new Snapshot(Map.of(), Set.of());

	public AppRouteRegistry(IFhirResourceDao<App> appDao, TaskScheduler taskScheduler, Duration refreshInterval) {
		this.appDao = appDao;
		this.taskScheduler = taskScheduler;
		this.refreshInterval = refreshInterval;
	}

	public Optional<Match> find(String method, List<String> requestSegments) {
		for (AppRoute route : snapshot.routesByMethod().getOrDefault(method, List.of())) {
			Optional<Map<String, String>> params = route.match(requestSegments);
			if (params.isPresent()) {
				return Optional.of(new Match(route, params.get()));
			}
		}
		return Optional.empty();
	}

	public List<AppRoute> routes() {
		return snapshot.routesByMethod().values().stream().flatMap(List::stream).toList();
	}

	public synchronized boolean refresh() {
		List<App> apps = appDao.search(SearchParameterMap.newSynchronous(), SystemRequestDetails.forAllPartitions())
			.getAllResources().stream()
			.map(App.class::cast)
			.filter(app -> App.STATUS_ACTIVE.equals(app.getStatus()))
			.toList();
		// lastUpdated is part of the key: after $expunge a re-created App restarts at version 1.
		Set<String> versionKeys = apps.stream()
			.map(app -> app.getIdElement().getIdPart() + "/" + app.getIdElement().getVersionIdPart() + "/"
				+ (app.getMeta().getLastUpdated() == null ? "" : app.getMeta().getLastUpdated().getTime()))
			.collect(Collectors.toUnmodifiableSet());
		if (versionKeys.equals(snapshot.versionKeys())) {
			return false;
		}
		Map<String, List<AppRoute>> routes = buildRoutes(apps);
		snapshot = new Snapshot(routes, versionKeys);
		ourLog.info("App routes rebuilt: {} active apps, {} routes", apps.size(), routes.values().stream().mapToInt(List::size).sum());
		return true;
	}

	static Map<String, List<AppRoute>> buildRoutes(List<App> apps) {
		Map<String, List<AppRoute>> byMethod = new HashMap<>();
		for (App app : apps) {
			String appId = app.getIdElement().getIdPart();
			try {
				List<AppRoute> appRoutes = new ArrayList<>();
				for (App.AppOperationComponent op : app.getOperation()) {
					appRoutes.add(new AppRoute(op.getMethod(), AppRoute.parsePath(op.getPath()), appId, op.getId(),
						op.isPublic(), app.getEndpoint().getUrl(), app.getEndpoint().getSecret()));
				}
				appRoutes.forEach(route -> byMethod.computeIfAbsent(route.method(), k -> new ArrayList<>()).add(route));
			} catch (RuntimeException e) {
				ourLog.warn("Skipping App {}: invalid definition ({})", appId, e.getMessage());
			}
		}
		Map<String, List<AppRoute>> sorted = new HashMap<>();
		byMethod.forEach((method, list) -> sorted.put(method, list.stream()
			.sorted(Comparator.comparingLong(AppRoute::literalCount).reversed())
			.toList()));
		return Map.copyOf(sorted);
	}

	@Hook(Pointcut.SERVER_PROCESSING_COMPLETED_NORMALLY)
	public void onRequestCompleted(RequestDetails theRequestDetails) {
		if ("App".equals(theRequestDetails.getResourceName()) && WRITES.contains(theRequestDetails.getRestOperationType())) {
			refreshQuietly();
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		refreshQuietly();
		taskScheduler.scheduleWithFixedDelay(this::refreshQuietly, Instant.now().plus(refreshInterval), refreshInterval);
	}

	private void refreshQuietly() {
		try {
			refresh();
		} catch (RuntimeException e) {
			ourLog.error("App route refresh failed; keeping previous routes", e);
		}
	}
}
```

`buildRoutes` catches `RuntimeException` per App on purpose: one bad App must not take down every route (Review Focus). This is handled — logged and skipped — not swallowed.

- [ ] **Step 4: Register the bean**

In `AppBean` add imports `ca.uhn.fhir.jpa.api.dao.IFhirResourceDao` (already present), `org.springframework.beans.factory.annotation.Value`, `org.springframework.scheduling.TaskScheduler`, `java.time.Duration`, and the method:

```java
	@Bean
	public AppRouteRegistry appRouteRegistry(
		IFhirResourceDao<App> appDao,
		RestfulServer restfulServer,
		TaskScheduler taskScheduler,
		@Value("${hapi.fhir.apps.refresh-ms:30000}") long refreshMs) {
		AppRouteRegistry registry = new AppRouteRegistry(appDao, taskScheduler, Duration.ofMillis(refreshMs));
		// SERVER_* pointcuts fire on the RestfulServer's own interceptor service.
		restfulServer.registerInterceptor(registry);
		return registry;
	}
```

(A `TaskScheduler` bean already exists — `ScheduledSvcImpl` depends on it.)

- [ ] **Step 5: Add the config key**

In `src/main/resources/application.yaml`, at the end of the "Z. Extensions" block (after `security:` and its three children, same 4-space indent as `security:`):

```yaml
    apps:
      refresh-ms: 30000              # how often each node re-reads App resources (picks up changes from other nodes)
```

- [ ] **Step 6: Run tests**

Run: `mvn -B test -Dtest='AppRouteRegistryTest,ExtensionWiringTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gjergjsheldija/app src/main/resources/application.yaml src/test/java/com/gjergjsheldija/app/AppRouteRegistryTest.java
git commit -m "Add AppRouteRegistry with local and periodic refresh (#<issue>)"
```

---

### Task 6: `AppValidationInterceptor` — reject invalid Apps

**Files:**
- Create: `src/main/java/com/gjergjsheldija/app/AppValidationInterceptor.java`
- Modify: `src/main/java/com/gjergjsheldija/app/AppBean.java` (add bean)
- Create: `src/test/java/com/gjergjsheldija/app/AppValidationInterceptorTest.java`

**Interfaces:**
- Consumes: `App`, `AppRoute.parsePath/collidesWith`, `AppRouteRegistry.routes()`.
- Produces: `AppValidationInterceptor(FhirContext, AppRouteRegistry)`, `void validate(App app)` throwing `UnprocessableEntityException`; hooks on `STORAGE_PRESTORAGE_RESOURCE_CREATED/UPDATED`, registered on the JPA `IInterceptorService` so DAO writes (not just REST) are validated.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/gjergjsheldija/app/AppValidationInterceptorTest.java`:

```java
package com.gjergjsheldija.app;

import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.net.http.HttpResponse;
import java.util.stream.Stream;

import static com.gjergjsheldija.app.AppJson.app;
import static com.gjergjsheldija.app.AppJson.op;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppValidationInterceptorTest extends BaseFhirR4ForTesting {

	private static final String URL = "http://127.0.0.1:1";

	@Autowired
	AppRouteRegistry registry;

	@BeforeEach
	void setUpClient() {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
	}

	@AfterEach
	void tearDown() {
		cleanUp();
		registry.refresh();
	}

	static Stream<Arguments> invalidApps() {
		return Stream.of(
			Arguments.of("bad status", app("v", "enabled", URL, null, op("o", "GET", "$x", false)), "status"),
			Arguments.of("bad endpoint type", app("v", "active", URL, null, op("o", "GET", "$x", false))
				.replace("\"http-rpc\"", "\"grpc\""), "endpoint.type"),
			Arguments.of("non-http url", app("v", "active", "ftp://host", null, op("o", "GET", "$x", false)), "endpoint.url"),
			Arguments.of("relative url", app("v", "active", "/relative", null, op("o", "GET", "$x", false)), "endpoint.url"),
			Arguments.of("bad method", app("v", "active", URL, null, op("o", "HEAD", "$x", false)), "method"),
			Arguments.of("duplicate op id", app("v", "active", URL, null,
				op("o", "GET", "$x", false) + "," + op("o", "GET", "$y", false)), "duplicate operation id"),
			Arguments.of("malformed path", app("v", "active", URL, null, op("o", "GET", "a//b", false)), "empty segment"),
			Arguments.of("duplicate param", app("v", "active", URL, null, op("o", "GET", "$x/{a}/{a}", false)), "duplicate route param"),
			Arguments.of("shadows read", app("v", "active", URL, null, op("o", "GET", "Patient/{id}", false)), "shadow"),
			Arguments.of("shadows type", app("v", "active", URL, null, op("o", "GET", "Patient", false)), "shadow"),
			Arguments.of("shadows custom type", app("v", "active", URL, null, op("o", "GET", "App/{id}", false)), "shadow"),
			Arguments.of("param first", app("v", "active", URL, null, op("o", "GET", "{anything}", false)), "shadow"),
			Arguments.of("metadata", app("v", "active", URL, null, op("o", "GET", "metadata", false)), "shadow"),
			Arguments.of("self collision", app("v", "active", URL, null,
				op("o1", "GET", "$x/{a}", false) + "," + op("o2", "GET", "$x/{b}", false)), "collides"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidApps")
	void rejectsInvalidApp(String name, String json, String expectedMessagePart) throws Exception {
		HttpResponse<String> response = AppJson.put(port, "v", json);
		assertEquals(422, response.statusCode(), response.body());
		assertTrue(response.body().contains(expectedMessagePart), response.body());
	}

	@Test
	void acceptsValidApp() throws Exception {
		HttpResponse<String> response = AppJson.put(port, "ok", app("ok", "active", URL, "s",
			op("o1", "GET", "Patient/$daily-report/{date}", false) + "," + op("o2", "POST", "$register", true)));
		assertEquals(201, response.statusCode(), response.body());
	}

	@Test
	void rejectsCollisionWithAnotherActiveApp() throws Exception {
		assertEquals(201, AppJson.put(port, "first", app("first", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		HttpResponse<String> second = AppJson.put(port, "second", app("second", "active", URL, null, op("o", "GET", "$x/{b}", false)));
		assertEquals(422, second.statusCode(), second.body());
		assertTrue(second.body().contains("first"), second.body());
	}

	@Test
	void updatingTheSameAppIsNotACollision() throws Exception {
		assertEquals(201, AppJson.put(port, "same", app("same", "active", URL, null, op("o", "GET", "$x/{a}", false))).statusCode());
		assertEquals(200, AppJson.put(port, "same", app("same", "active", "http://127.0.0.1:2", null, op("o", "GET", "$x/{a}", false))).statusCode());
	}

	@Test
	void inactiveAppDoesNotCollide() throws Exception {
		assertEquals(201, AppJson.put(port, "first", app("first", "active", URL, null, op("o", "GET", "$x", false))).statusCode());
		assertEquals(201, AppJson.put(port, "parked", app("parked", "inactive", URL, null, op("o", "GET", "$x", false))).statusCode());
	}
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -B test -Dtest=AppValidationInterceptorTest`
Expected: FAIL — every `rejectsInvalidApp` case returns 201 instead of 422 (no validation yet); `rejectsCollisionWithAnotherActiveApp` also fails.

- [ ] **Step 3: Implement**

`src/main/java/com/gjergjsheldija/app/AppValidationInterceptor.java`:

```java
package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rejects App writes (REST or DAO) that could not be routed safely. See spec section 1, "Write validation".
 */
@Interceptor
public class AppValidationInterceptor {

	private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
	private static final Set<String> STATUSES = Set.of(App.STATUS_ACTIVE, App.STATUS_INACTIVE);
	private static final Set<String> CUSTOM_TYPES = Set.of("App", "Configuration");
	private static final Set<String> RESERVED_FIRST_SEGMENTS = Set.of("metadata", "_history");

	private final FhirContext fhirContext;
	private final AppRouteRegistry registry;

	public AppValidationInterceptor(FhirContext fhirContext, AppRouteRegistry registry) {
		this.fhirContext = fhirContext;
		this.registry = registry;
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_CREATED)
	public void onCreate(IBaseResource theResource) {
		if (theResource instanceof App app) {
			validate(app);
		}
	}

	@Hook(Pointcut.STORAGE_PRESTORAGE_RESOURCE_UPDATED)
	public void onUpdate(IBaseResource theOldResource, IBaseResource theNewResource) {
		if (theNewResource instanceof App app) {
			validate(app);
		}
	}

	void validate(App app) {
		List<String> errors = new ArrayList<>();
		String appId = app.getIdElement().getIdPart();

		if (!STATUSES.contains(app.getStatus())) {
			errors.add("status must be one of " + STATUSES);
		}
		App.AppEndpointComponent endpoint = app.getEndpoint();
		if (endpoint == null) {
			errors.add("endpoint is required");
		} else {
			if (!App.ENDPOINT_TYPE_HTTP_RPC.equals(endpoint.getType())) {
				errors.add("endpoint.type must be http-rpc");
			}
			if (!isHttpUrl(endpoint.getUrl())) {
				errors.add("endpoint.url must be an absolute http(s) URL");
			}
		}
		if (app.getOperation().isEmpty()) {
			errors.add("at least one operation is required");
		}

		Set<String> operationIds = new HashSet<>();
		List<AppRoute> ownRoutes = new ArrayList<>();
		for (App.AppOperationComponent op : app.getOperation()) {
			String opId = op.getId();
			if (opId == null || opId.isBlank()) {
				errors.add("operation id is required");
			} else if (!operationIds.add(opId)) {
				errors.add("duplicate operation id " + opId);
			}
			if (!METHODS.contains(op.getMethod())) {
				errors.add("operation " + opId + ": method must be one of " + METHODS);
				continue;
			}
			List<String> segments;
			try {
				segments = AppRoute.parsePath(op.getPath());
			} catch (IllegalArgumentException e) {
				errors.add("operation " + opId + ": " + e.getMessage());
				continue;
			}
			String shadow = shadowProblem(segments);
			if (shadow != null) {
				errors.add("operation " + opId + ": " + shadow);
				continue;
			}
			AppRoute route = new AppRoute(op.getMethod(), segments, appId, opId, op.isPublic(), null, null);
			for (AppRoute existing : ownRoutes) {
				if (existing.collidesWith(route)) {
					errors.add("operation " + opId + " collides with operation " + existing.operationId());
				}
			}
			ownRoutes.add(route);
		}

		if (App.STATUS_ACTIVE.equals(app.getStatus())) {
			for (AppRoute mine : ownRoutes) {
				for (AppRoute other : registry.routes()) {
					if (!other.appId().equals(appId) && other.collidesWith(mine)) {
						errors.add("operation " + mine.operationId() + " collides with App " + other.appId()
							+ " operation " + other.operationId());
					}
				}
			}
		}

		if (!errors.isEmpty()) {
			throw new UnprocessableEntityException("Invalid App " + appId + ": " + String.join("; ", errors));
		}
	}

	private String shadowProblem(List<String> segments) {
		String first = segments.get(0);
		if (AppRoute.isParam(first) || RESERVED_FIRST_SEGMENTS.contains(first)) {
			return "path would shadow core FHIR endpoints";
		}
		boolean isResourceType = fhirContext.getResourceTypes().contains(first) || CUSTOM_TYPES.contains(first);
		if (isResourceType && (segments.size() < 2 || !segments.get(1).startsWith("$"))) {
			return "path would shadow core FHIR endpoints; after a resource type the next segment must start with $";
		}
		return null;
	}

	private static boolean isHttpUrl(String url) {
		if (url == null) {
			return false;
		}
		try {
			URI uri = new URI(url);
			return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
```

- [ ] **Step 4: Register the bean**

In `AppBean` add import `ca.uhn.fhir.interceptor.api.IInterceptorService` and:

```java
	@Bean
	public AppValidationInterceptor appValidationInterceptor(AppRouteRegistry registry, IInterceptorService interceptorService) {
		AppValidationInterceptor interceptor = new AppValidationInterceptor(fhirContext, registry);
		// The JPA interceptor service sees every DAO write, including non-REST ones.
		interceptorService.registerInterceptor(interceptor);
		return interceptor;
	}
```

- [ ] **Step 5: Run tests**

Run: `mvn -B test -Dtest='AppValidationInterceptorTest,AppRouteRegistryTest,AppResourceTest'`
Expected: PASS. (`AppRouteRegistryTest` still writes only valid Apps through the DAO; `invalidStoredAppIsSkippedOthersStillRoute` bypasses the DAO.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gjergjsheldija/app src/test/java/com/gjergjsheldija/app/AppValidationInterceptorTest.java
git commit -m "Validate App writes: endpoint, methods, paths, shadowing, collisions (#<issue>)"
```

---

### Task 7: Proxy filter, envelope, and docs

**Files:**
- Create: `src/main/java/com/gjergjsheldija/app/AppEnvelope.java`
- Create: `src/main/java/com/gjergjsheldija/app/AppProxyFilter.java`
- Modify: `src/main/java/com/gjergjsheldija/app/AppBean.java` (filter registration)
- Modify: `src/main/resources/application.yaml` (timeout keys)
- Create: `src/test/java/com/gjergjsheldija/app/AppEnvelopeTest.java`
- Create: `src/test/java/com/gjergjsheldija/app/StubApp.java`
- Create: `src/test/java/com/gjergjsheldija/app/AppProxyFilterTest.java`
- Create: `src/test/java/com/gjergjsheldija/app/AppProxySecurityTest.java`
- Create: `doc/apps.md`

**Interfaces:**
- Consumes: `AppRouteRegistry.find`, `AppRoute`, `AccessTokenValidation.validateToken(String)` (throws on invalid; expects the full `Bearer ...` header value).
- Produces:
  - `AppEnvelope.build(ObjectMapper mapper, AppRoute route, Map<String, String> routeParams, HttpServletRequest request, byte[] body, String baseUrl)` → `byte[]` JSON.
  - `AppEnvelope.parseQuery(String rawQuery)` → `Map<String, List<String>>`.
  - `AppProxyFilter` registered for `/fhir/*`.

- [ ] **Step 1: Write the failing envelope tests**

`src/test/java/com/gjergjsheldija/app/AppEnvelopeTest.java`:

```java
package com.gjergjsheldija.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppEnvelopeTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final AppRoute route = new AppRoute("GET", List.of("Patient", "$daily-report", "{date}"),
		"myorg.myapp", "daily-patient-report", false, "http://x", "s");

	private JsonNode build(MockHttpServletRequest request, String body) throws Exception {
		byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
		return mapper.readTree(AppEnvelope.build(mapper, route, Map.of("date", "2024-01-01"), request, bytes, "http://base/fhir"));
	}

	@Test
	void buildsEnvelopeForGet() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/fhir/Patient/$daily-report/2024-01-01");
		request.setQueryString("code=a&code=b&x=1%202");
		request.addHeader("Authorization", "Bearer t");
		request.addHeader("X-Multi", "one");
		request.addHeader("X-Multi", "two");

		JsonNode env = build(request, null);

		assertEquals("operation", env.get("type").asText());
		assertTrue(env.get("request").get("resource").isNull());
		assertEquals(List.of("a", "b"), mapper.convertValue(env.get("request").get("params").get("code"), List.class));
		assertEquals("1 2", env.get("request").get("params").get("x").get(0).asText());
		assertEquals("2024-01-01", env.get("request").get("route-params").get("date").asText());
		assertEquals("Bearer t", env.get("request").get("headers").get("authorization").asText());
		assertEquals("one, two", env.get("request").get("headers").get("x-multi").asText());
		assertEquals("http://base/fhir", env.get("box").get("base-url").asText());
		assertEquals("daily-patient-report", env.get("operation").get("id").asText());
		assertEquals("myorg.myapp", env.get("operation").get("app").get("id").asText());
		assertEquals("App", env.get("operation").get("app").get("resourceType").asText());
	}

	@Test
	void jsonBodyIsEmbeddedAsJson() throws Exception {
		JsonNode env = build(new MockHttpServletRequest("POST", "/fhir/x"), "{\"resourceType\":\"Parameters\"}");
		assertEquals("Parameters", env.get("request").get("resource").get("resourceType").asText());
	}

	@Test
	void nonJsonBodyIsEmbeddedAsString() throws Exception {
		JsonNode env = build(new MockHttpServletRequest("POST", "/fhir/x"), "a=1&b=2");
		assertEquals("a=1&b=2", env.get("request").get("resource").asText());
	}

	@Test
	void parseQueryHandlesEdgeCases() {
		assertEquals(Map.of(), AppEnvelope.parseQuery(null));
		assertEquals(Map.of(), AppEnvelope.parseQuery(""));
		assertEquals(Map.of("flag", List.of("")), AppEnvelope.parseQuery("flag"));
		assertEquals(Map.of("a", List.of("x=y")), AppEnvelope.parseQuery("a=x%3Dy"));
	}
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -B test -Dtest=AppEnvelopeTest`
Expected: compilation FAIL — `cannot find symbol: class AppEnvelope`.

- [ ] **Step 3: Implement the envelope**

`src/main/java/com/gjergjsheldija/app/AppEnvelope.java`:

```java
package com.gjergjsheldija.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the JSON body POSTed to an App (Aidbox-style "operation" envelope).
 */
final class AppEnvelope {

	private AppEnvelope() {
	}

	static byte[] build(ObjectMapper mapper, AppRoute route, Map<String, String> routeParams, HttpServletRequest request,
							  byte[] body, String baseUrl) throws IOException {
		Map<String, Object> req = new LinkedHashMap<>();
		req.put("resource", parseBody(mapper, body));
		// Parsed from the raw query string: getParameterMap() would consume form-encoded bodies.
		req.put("params", parseQuery(request.getQueryString()));
		req.put("route-params", routeParams);
		req.put("headers", headers(request));

		Map<String, Object> app = new LinkedHashMap<>();
		app.put("id", route.appId());
		app.put("resourceType", "App");
		Map<String, Object> operation = new LinkedHashMap<>();
		operation.put("id", route.operationId());
		operation.put("app", app);

		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("type", "operation");
		envelope.put("request", req);
		envelope.put("box", Map.of("base-url", baseUrl));
		envelope.put("operation", operation);
		return mapper.writeValueAsBytes(envelope);
	}

	static Map<String, List<String>> parseQuery(String rawQuery) {
		Map<String, List<String>> params = new LinkedHashMap<>();
		if (rawQuery == null || rawQuery.isEmpty()) {
			return params;
		}
		for (String pair : rawQuery.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int eq = pair.indexOf('=');
			String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
		}
		return params;
	}

	private static Object parseBody(ObjectMapper mapper, byte[] body) {
		if (body.length == 0) {
			return null;
		}
		try {
			return mapper.readTree(body);
		} catch (JsonProcessingException e) {
			return new String(body, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// readTree(byte[]) only throws JsonProcessingException in practice; keep the raw body if not.
			return new String(body, StandardCharsets.UTF_8);
		}
	}

	private static Map<String, String> headers(HttpServletRequest request) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (String name : Collections.list(request.getHeaderNames())) {
			headers.put(name.toLowerCase(Locale.ROOT), String.join(", ", Collections.list(request.getHeaders(name))));
		}
		return headers;
	}
}
```

- [ ] **Step 4: Run envelope tests**

Run: `mvn -B test -Dtest=AppEnvelopeTest`
Expected: PASS.

- [ ] **Step 5: Create the stub backend**

`src/test/java/com/gjergjsheldija/app/StubApp.java`:

```java
package com.gjergjsheldija.app;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal app backend for tests: records the last request and replies with a configurable response. */
final class StubApp implements AutoCloseable {

	private final HttpServer server;
	private final ExecutorService executor = Executors.newCachedThreadPool();
	final AtomicInteger calls = new AtomicInteger();
	volatile int status = 200;
	volatile String contentType = "application/json";
	volatile String body = "{\"ok\":true}";
	volatile long delayMs = 0;
	volatile String lastBody;
	volatile Headers lastHeaders;

	StubApp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			calls.incrementAndGet();
			lastHeaders = exchange.getRequestHeaders();
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			if (delayMs > 0) {
				try {
					Thread.sleep(delayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", contentType);
			exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				exchange.getResponseBody().write(bytes);
			}
			exchange.close();
		});
		server.setExecutor(executor);
		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}
}
```

- [ ] **Step 6: Write the failing proxy tests**

`src/test/java/com/gjergjsheldija/app/AppProxyFilterTest.java`:

```java
package com.gjergjsheldija.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.gjergjsheldija.app.AppJson.app;
import static com.gjergjsheldija.app.AppJson.op;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppProxyFilterTest extends BaseFhirR4ForTesting {

	private final ObjectMapper mapper = new ObjectMapper();
	private StubApp stub;

	@Autowired
	AppRouteRegistry registry;

	@BeforeEach
	void setUp() throws Exception {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
		stub = new StubApp();
	}

	@AfterEach
	void tearDown() {
		stub.close();
		cleanUp();
		registry.refresh();
	}

	private void register(String status, String url) throws Exception {
		HttpResponse<String> put = AppJson.put(port, "myorg.myapp", app("myorg.myapp", status, url, "s3cr3t",
			op("daily-patient-report", "GET", "Patient/$daily-report/{date}", false) + ","
				+ op("register-user", "POST", "User/$register", true)));
		assertTrue(put.statusCode() == 200 || put.statusCode() == 201, put.body());
	}

	@Test
	void forwardsEnvelopeAndReturnsResponseVerbatim() throws Exception {
		register("active", stub.url());
		stub.status = 418;
		stub.contentType = "text/plain";
		stub.body = "teapot";

		HttpResponse<String> response = AppJson.send(port, "GET", "Patient/$daily-report/2024-01-01?code=a&code=b",
			null, null, "Bearer user-token");

		assertEquals(418, response.statusCode());
		assertEquals("teapot", response.body());
		assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));

		String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("myorg.myapp:s3cr3t".getBytes(StandardCharsets.UTF_8));
		assertEquals(expectedAuth, stub.lastHeaders.getFirst("Authorization"));
		JsonNode env = mapper.readTree(stub.lastBody);
		assertEquals("2024-01-01", env.get("request").get("route-params").get("date").asText());
		assertEquals(2, env.get("request").get("params").get("code").size());
		assertEquals("Bearer user-token", env.get("request").get("headers").get("authorization").asText());
		assertEquals("daily-patient-report", env.get("operation").get("id").asText());
		assertEquals("myorg.myapp", env.get("operation").get("app").get("id").asText());
		assertTrue(env.get("box").get("base-url").asText().endsWith("/fhir"), env.toString());
	}

	@Test
	void formEncodedBodyIsForwardedIntact() throws Exception {
		register("active", stub.url());
		AppJson.send(port, "POST", "User/$register?x=1", "application/x-www-form-urlencoded", "name=alice&role=admin", null);
		JsonNode env = mapper.readTree(stub.lastBody);
		assertEquals("name=alice&role=admin", env.get("request").get("resource").asText());
		assertEquals("1", env.get("request").get("params").get("x").get(0).asText());
	}

	@Test
	void unreachableAppGives502() throws Exception {
		String url = stub.url();
		stub.close();
		register("active", url);
		HttpResponse<String> response = AppJson.send(port, "POST", "User/$register", null, null, null);
		assertEquals(502, response.statusCode(), response.body());
		assertTrue(response.body().contains("OperationOutcome"), response.body());
	}

	@Test
	void slowAppGives504() throws Exception {
		register("active", stub.url());
		stub.delayMs = 4000; // test base sets request-timeout-ms=2000
		HttpResponse<String> response = AppJson.send(port, "POST", "User/$register", null, null, null);
		assertEquals(504, response.statusCode(), response.body());
	}

	@Test
	void inactiveAppFallsThroughToHapi() throws Exception {
		register("inactive", stub.url());
		HttpResponse<String> response = AppJson.send(port, "GET", "Patient/$daily-report/2024-01-01", null, null, null);
		assertNotEquals(200, response.statusCode());
		assertEquals(0, stub.calls.get());
	}

	@Test
	void deletedAppFallsThroughToHapi() throws Exception {
		register("active", stub.url());
		int deleteStatus = AppJson.send(port, "DELETE", "App/myorg.myapp", null, null, null).statusCode();
		assertTrue(deleteStatus == 200 || deleteStatus == 204, "delete returned " + deleteStatus);
		AppJson.send(port, "POST", "User/$register", null, null, null);
		assertEquals(0, stub.calls.get());
	}

	@Test
	void securityDisabledLetsNonPublicOpsThrough() throws Exception {
		register("active", stub.url());
		assertEquals(200, AppJson.send(port, "GET", "Patient/$daily-report/2024-01-01", null, null, null).statusCode());
	}

	@Test
	void coreFhirIsUnaffected() throws Exception {
		register("active", stub.url());
		assertEquals(200, AppJson.send(port, "GET", "Patient", null, null, null).statusCode());
		assertEquals(0, stub.calls.get());
	}
}
```

`src/test/java/com/gjergjsheldija/app/AppProxySecurityTest.java`:

```java
package com.gjergjsheldija.app;

import com.gjergjsheldija.security.token.AccessTokenValidation;
import com.gjergjsheldija.util.BaseFhirR4ForTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;

import java.time.Instant;

import static com.gjergjsheldija.app.AppJson.app;
import static com.gjergjsheldija.app.AppJson.op;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "hapi.fhir.security.enabled=true")
@Import(AppProxySecurityTest.TokenConfig.class)
class AppProxySecurityTest extends BaseFhirR4ForTesting {

	@TestConfiguration
	static class TokenConfig {
		@Bean(name = "myAccessTokenValidation")
		AccessTokenValidation accessTokenValidation() {
			JwtDecoder decoder = token -> {
				if (!"good-token".equals(token)) {
					throw new BadJwtException("bad token");
				}
				return Jwt.withTokenValue(token).header("alg", "none").subject("tester")
					.issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(3600)).build();
			};
			return new AccessTokenValidation(decoder, jwt -> OAuth2TokenValidatorResult.success());
		}
	}

	private StubApp stub;

	@Autowired
	AppRouteRegistry registry;

	@BeforeEach
	void setUp() throws Exception {
		ourCtx = FhirContext.forR4();
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		ourClient = ourCtx.newRestfulGenericClient("http://localhost:" + port + "/fhir/");
		stub = new StubApp();
		AppJson.put(port, "sec", app("sec", "active", stub.url(), null,
			op("private", "GET", "$private", false) + "," + op("open", "GET", "$open", true)));
	}

	@AfterEach
	void tearDown() {
		stub.close();
		cleanUp();
		registry.refresh();
	}

	@Test
	void nonPublicOpWithoutTokenIs401() throws Exception {
		assertEquals(401, AppJson.send(port, "GET", "$private", null, null, null).statusCode());
		assertEquals(0, stub.calls.get());
	}

	@Test
	void nonPublicOpWithBadTokenIs401() throws Exception {
		assertEquals(401, AppJson.send(port, "GET", "$private", null, null, "Bearer nope").statusCode());
	}

	@Test
	void nonPublicOpWithGoodTokenPasses() throws Exception {
		assertEquals(200, AppJson.send(port, "GET", "$private", null, null, "Bearer good-token").statusCode());
	}

	@Test
	void publicOpNeedsNoToken() throws Exception {
		assertEquals(200, AppJson.send(port, "GET", "$open", null, null, null).statusCode());
	}
}
```

- [ ] **Step 7: Run to verify failure**

Run: `mvn -B test -Dtest='AppProxyFilterTest,AppProxySecurityTest'`
Expected: FAIL — requests reach HAPI (no filter), e.g. `forwardsEnvelopeAndReturnsResponseVerbatim` gets 400/404 instead of 418.

- [ ] **Step 8: Implement the filter**

`src/main/java/com/gjergjsheldija/app/AppProxyFilter.java`:

```java
package com.gjergjsheldija.app;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjergjsheldija.security.token.AccessTokenValidation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Routes requests that match an active App operation to that App; everything else continues to HAPI.
 */
public class AppProxyFilter extends OncePerRequestFilter {

	private static final Logger ourLog = LoggerFactory.getLogger(AppProxyFilter.class);
	private static final List<String> MDC_KEYS = List.of("uri", "method", "status", "latency", "operation_name", "id_resource_name");

	private final AppRouteRegistry registry;
	private final ObjectProvider<AccessTokenValidation> tokenValidation;
	private final boolean securityEnabled;
	private final String serverAddress;
	private final Duration requestTimeout;
	private final HttpClient httpClient;
	private final FhirContext fhirContext;
	private final ObjectMapper mapper = new ObjectMapper();

	public AppProxyFilter(AppRouteRegistry registry, ObjectProvider<AccessTokenValidation> tokenValidation,
								 boolean securityEnabled, String serverAddress, Duration connectTimeout, Duration requestTimeout,
								 FhirContext fhirContext) {
		this.registry = registry;
		this.tokenValidation = tokenValidation;
		this.securityEnabled = securityEnabled;
		this.serverAddress = serverAddress;
		this.requestTimeout = requestTimeout;
		this.fhirContext = fhirContext;
		this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		Optional<AppRouteRegistry.Match> match = registry.find(request.getMethod(), AppRoute.splitRequestPath(request.getPathInfo()));
		if (match.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}
		long start = System.nanoTime();
		AppRoute route = match.get().route();
		try {
			if (securityEnabled && !route.isPublic() && !isAuthorized(request)) {
				writeError(response, 401, "Missing or invalid access token");
				return;
			}
			forward(match.get(), request, response);
		} finally {
			log(request, response, route, start);
		}
	}

	private boolean isAuthorized(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		AccessTokenValidation validator = tokenValidation.getIfAvailable();
		if (validator == null) {
			ourLog.error("hapi.fhir.security.enabled=true but no AccessTokenValidation bean; rejecting App call");
			return false;
		}
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return false;
		}
		try {
			validator.validateToken(authorization);
			return true;
		} catch (RuntimeException e) {
			ourLog.info("Rejected App call: {}", e.getMessage());
			return false;
		}
	}

	private void forward(AppRouteRegistry.Match match, HttpServletRequest request, HttpServletResponse response) throws IOException {
		AppRoute route = match.route();
		byte[] body = request.getInputStream().readAllBytes();
		byte[] envelope = AppEnvelope.build(mapper, route, match.routeParams(), request, body, baseUrl(request));

		HttpRequest.Builder outbound = HttpRequest.newBuilder(URI.create(route.endpointUrl()))
			.timeout(requestTimeout)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofByteArray(envelope));
		if (route.secret() != null && !route.secret().isEmpty()) {
			String credentials = route.appId() + ":" + route.secret();
			outbound.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
		}

		HttpResponse<byte[]> appResponse;
		try {
			appResponse = httpClient.send(outbound.build(), HttpResponse.BodyHandlers.ofByteArray());
		} catch (HttpConnectTimeoutException e) {
			writeError(response, 502, "App " + route.appId() + " is unreachable");
			return;
		} catch (HttpTimeoutException e) {
			writeError(response, 504, "App " + route.appId() + " did not respond in time");
			return;
		} catch (IOException e) {
			writeError(response, 502, "App " + route.appId() + " is unreachable");
			return;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			writeError(response, 502, "Interrupted while calling App " + route.appId());
			return;
		}

		response.setStatus(appResponse.statusCode());
		appResponse.headers().firstValue("Content-Type").ifPresent(response::setContentType);
		response.getOutputStream().write(appResponse.body());
	}

	private String baseUrl(HttpServletRequest request) {
		if (serverAddress != null && !serverAddress.isBlank()) {
			return serverAddress;
		}
		String port = (request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort();
		return request.getScheme() + "://" + request.getServerName() + port + request.getContextPath() + request.getServletPath();
	}

	private void writeError(HttpServletResponse response, int status, String message) throws IOException {
		OperationOutcome outcome = new OperationOutcome();
		outcome.addIssue()
			.setSeverity(OperationOutcome.IssueSeverity.ERROR)
			.setCode(status == 401 ? OperationOutcome.IssueType.LOGIN : OperationOutcome.IssueType.TRANSIENT)
			.setDiagnostics(message);
		response.setStatus(status);
		response.setContentType("application/fhir+json");
		response.getOutputStream().write(fhirContext.newJsonParser().encodeResourceToString(outcome).getBytes(StandardCharsets.UTF_8));
	}

	private void log(HttpServletRequest request, HttpServletResponse response, AppRoute route, long startNanos) {
		try {
			MDC.put("uri", request.getRequestURI());
			MDC.put("method", request.getMethod());
			MDC.put("status", String.valueOf(response.getStatus()));
			MDC.put("latency", String.valueOf((System.nanoTime() - startNanos) / 1_000_000));
			MDC.put("operation_name", route.operationId());
			MDC.put("id_resource_name", route.appId());
			ourLog.info("App call {}#{} -> {}", route.appId(), route.operationId(), response.getStatus());
		} finally {
			MDC_KEYS.forEach(MDC::remove);
		}
	}
}
```

- [ ] **Step 9: Register the filter**

In `AppBean` add imports `com.gjergjsheldija.security.token.AccessTokenValidation`, `org.springframework.beans.factory.ObjectProvider`, `org.springframework.beans.factory.annotation.Qualifier`, `org.springframework.boot.web.servlet.FilterRegistrationBean`, and:

```java
	@Bean
	public FilterRegistrationBean<AppProxyFilter> appProxyFilterRegistration(
		AppRouteRegistry registry,
		// Two AccessTokenValidation beans exist in production (the @Component and SecurityBean's
		// myAccessTokenValidation); SecurityInterceptor uses the latter, so must we.
		@Qualifier("myAccessTokenValidation") ObjectProvider<AccessTokenValidation> tokenValidation,
		@Value("${hapi.fhir.security.enabled:false}") boolean securityEnabled,
		@Value("${hapi.fhir.server_address:}") String serverAddress,
		@Value("${hapi.fhir.apps.connect-timeout-ms:5000}") long connectTimeoutMs,
		@Value("${hapi.fhir.apps.request-timeout-ms:30000}") long requestTimeoutMs) {
		// Created here, not as its own bean, so Spring Boot doesn't also map it to /*.
		AppProxyFilter filter = new AppProxyFilter(registry, tokenValidation, securityEnabled, serverAddress,
			Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(requestTimeoutMs), fhirContext);
		FilterRegistrationBean<AppProxyFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setName("appProxyFilter");
		registration.addUrlPatterns("/fhir/*");
		return registration;
	}
```

- [ ] **Step 10: Add the timeout config keys**

Under the `apps:` key added in Task 5:

```yaml
    apps:
      refresh-ms: 30000              # how often each node re-reads App resources (picks up changes from other nodes)
      connect-timeout-ms: 5000       # connecting to an App's endpoint.url
      request-timeout-ms: 30000      # waiting for an App's response; exceeded -> 504
```

- [ ] **Step 11: Run all App tests**

Run: `mvn -B test -Dtest='App*Test,ExtensionWiringTest'`
Expected: PASS.

- [ ] **Step 12: Write the docs**

`doc/apps.md`:

````markdown
# Apps

Apps let an external service (any language) serve custom endpoints under `/fhir/...`.
Register an `App` resource; the server forwards matching requests to the app and returns
its response unchanged. Inspired by [Aidbox Apps](https://www.health-samurai.io/docs/aidbox/developer-experience/apps).

## Registering an App

```json
PUT /fhir/App/myorg.myapp
{
  "resourceType": "App",
  "id": "myorg.myapp",
  "status": "active",
  "endpoint": { "type": "http-rpc", "url": "https://my.service:8888", "secret": "s3cr3t" },
  "operation": [
    { "id": "daily-patient-report", "method": "GET",  "path": "Patient/$daily-report/{date}" },
    { "id": "register-user",        "method": "POST", "path": "User/$register", "public": true }
  ]
}
```

- `status`: `active` routes the operations; `inactive` keeps the App stored but unrouted.
- `path`: relative to `/fhir/`, `{name}` segments become route params. If the first segment is a
  resource type, the second must start with `$` (Apps can't shadow `GET /Patient/123`).
- `public: true` skips the access-token check for that operation.
- Two active Apps can't declare the same method + path shape (422).
- Changes apply immediately on the node that received the write and within
  `hapi.fhir.apps.refresh-ms` (default 30 s) on other nodes.

## What the app receives

Every matching request becomes a `POST` to `endpoint.url` with
`Authorization: Basic base64(<App id>:<secret>)` (omitted when no secret) and this body:

```json
{
  "type": "operation",
  "request": {
    "resource": { "...": "request body as JSON, a string if not JSON, null if empty" },
    "params": { "code": ["a", "b"] },
    "route-params": { "date": "2024-01-01" },
    "headers": { "authorization": "Bearer <user token>", "content-type": "..." }
  },
  "box": { "base-url": "http://localhost:8080/fhir" },
  "operation": { "id": "daily-patient-report", "app": { "id": "myorg.myapp", "resourceType": "App" } }
}
```

The app's status code, `Content-Type` and body are returned to the client as-is.

## Minimal app (Python)

```python
from http.server import BaseHTTPRequestHandler, HTTPServer
import json

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        envelope = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        date = envelope["request"]["route-params"].get("date")
        body = json.dumps({"resourceType": "Parameters",
                           "parameter": [{"name": "date", "valueString": date}]}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/fhir+json")
        self.end_headers()
        self.wfile.write(body)

HTTPServer(("0.0.0.0", 8888), Handler).serve_forever()
```

Check the `Authorization` header against your secret before trusting the call.

## Errors

| Situation | Status |
|---|---|
| Missing/invalid token on a non-public operation (`SECURITY_ENABLED=true`) | 401 |
| Invalid App resource | 422 |
| App unreachable | 502 |
| App slower than `hapi.fhir.apps.request-timeout-ms` | 504 |

## Configuration

```yaml
hapi:
  fhir:
    apps:
      refresh-ms: 30000
      connect-timeout-ms: 5000
      request-timeout-ms: 30000
```

## Security notes (accepted risks)

- Anyone allowed to write `App` resources can make the server POST to any host, including
  internal services and cloud metadata endpoints. Restrict egress at the network level.
- `endpoint.secret` is returned on reads to anyone allowed to read `App` resources.
````

- [ ] **Step 13: Full build**

Run: `mvn -B verify`
Expected: `BUILD SUCCESS`.

- [ ] **Step 14: Commit**

```bash
git add src/main/java/com/gjergjsheldija/app src/main/resources/application.yaml src/test/java/com/gjergjsheldija/app doc/apps.md
git commit -m "Proxy matching requests to Apps with Aidbox-style envelope (#<issue>)"
```
