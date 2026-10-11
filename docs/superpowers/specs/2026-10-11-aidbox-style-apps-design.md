# Aidbox-style Apps — Design

Date: 2026-10-11
Status: Approved design, pending spec review
Reference: https://www.health-samurai.io/docs/aidbox/developer-experience/apps

## Goal

Let teams add custom endpoints to the FHIR server as **external services in any language**
(polyglot extensions), complementing the existing in-process Groovy scripting. An `App`
resource declares operations (method + path) and an HTTP endpoint; the server proxies
matching requests to that endpoint using an Aidbox-like JSON envelope and returns the
app's response unchanged.

The Aidbox shape is a guide, not a contract: existing Aidbox apps need not run unmodified.

## Decisions

| Topic | Decision |
|---|---|
| Path shapes | Arbitrary paths under the FHIR base (`/fhir/...`) with `{name}` route params |
| Routing | Servlet filter in front of the HAPI `RestfulServer` |
| Storage | New custom FHIR resource `App` (JPA-backed, like `Configuration`) |
| Auth | Existing `AccessTokenValidation` JWT check; per-operation `public: true` skips it; user token always forwarded |
| Topology | Multi-node: local rebuild on write + periodic refresh (default 30 s) |
| Secret | Stored and returned as-is on reads (same as Aidbox) |
| Endpoint type | `http-rpc` only |

## Out of scope

Aidbox `resources` and `subscriptions` (deprecated upstream), `policies` (replaced by
`public`), `apiVersion`/`type` fields (single-valued), retries, listing App operations in
`/metadata`, streaming bodies larger than memory, secret masking, endpoint host allowlist
(see Accepted risks).

## 1. `App` resource

```json
PUT /fhir/App/myorg.myapp
{
  "resourceType": "App",
  "id": "myorg.myapp",
  "status": "active",
  "endpoint": { "type": "http-rpc", "url": "https://my.service.com:8888", "secret": "s3cr3t" },
  "operation": [
    { "id": "daily-patient-report", "method": "GET",  "path": "Patient/$daily-report/{date}" },
    { "id": "register-user",        "method": "POST", "path": "User/$register", "public": true }
  ]
}
```

Differences from Aidbox, forced by the FHIR data model: `operations` map → `operation`
list with an `id` per entry; `path` array → string template where `{name}` segments are
route params.

### Fields

| Path | Type | Card. | Notes |
|---|---|---|---|
| `status` | code `active \| inactive` | 1..1 | Only `active` apps are routed |
| `endpoint` | BackboneElement | 1..1 | |
| `endpoint.type` | code `http-rpc` | 1..1 | |
| `endpoint.url` | uri | 1..1 | Must be `http` or `https` |
| `endpoint.secret` | string | 0..1 | Basic auth password; omitted → no `Authorization` header to the app |
| `operation` | BackboneElement | 1..* | |
| `operation.id` | string | 1..1 | Unique within the App |
| `operation.method` | code `GET \| POST \| PUT \| DELETE \| PATCH` | 1..1 | |
| `operation.path` | string | 1..1 | Relative to the FHIR base, no leading `/` |
| `operation.public` | boolean | 0..1 | Default `false` |

### Code layout (package `com.gjergjsheldija.app`)

- `App` — `DomainResource` with `@Block` classes `AppEndpointComponent` and
  `AppOperationComponent`; search param `status` (token). Modeled on `configuration/Configuration.java`.
- `AppBean` — `@Configuration` that calls `fhirContext.registerCustomType(App.class)` and
  creates `myAppDao` (`JpaResourceDao<App>`) and `myAppResourceProvider`. Modeled on
  `configuration/ConfigurationBean.java`.
- `AppResourceProvider` — `BaseJpaResourceProvider<App>`. Modeled on `ConfigurationResourceProvider`.
- No custom `SearchParameter`: the registry loads all `App` resources and filters on `status`
  in Java (App counts are small), avoiding FHIRPath on a custom type.

### Write validation

`AppValidationInterceptor` hooks `STORAGE_PRESTORAGE_RESOURCE_CREATED` and
`STORAGE_PRESTORAGE_RESOURCE_UPDATED`; violations throw `UnprocessableEntityException` (HTTP 422):

