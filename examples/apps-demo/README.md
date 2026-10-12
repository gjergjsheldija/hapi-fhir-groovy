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
`demo-secret`), and removes the App and stops `app.py` on exit. `FHIR_BASE` overrides the server URL.
The App listens on `localhost`, so the FHIR server must be able to reach it there (not the case inside
docker compose unless you change `endpoint.url` in `app.json`).

## Expected output (trimmed)
```
== 3. Register the App: PUT App/demo.app           HTTP 201
== 4. Public operation: GET $hello                 HTTP 200   (greeting: Hello from an external App)
== 5. GET Patient/$daily-report/2026-10-12?ward=A&ward=B
HTTP 200  {"parameter": [{"name": "date", "valueString": "2026-10-12"}, {"name": "requested-by", "valueString": "anonymous"},
                         {"name": "params", "valueString": "{\"ward\": [\"A\", \"B\"]}"}]}
== 6. Guard rail: App claims GET $export           HTTP 422  "path would shadow server operation $export"
== 7. Guard rail: App claims GET Patient/{id}      HTTP 422  "path would shadow core FHIR endpoints; ..."
== 8. Guard rail: App down -> $hello               HTTP 502  "App demo.app is unreachable"
== 9a. DELETE App/demo.app                         HTTP 200
== 9b. $hello after delete                         HTTP 400  (falls through to HAPI)
All steps matched.
```

## With security enabled
With `SECURITY_ENABLED=true`, writing Apps needs a token with the admin scope `app:admin`, and
`daily-report` (non-public) needs a valid token:
```bash
ADMIN_TOKEN=<token with app:admin> USER_TOKEN=<any valid token> ./examples/apps-demo/run-demo.sh
```
Tokens are sent as `Authorization: Bearer ...`; the demo App never prints them.

See [`doc/apps.md`](../../doc/apps.md) for the full reference.
