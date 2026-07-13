<#
  Smoke test against the REAL simulator container (Windows / Docker Desktop).
  Prereq: the app + MySQL are running (docker compose up) and reachable on :3003.
#>
$ErrorActionPreference = "Stop"
$AppUrl = if ($env:APP_URL) { $env:APP_URL } else { "http://localhost:3003" }
$SimImage = "cfontes0estapar/garage-sim:1.0.0"

Write-Host "==> Starting simulator ($SimImage)"
$SimId = docker run -d -p 3000:3000 -e CLIENT_WEBHOOK_URL="http://host.docker.internal:3003/webhook" $SimImage

try {
    Write-Host "==> Waiting for readiness (garage configuration sync)"
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $health = Invoke-RestMethod "$AppUrl/actuator/health/readiness" -ErrorAction Stop
            if ($health.status -eq "UP") { Write-Host "    ready"; break }
        } catch { }
        Start-Sleep -Seconds 2
    }

    Write-Host "==> Letting events flow for 20s"
    Start-Sleep -Seconds 20

    Write-Host "==> Event metrics"
    try { Invoke-RestMethod "$AppUrl/actuator/metrics/garage_webhook_events_total" | ConvertTo-Json -Depth 5 } catch { }

    Write-Host "==> Revenue for sector A today (UTC date)"
    $today = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
    try { Invoke-RestMethod "$AppUrl/revenue?date=$today&sector=A" | ConvertTo-Json } catch { }

    Write-Host "==> Smoke test done"
}
finally {
    Write-Host "==> Stopping simulator"
    docker rm -f $SimId | Out-Null
}
