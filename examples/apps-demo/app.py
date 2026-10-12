#!/usr/bin/env python3
"""Demo App for the HAPI Apps feature. Standard library only."""
import base64
import hmac
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(os.environ.get("DEMO_APP_PORT", "8888"))
EXPECTED = "Basic " + base64.b64encode(
    ("demo.app:" + os.environ.get("DEMO_APP_SECRET", "demo-secret")).encode()).decode()


def params(*pairs):
    return {"resourceType": "Parameters",
            "parameter": [{"name": n, "valueString": v} for n, v in pairs]}


class Handler(BaseHTTPRequestHandler):
    def reply(self, status, body, ctype="application/json"):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        try:
            raw = self.rfile.read(int(self.headers.get("Content-Length") or 0))
        except ValueError:
            return self.reply(400, {"error": "bad envelope"})
        if not hmac.compare_digest(self.headers.get("Authorization", "").encode(), EXPECTED.encode()):
            print("(auth) -> 401", flush=True)
            return self.reply(401, {"error": "bad app credentials"})
        try:
            env = json.loads(raw or b"{}")
        except ValueError:
            env = None
        if not isinstance(env, dict):
            print("(envelope) -> 400", flush=True)
            return self.reply(400, {"error": "bad envelope"})
        op = (env.get("operation") or {}).get("id")
        req = env.get("request") or {}
        if op == "daily-report":
            who = "authenticated user" if "authorization" in {k.lower() for k in (req.get("headers") or {})} else "anonymous"
            body = params(("date", (req.get("route-params") or {}).get("date", "")),
                          ("requested-by", who),
                          ("params", json.dumps(req.get("params") or {})))
            status = 200
        elif op == "hello":
            body, status = params(("greeting", "Hello from an external App")), 200
        else:
            body, status = {"error": "unknown operation"}, 404
        print(f"{op} -> {status}", flush=True)
        ctype = "application/fhir+json" if status == 200 else "application/json"
        self.reply(status, body, ctype)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
