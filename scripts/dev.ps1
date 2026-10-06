# Windows counterpart of dev.sh: starts the Spring Boot backend and the Angular
# frontend, waits until both answer, and streams their logs into this console.
# Press Ctrl+C once to stop both services.
#
# Usage (from the project root):
#   scripts\dev.cmd                      # works regardless of the PowerShell execution policy
#   powershell -File scripts\dev.ps1     # if scripts are allowed

$ErrorActionPreference = 'Stop'

$RootDir = Split-Path -Parent $PSScriptRoot
$FrontendDir = Join-Path $RootDir 'frontend'
$LogDir = Join-Path $RootDir '.dev-logs'

$BackendUrl = 'http://localhost:8080/api/simulation/status'
$FrontendUrl = 'http://localhost:4200'

function Assert-Command([string]$name, [string]$hint) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        Write-Host "[dev] '$name' was not found on PATH. $hint" -ForegroundColor Red
        exit 1
    }
}

# Each service runs through cmd.exe in its own hidden console, so Ctrl+C in this
# window is not forwarded to it (no "Terminate batch job (Y/N)?" prompts). Output is
# redirected to a log file that this script tails.
function Start-DevService([string]$name, [string]$workDir, [string]$command) {
    $logFile = Join-Path $LogDir "$name.log"
    Set-Content -Path $logFile -Value $null

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = "/d /s /c `"$command > `"$logFile`" 2>&1`""
    $psi.WorkingDirectory = $workDir
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.EnvironmentVariables['NG_CLI_ANALYTICS'] = 'false'

    return @{
        Name    = $name
        Process = [System.Diagnostics.Process]::Start($psi)
        Log     = $logFile
        Pos     = 0L
    }
}

# Kills cmd.exe and its whole process tree (mvn -> forked JVM, npm -> node).
function Stop-DevService($svc) {
    if ($null -eq $svc -or $svc.Process.HasExited) { return }
    # Via cmd.exe so taskkill stderr never becomes a PowerShell error inside finally.
    & cmd.exe /d /c "taskkill /PID $($svc.Process.Id) /T /F >nul 2>&1"
}

# Prints the complete lines appended to the service log since the last call.
function Write-NewLogLines($svc) {
    if ($null -eq $svc -or -not (Test-Path $svc.Log)) { return }
    $fs = [System.IO.File]::Open($svc.Log, 'Open', 'Read', 'ReadWrite')
    try {
        if ($fs.Length -le $svc.Pos) { return }
        $fs.Seek($svc.Pos, 'Begin') | Out-Null
        $buffer = New-Object byte[] ($fs.Length - $svc.Pos)
        $read = $fs.Read($buffer, 0, $buffer.Length)
        if ($read -le 0) { return }
        $lastNewline = [Array]::LastIndexOf($buffer, [byte]10, $read - 1)
        if ($lastNewline -lt 0) { return }
        $svc.Pos += $lastNewline + 1
        $text = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $lastNewline + 1)
        foreach ($line in ($text -split "`r?`n")) {
            if ($line -ne '') { Write-Host "[$($svc.Name)] $line" }
        }
    } finally {
        $fs.Dispose()
    }
}

function Test-Url([string]$url) {
    try {
        Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Wait-ForUrl($svc, [string]$url, [int]$timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Write-NewLogLines $svc
        if ($svc.Process.HasExited) {
            Write-NewLogLines $svc
            throw "$($svc.Name) exited before becoming ready (full log: $($svc.Log))"
        }
        if (Test-Url $url) {
            Write-Host "[dev] $($svc.Name) ready" -ForegroundColor Green
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "$($svc.Name) did not become ready: $url (full log: $($svc.Log))"
}

Assert-Command 'java' 'Install JDK 17 (e.g. winget install EclipseAdoptium.Temurin.17.JDK).'
Assert-Command 'mvn' 'Install Maven 3.9+ and add its bin folder to PATH.'
Assert-Command 'npm' 'Install Node.js (e.g. winget install OpenJS.NodeJS.22).'

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

if (-not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
    Write-Host '[dev] frontend dependencies missing, running npm ci'
    Push-Location $FrontendDir
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    } finally {
        Pop-Location
    }
}

$backend = $null
$frontend = $null
$exitCode = 0

try {
    Write-Host '[dev] starting backend on http://localhost:8080'
    $backend = Start-DevService 'backend' $RootDir 'mvn spring-boot:run'
    # The first run downloads every Maven dependency, so allow a generous timeout.
    Wait-ForUrl $backend $BackendUrl 600

    Write-Host '[dev] starting frontend on http://localhost:4200'
    $frontend = Start-DevService 'frontend' $FrontendDir 'npm start'
    Wait-ForUrl $frontend $FrontendUrl 300

    Write-Host "[dev] backend pid=$($backend.Process.Id), frontend pid=$($frontend.Process.Id)" -ForegroundColor Green
    Write-Host "[dev] open $FrontendUrl - press Ctrl+C to stop both services" -ForegroundColor Green

    while ($true) {
        Write-NewLogLines $backend
        Write-NewLogLines $frontend
        foreach ($svc in @($backend, $frontend)) {
            if ($svc.Process.HasExited) {
                Write-NewLogLines $svc
                throw "$($svc.Name) stopped unexpectedly (full log: $($svc.Log))"
            }
        }
        Start-Sleep -Milliseconds 500
    }
} catch {
    Write-Host "[dev] $($_.Exception.Message)" -ForegroundColor Red
    $exitCode = 1
} finally {
    # Runs on Ctrl+C as well as on errors.
    Write-Host '[dev] stopping services'
    Stop-DevService $frontend
    Stop-DevService $backend
}

exit $exitCode
