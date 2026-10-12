#!/usr/bin/env python3
"""Demo App for the HAPI Apps feature. Standard library only."""
import base64
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
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length)
        if self.headers.get("Authorization") != EXPECTED:
            print("(auth) -> 401", flush=True)
            return self.reply(401, {"error": "bad app credentials"})
        env = json.loads(raw or b"{}")
        op = env.get("operation", {}).get("id")
        req = env.get("request", {})
        if op == "daily-report":
            who = "authenticated user" if "authorization" in req.get("headers", {}) else "anonymous"
            body = params(("date", req.get("route-params", {}).get("date", "")),
                          ("requested-by", who),
                          ("params", json.dumps(req.get("params", {}))))
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
