# Apps demo

A runnable end-to-end demo of the Apps feature: a tiny Python service registers itself as an `App`,
the FHIR server forwards matching requests to it, and the guard rails (shadowing, unreachable App) are exercised.

Files: `app.py` (the App, stdlib only), `app.json` (its manifest), `run-demo.sh` (the walkthrough).

## Prerequisites
JDK 21, Maven, Python 3, curl.

## Run
```bash
# terminal 1: start the FHIR server (port 8080)
JAVA_HOME=<jdk21> mvn spring-boot:run -Pboot      # or: docker compose up -d --build

# terminal 2
./examples/apps-demo/run-demo.sh
```
The script starts `app.py` itself (port `DEMO_APP_PORT`, default 8888; secret `DEMO_APP_SECRET`, default
`demo-secret`; both are also written into the registered manifest), and removes the App and stops `app.py` on exit. `FHIR_BASE` overrides the server URL.
The App listens on `localhost`, so the FHIR server must be able to reach it there (not the case inside
docker compose unless you change `endpoint.url` in `app.json`).

## Expected output (abridged)
Response bodies are shortened to the interesting line; step 3 prints only the status because the
response echoes `endpoint.secret`.
```
== 3. Register the App: PUT App/demo.app
HTTP 201 (expected ^(200|201)$)

== 4. Public operation: GET $hello
hello -> 200
HTTP 200 (expected ^200$)
{"resourceType": "Parameters", "parameter": [{"name": "greeting", "valueString": "Hello from an external App"}]}

== 5. Operation with route + query params: GET Patient/$daily-report/2026-10-12
daily-report -> 200
HTTP 200 (expected ^200$)
{"resourceType": "Parameters", "parameter": [{"name": "date", "valueString": "2026-10-12"}, {"name": "requested-by", "valueString": "anonymous"}, {"name": "params", "valueString": "{\"ward\": [\"A\", \"B\"]}"}]}

== 6. Guard rail: App claims GET $export (server operation)
HTTP 422 (expected ^422$)
  ... "diagnostics": "Invalid App evil.app: operation evil: path would shadow server operation $export"

== 7. Guard rail: App claims GET Patient/{id} (shadows a read)
HTTP 422 (expected ^422$)
  ... "diagnostics": "Invalid App evil.app: operation evil: path would shadow core FHIR endpoints; ..."

== 8. Guard rail: App down -> $hello
HTTP 502 (expected ^502$)
{"resourceType":"OperationOutcome", ... "diagnostics":"App demo.app is unreachable"}

== 9a. Cleanup: DELETE App/demo.app
HTTP 200 (expected ^(200|204)$)

== 9b. After delete, $hello falls through to HAPI
HTTP 400 (expected ^400$)
  ... "diagnostics": "Invalid request: The FHIR endpoint on this server does not know how to handle GET operation[$hello] ..."

All steps matched.
```

The script creates `App/demo.app` (and tries `App/evil.app`, which is rejected) and deletes only those on exit.

## With security enabled
With `SECURITY_ENABLED=true`, writing Apps needs a token with the admin scope `app:admin`, and
`daily-report` (non-public) needs a valid token:
```bash
ADMIN_TOKEN=<token with app:admin> USER_TOKEN=<any valid token> ./examples/apps-demo/run-demo.sh
```
Tokens are sent as `Authorization: Bearer ...`; the demo App never prints them.

See [`doc/apps.md`](../../doc/apps.md) for the full reference.