1. `endpoint.url` parses as an absolute `http`/`https` URI.
2. `operation.method` is one of the five allowed values.
3. `operation.id` values are unique within the App.
4. Path compiles: non-empty segments; `{name}` param names unique within the path.
5. **No core-FHIR shadowing:** if segment 0 is a known resource type
   (`fhirContext.getResourceTypes()`, which includes `App` and `Configuration`), segment 1
   must exist and start with `$`. `Patient/$x/{d}` is allowed; `Patient/{id}`, `Patient`,
   and `metadata`-like paths are not. A segment-0 value starting with `$` is allowed
   (system-level operation). The literal `metadata` and `_history` segments at position 0
   are rejected.
6. **No collisions:** for an `active` App, no operation may have the same method and an
   equivalent path (same literal segments, params in the same positions) as an operation of
   another active App. Checked against the current registry snapshot.

## 2. Routing, sync, proxying

### `AppRoute` (value object)

Compiled from one operation: `method`, `segments` (each literal or param name), `appId`,
`operationId`, `isPublic`, `endpointUrl`, `secret`. Method
`Optional<Map<String,String>> match(List<String> requestSegments)` returns the
route-params on match. Paths match only when the segment count is equal. Request segments
are URL-decoded before matching.

### `AppRouteRegistry`

- Holds a volatile immutable snapshot: `Map<String method, List<AppRoute>>`, each list
  sorted by literal-segment count descending (most specific first), and the set of
  `(appId, versionId)` pairs it was built from.
- `Optional<Match> find(String method, String path)` — first matching route wins.
- `refresh()` — loads all `App` resources (synchronous, `SystemRequestDetails.forAllPartitions()`),
  keeps the `active` ones, compares the `(id, versionId, lastUpdated)` set to the snapshot's,
  and rebuilds only if it differs. `lastUpdated` is in the key because after `$expunge` a
  re-created App restarts at version 1. An App that fails to compile is skipped with a warning
  so the others keep routing.
- Triggers:
  - `ApplicationReadyEvent` → `refresh()`, then a fixed-delay `refresh()` every
    `hapi.fhir.apps.refresh-ms` on the existing Spring `TaskScheduler`; this is how other
    replicas see changes. Failures are logged and the old snapshot is kept.
  - `SERVER_PROCESSING_COMPLETED_NORMALLY` for REST create/update/delete/patch of `App` →
    `refresh()` (runs after commit). Writes inside a transaction Bundle are picked up by the
    periodic refresh.
- Registration: the registry is registered on the `RestfulServer` interceptor service (server
  pointcuts); `AppValidationInterceptor` on the JPA `IInterceptorService` (sees DAO writes too).
  Both are registered by `AppBean`, not via `custom-interceptor-classes`, so the proxy can't be
  enabled without its validation.

### `AppProxyFilter`

`OncePerRequestFilter`, registered via `FilterRegistrationBean` on `/fhir/*` with an
order that runs before the HAPI servlet (servlet filters always precede the servlet).

1. Path = `request.getRequestURI()` minus context path minus `/fhir/`. Split on `/`.
2. `registry.find(method, path)`; no match → `chain.doFilter(...)` (HAPI handles it as today).
3. If `hapi.fhir.security.enabled` and the route isn't public: require an `Authorization`
   header and call `AccessTokenValidation.validateToken`. Failure → 401 with a FHIR
   `OperationOutcome` body (`application/fhir+json`).
