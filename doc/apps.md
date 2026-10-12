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
- No App can use a standard FHIR/HAPI operation name (e.g. `$export`, `$validate`) or any operation the
  server serves, even for features that are currently disabled (422).
- Reserved root paths: `metadata`, `_history`, `swagger-ui`, `api-docs`, `_search`, `.well-known` (422).
- Colliding Apps are rejected with 422, including two Apps within one transaction Bundle. Two routes collide
  when they are ambiguous (same method and equal-specificity overlapping paths); a more specific route
  still coexists with a less specific one.
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
