#requires -Version 5.1
<#
.SYNOPSIS
  Verifies the local development environment for the Smart Competitive Examination Platform.

.DESCRIPTION
  Checks the required toolchain and connectivity (specs/09-P0-Foundation-V1.5.md §97) and
  reports actionable errors. It does not modify the environment.

.NOTES
  Run from the repository root: ./scripts/verify-environment.ps1
#>

$ErrorActionPreference = "Continue"
$failures = 0

function Write-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail, [string]$Remedy = "")
    if ($Ok) {
        Write-Host ("  [ OK ] {0}: {1}" -f $Name, $Detail) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] {0}: {1}" -f $Name, $Detail) -ForegroundColor Red
        if ($Remedy) { Write-Host ("         remedy: {0}" -f $Remedy) -ForegroundColor Yellow }
        $script:failures++
    }
}

Write-Host "`nSmart Competitive Examination Platform - environment verification`n" -ForegroundColor Cyan

# --- Java ---
$java = Get-Command java -ErrorAction SilentlyContinue
if ($java) {
    $javaVersionOutput = (& java -version 2>&1 | Out-String)
    if ($javaVersionOutput -match 'version "(\d+)') {
        $major = [int]$Matches[1]
        $ok = $major -ge 21
        Write-Check "Java" $ok "version $major" "Install JDK 21 (approved fallback) or JDK 25."
    } else {
        Write-Check "Java" $false "version undetermined" "Install JDK 21 or JDK 25."
    }
} else {
    Write-Check "Java" $false "not found" "Install JDK 21 or JDK 25 and add it to PATH."
}

# --- Maven wrapper ---
$mvnw = Join-Path (Get-Location) "backend/mvnw"
Write-Check "Maven wrapper" (Test-Path $mvnw) $(if (Test-Path $mvnw) { "backend/mvnw present" } else { "missing" }) "Ensure backend/mvnw is committed."

# --- Node ---
$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    $nodeVersion = (& node -v).TrimStart('v')
    $nodeMajor = [int]($nodeVersion.Split('.')[0])
    Write-Check "Node.js" ($nodeMajor -ge 20) "v$nodeVersion" "Install Node.js 22 (LTS)."
} else {
    Write-Check "Node.js" $false "not found" "Install Node.js 22 (LTS)."
}

# --- Docker ---
$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($docker) {
    $dockerVersion = (docker --version 2>$null)
    Write-Check "Docker" $true "$dockerVersion"
    try {
        docker info *> $null
        Write-Check "Docker daemon" $true "reachable"
    } catch {
        Write-Check "Docker daemon" $false "not reachable" "Start Docker Desktop / the Docker daemon."
    }
} else {
    Write-Check "Docker" $false "not found" "Install Docker with Compose v2 (required for the containerized stack)."
}

# --- Environment variables derived from .env.example ---
$envPath = Join-Path (Get-Location) ".env"
if (Test-Path $envPath) {
    Write-Check "Local .env" $true "present (not committed)"
} else {
    Write-Host "  [INFO] Local .env: not present - copy .env.example to .env for local runs." -ForegroundColor DarkGray
}

# --- Database connectivity (informational; only if a client is available) ---
$pgHost = if ($env:POSTGRES_HOST) { $env:POSTGRES_HOST } else { "localhost" }
$pgPort = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { "5432" }
$tcp = Test-NetConnection -ComputerName $pgHost -Port $pgPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if ($tcp -and $tcp.TcpTestSucceeded) {
    Write-Check "PostgreSQL port" $true "${pgHost}:${pgPort} reachable"
} else {
    Write-Host ("  [INFO] PostgreSQL port: {0}:{1} not reachable - start it with 'docker compose up -d postgres'." -f $pgHost, $pgPort) -ForegroundColor DarkGray
}

# --- Redis connectivity (informational) ---
$redisHost = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { "localhost" }
$redisPort = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { "6379" }
$redisTcp = Test-NetConnection -ComputerName $redisHost -Port $redisPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if ($redisTcp -and $redisTcp.TcpTestSucceeded) {
    Write-Check "Redis port" $true "${redisHost}:${redisPort} reachable"
} else {
    Write-Host ("  [INFO] Redis port: {0}:{1} not reachable - start it with 'docker compose up -d redis'." -f $redisHost, $redisPort) -ForegroundColor DarkGray
}

Write-Host ""
if ($failures -gt 0) {
    Write-Host ("Environment verification FAILED with {0} issue(s)." -f $failures) -ForegroundColor Red
    exit 1
}
Write-Host "Environment verification PASSED." -ForegroundColor Green
exit 0