4. Build the envelope:

   ```json
   {
     "type": "operation",
     "request": {
       "resource": <body parsed as JSON; raw string if not JSON; null if empty>,
       "params": { "name": ["v1", "v2"] },
       "route-params": { "date": "2024-01-01" },
       "headers": { "content-type": "...", "authorization": "Bearer ..." }
     },
     "box": { "base-url": "<hapi.fhir.server_address, else derived from the request>" },
     "operation": { "id": "daily-patient-report", "app": { "id": "myorg.myapp", "resourceType": "App" } }
   }
   ```

   Header names are lower-cased; multi-valued headers are joined with `, `. Query params
   are always arrays (diverges from Aidbox's single-value form; avoids losing repeats).
   Serialization uses Jackson (already on the classpath).
5. POST it to `endpoint.url` with `Content-Type: application/json` and, when a secret is
   set, `Authorization: Basic base64(appId:secret)`. Uses one shared
   `java.net.http.HttpClient` with connect timeout `hapi.fhir.apps.connect-timeout-ms`
   (default 5000) and per-request timeout `hapi.fhir.apps.request-timeout-ms` (default 30000).
6. Copy the response status, `Content-Type`, and body to the client verbatim.

Errors (all with an `OperationOutcome` body):

| Condition | Status |
|---|---|
| Connection refused / DNS / IO error | 502 |
| Request timeout | 504 |
| Missing/invalid token on non-public op | 401 |

Logging: one log line per proxied call via the existing logging setup, with MDC keys
`uri`, `method`, `status`, `latency`, `operation_name` (= operation id),
`id_resource_name` (= app id), matching `CustomLoggingInterceptor`.

### Configuration

```yaml
hapi:
  fhir:
    apps:
      refresh-ms: 30000
      connect-timeout-ms: 5000
      request-timeout-ms: 30000
```

### Bundled fix

Restore in `src/main/resources/application.yaml` the wiring removed between `746a258` and
`700f787`, renamed from `com.clinomic` to `com.gjergjsheldija`. Two original entries are
dropped because their classes no longer exist:
`customoperation.observation.CustomResponseInterceptor` and
`customoperation.observation.GroupByInterval`.

```yaml
custom-bean-packages: ca.uhn.fhir.jpa.starter, com.gjergjsheldija
custom-interceptor-classes:
  - com.gjergjsheldija.logging.CustomLoggingInterceptor
  - ca.uhn.fhir.jpa.subscription.util.SubscriptionDebugLogInterceptor
  - com.gjergjsheldija.auditevent.AuditEventInterceptor
  - com.gjergjsheldija.security.SecurityInterceptor
  - com.gjergjsheldija.terminology.TerminologyCapabilityInterceptor
  - com.gjergjsheldija.validation.CustomRequestValidatingInterceptor
custom-provider-classes:
  - com.gjergjsheldija.scripting.ScriptingProvider
```

The same commits also dropped every `${ENV}` binding from `application.yaml` (26 variables,
including `SQL_URL`/`SQL_USERNAME`/`SQL_PASSWORD`/`SQL_DIALECT`, so a compose deployment would
run on in-memory H2). Restore them with H2 defaults for local runs, plus the `security`,
`audit_event`, `log_request_body`, `cache_type`, validation, subscription and `searchcache`
keys the extensions read. `CustomRequestValidatingInterceptor` must also skip `App` (it skips
only `Configuration` today), or every App write is rejected as an unknown type.

Re-enabling these turns the existing extensions back on (JWT security, BALP audit,
logging, scripting, Infinispan cache). Their own config keys and env vars (`.env`) must be
present for the server to start; the plan includes a smoke test that the app boots with the
default `application.yaml` and the test `application.yaml`.

## Accepted risks

- **SSRF via `endpoint.url`.** Any caller allowed to write `App` resources (today: any
  valid token, since `SecurityInterceptor` grants `allowAll`) can make the server POST to an
  arbitrary host, including internal services and cloud metadata endpoints. Accepted by the
  owner; mitigation is expected from network policy outside the app (egress rules). Recorded
  in `doc/apps.md`.
- **Secret readable.** `endpoint.secret` is returned on reads to anyone allowed to read `App`.

## Delivery prerequisites

The workflow in `CLAUDE.md` lets agents merge green PRs, so CI is the only gate. Before any
feature issue:

1. A GitHub Actions workflow running `mvn -B verify` on every PR and on `main`.
2. Branch protection on `main` requiring that check to pass (set by the owner in GitHub settings).

## 3. Testing

No new test dependencies. Integration tests extend `com.gjergjsheldija.util.BaseFhirR4ForTesting`
(H2). The stub backend is `com.sun.net.httpserver.HttpServer` on a random port.

- `AppRouteTest` (unit): template compile; literal and param matching; segment-count
  mismatch; URL-decoded segments; most-literal wins; duplicate param names rejected.
- `AppValidationInterceptorTest` (unit or integration): each of the six validation rules
  produces 422; a valid App is accepted.
- `AppRouteRegistryTest` (integration): App written via DAO with the local refresh path
  bypassed → `refresh()` picks it up; unchanged `refresh()` doesn't rebuild the snapshot;
  `inactive` / deleted apps are dropped.
- `AppProxyFilterTest` (integration):
  - Envelope correctness at the stub (`route-params`, `params`, forwarded `authorization`,
    `box.base-url`, `operation.id`, `operation.app.id`) and Basic auth `base64(id:secret)`.
  - Status, `Content-Type`, and body returned verbatim (including non-2xx from the app).
  - Security enabled: non-public without token → 401; public op passes.
  - Stub stopped → 502; stub sleeping past the timeout → 504.
  - Inactive/deleted App → request falls through to HAPI's normal response.
  - Plain `GET /fhir/Patient` unaffected.

## Docs

`doc/apps.md` in the style of the other `doc/*.md`: manifest reference, envelope reference,
auth, config keys, and a minimal backend example (a few lines of Python or Node).
