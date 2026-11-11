$ErrorActionPreference = "Stop"

Write-Host "Starting SwiftFleet..." -ForegroundColor Cyan

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"

# Check if Docker Redis is running; if not, backend auto-falls back to In-Memory Geo/Lock store
try {
    docker info --format "{{.ServerVersion}}" | Out-Null
    Write-Host "Docker detected — starting Redis container on port 6379..." -ForegroundColor Green
    docker compose -f (Join-Path $root "docker-compose.yml") up -d redis | Out-Null
} catch {
    Write-Host "Docker daemon not running — backend will start in IN_MEMORY_FALLBACK mode automatically." -ForegroundColor Yellow
}

Write-Host "Launching Spring Boot backend (http://localhost:8085)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendDir'; mvn spring-boot:run"

Write-Host "Launching React Vite frontend (http://localhost:5173)..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$frontendDir'; npm run dev"

Write-Host "SwiftFleet starting! Open http://localhost:5173 (Split Demo) or http://localhost:5173/rider (Mobile GPS)" -ForegroundColor Cyan

