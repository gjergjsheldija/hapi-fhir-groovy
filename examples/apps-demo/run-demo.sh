#!/usr/bin/env bash
# End-to-end demo of HAPI Apps. Needs curl + python3 and a running FHIR server.
# SECURITY_ENABLED=true: export ADMIN_TOKEN (scope app:admin) and USER_TOKEN.
set -euo pipefail
cd "$(dirname "$0")"

FHIR_BASE="${FHIR_BASE:-http://localhost:8080/fhir}"
ADMIN_AUTH=(); [ -n "${ADMIN_TOKEN:-}" ] && ADMIN_AUTH=(-H "Authorization: Bearer $ADMIN_TOKEN")
USER_AUTH=();  [ -n "${USER_TOKEN:-}" ]  && USER_AUTH=(-H "Authorization: Bearer $USER_TOKEN")
JSON=(-H "Content-Type: application/fhir+json")
OUT=$(mktemp); APP_PID=""; FAIL=0

cleanup() {
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null || true
  curl -s -o /dev/null -X DELETE ${ADMIN_AUTH[@]+"${ADMIN_AUTH[@]}"} "$FHIR_BASE/App/demo.app" || true
  curl -s -o /dev/null -X DELETE ${ADMIN_AUTH[@]+"${ADMIN_AUTH[@]}"} "$FHIR_BASE/App/evil.app" || true
  rm -f "$OUT"
}
trap cleanup EXIT

# step <title> <expected-status-regex> <expected-body-substring or ""> <curl args...>
step() {
  local title="$1" want="$2" needle="$3"; shift 3
  echo; echo "== $title"
  local code; code=$(curl -s -o "$OUT" -w '%{http_code}' "$@")
  echo "HTTP $code (expected $want)"; head -c 700 "$OUT"; echo
  if ! [[ "$code" =~ $want ]] || { [ -n "$needle" ] && ! grep -q -- "$needle" "$OUT"; }; then
    echo "!! UNEXPECTED (wanted status $want${needle:+ and body containing '$needle'})"; FAIL=1
  fi
}

evil() { # evil <method> <path>
  printf '{"resourceType":"App","id":"evil.app","status":"active","endpoint":{"type":"http-rpc","url":"http://localhost:8888"},"operation":[{"id":"evil","method":"%s","path":"%s"}]}' "$1" "$2"
}

echo "== 1. Is the FHIR server up?"
code=$(curl -s -o /dev/null -w '%{http_code}' "$FHIR_BASE/metadata" || true)
echo "GET $FHIR_BASE/metadata -> HTTP $code"
if [ "$code" != "200" ]; then
  echo "Start the server first:  JAVA_HOME=<jdk21> mvn spring-boot:run -Pboot   (or: docker compose up -d)"
  exit 1
fi

echo; echo "== 2. Starting the demo App (python3 app.py, port ${DEMO_APP_PORT:-8888})"
python3 app.py & APP_PID=$!
sleep 1

step "3. Register the App: PUT App/demo.app" '^(200|201)$' '"demo.app"' \
  -X PUT "${JSON[@]}" ${ADMIN_AUTH[@]+"${ADMIN_AUTH[@]}"} --data @app.json "$FHIR_BASE/App/demo.app"

sleep 1 # route refresh runs just after the PUT response is sent
step "4. Public operation: GET \$hello" '^200$' 'Hello from an external App' \
  ${USER_AUTH[@]+"${USER_AUTH[@]}"} "$FHIR_BASE/\$hello"

step "5. Operation with route + query params: GET Patient/\$daily-report/2026-10-12" '^200$' '2026-10-12' \
  ${USER_AUTH[@]+"${USER_AUTH[@]}"} "$FHIR_BASE/Patient/\$daily-report/2026-10-12?ward=A&ward=B"

step "6. Guard rail: App claims GET \$export (server operation)" '^422$' 'shadow server operation' \
  -X PUT "${JSON[@]}" ${ADMIN_AUTH[@]+"${ADMIN_AUTH[@]}"} --data "$(evil GET '$export')" "$FHIR_BASE/App/evil.app"

step "7. Guard rail: App claims GET Patient/{id} (shadows a read)" '^422$' 'shadow' \
  -X PUT "${JSON[@]}" ${ADMIN_AUTH[@]+"${ADMIN_AUTH[@]}"} --data "$(evil GET 'Patient/{id}')" "$FHIR_BASE/App/evil.app"

echo; echo "(stopping the demo App)"; kill "$APP_PID"; wait "$APP_PID" 2>/dev/null || true; APP_PID=""
step "8. Guard rail: App down -> \$hello" '^502$' '' ${USER_AUTH[@]+"${USER_AUTH[@]}"} "$FHIR_BASE/\$hello"

step "9a. Cleanup: DELETE App/demo.app" '^(200|204)$' '' -X DELETE ${ADMIN_AUTH[@]+"${ADMIN_AUTH[@]}"} "$FHIR_BASE/App/demo.app"
step "9b. After delete, \$hello falls through to HAPI" '^[45][0-9][0-9]$' '' ${USER_AUTH[@]+"${USER_AUTH[@]}"} "$FHIR_BASE/\$hello"

echo
if [ "$FAIL" = 0 ]; then
  cat <<'DONE'
== What you just saw
 - An external process registered itself as an App and served $hello and Patient/$daily-report/{date}.
 - The server signed each call (Basic auth) and passed route params, query params and headers in an envelope.
 - Guard rails: Apps cannot shadow server operations ($export) or resource reads (Patient/{id}) - 422.
 - A dead App gives 502; deleting the App removes its routes.
All steps matched.
DONE
else
  echo "Some steps did not match expectations (see !! lines above)."; exit 1
fi
