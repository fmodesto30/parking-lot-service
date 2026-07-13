#!/usr/bin/env bash
# Smoke test against the REAL simulator container.
# Prereq: the app + MySQL are running (docker compose up) and reachable on :3003.
# The app calls the simulator's GET /garage on startup, which starts the event stream.
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:3003}"
SIM_IMAGE="cfontes0estapar/garage-sim:1.0.0"

echo "==> Starting simulator ($SIM_IMAGE)"
# On Linux, --network=host lets the simulator reach the app on localhost:3003.
# On Docker Desktop, publish 3000 and point the webhook at host.docker.internal.
if [[ "$(uname -s)" == "Linux" ]]; then
  SIM_ID=$(docker run -d --network=host "$SIM_IMAGE")
else
  SIM_ID=$(docker run -d -p 3000:3000 -e CLIENT_WEBHOOK_URL="http://host.docker.internal:3003/webhook" "$SIM_IMAGE")
fi
trap 'echo "==> Stopping simulator"; docker rm -f "$SIM_ID" >/dev/null' EXIT

echo "==> Waiting for readiness (garage configuration sync)"
for _ in $(seq 1 60); do
  if curl -fs "$APP_URL/actuator/health/readiness" | grep -q '"status":"UP"'; then
    echo "    ready"
    break
  fi
  sleep 2
done

echo "==> Letting events flow for 20s"
sleep 20

echo "==> Event metrics"
curl -fs "$APP_URL/actuator/metrics/garage_webhook_events_total" || true
echo
echo "==> Active sessions"
curl -fs "$APP_URL/actuator/metrics/garage_active_sessions" || true
echo
echo "==> Revenue for sector A today (UTC date)"
curl -fs "$APP_URL/revenue?date=$(date -u +%Y-%m-%d)&sector=A" || true
echo
echo "==> Smoke test done"
