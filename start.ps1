# Start Godzilla VR's server.
#   .\start.ps1             build the mod if the jar is out of date, start the server in its own window, wait until it answers
#   .\start.ps1 -NewWorld   throw the world away first (a fresh city stamp; about 10 s longer)
#   .\start.ps1 -Offline    accept clients without a Minecraft account (the flat test client); the real VR client needs the default
#   .\start.ps1 -NoBuild    never build
# Then: SteamVR, the Minecraft launcher profile "Godzilla VR", Multiplayer, "Godzilla".
param([switch]$NewWorld, [switch]$Offline, [switch]$NoBuild)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$python = "D:\voice-assistant\.venv\Scripts\python.exe"
$rcon = Join-Path $root "tools\rcon.py"

function Test-Server { & $python -I $rcon "list" *> $null; return ($LASTEXITCODE -eq 0) }

if (Test-Server) {
    Write-Host "A Godzilla server already answers on RCON (port 25577). Close its window (or run tools\server.ps1 -Stop) before starting another."
    exit 1
}

# the jar is stale when any source, resource, the city/audio output or the build script is newer than it
if (-not $NoBuild) {
    $jar = Get-ChildItem (Join-Path $root "mod\build\libs") -Filter "godzilla-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources*" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $inputs = @(Get-ChildItem (Join-Path $root "mod\src") -Recurse -File) +
        @(Get-ChildItem (Join-Path $root "mod\build.gradle"), (Join-Path $root "mod\gradle.properties") -ErrorAction SilentlyContinue) +
        @(Get-ChildItem (Join-Path $root "city\out"), (Join-Path $root "audio\out") -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Extension -ne ".wav" })
    $newest = ($inputs | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
    if (-not $jar -or ($newest -and $newest.LastWriteTime -gt $jar.LastWriteTime)) {
        Write-Host "Building the mod ($(if ($jar) { "newer: " + $newest.Name } else { "no jar yet" }))..."
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "tools\build.ps1")
        if ($LASTEXITCODE -ne 0) { throw "build failed" }
    } else {
        Write-Host "Jar is up to date ($($jar.Name))."
    }
}

$serverArgs = @("-NoExit", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $root "tools\server.ps1"))
if ($NewWorld) { $serverArgs += "-NewWorld" }
if ($Offline) { $serverArgs += "-Offline" }
Start-Process powershell -ArgumentList $serverArgs

Write-Host "Waiting for the server (the first start of a world also stamps the city, 15 to 30 s)..."
$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 2
    if ((Get-Date) -gt $deadline) { throw "the server did not answer within 5 minutes; look at its window and server\logs\latest.log" }
} until (Test-Server)

# RCON answers a moment before the city is in the world: wait for the stamp (or the note that the world already has it)
$log = Join-Path $root "server\logs\latest.log"
$deadline = (Get-Date).AddMinutes(3)
do {
    Start-Sleep -Seconds 1
    $text = if (Test-Path $log) { Get-Content $log -Raw -ErrorAction SilentlyContinue } else { "" }
} until ($text -match "godzilla city: (stamped|this world already holds)" -or (Get-Date) -gt $deadline)

Write-Host ""
Write-Host "Server is up ($(if ($Offline) { 'OFFLINE mode, test clients only' } else { 'online mode' })), port 25567."
Write-Host "Start SteamVR, then the launcher profile 'Godzilla VR' -> Multiplayer -> 'Godzilla'."
Write-Host "You arrive on a pier facing Tokyo: smash the SMASH ME sign (or pull the trigger at it) to begin."
Write-Host "Stop the server with the 'stop' command in its window (or tools\server.ps1 -Stop)."
