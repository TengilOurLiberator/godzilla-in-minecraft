# Build the mod with the project JDK (D:\godzilla-vr\jdk) and deploy the jar.
#   .\tools\build.ps1            build, then copy build\libs\godzilla-<version>.jar into server\mods and game\mods
#   .\tools\build.ps1 -Clean     clean first
#   .\tools\build.ps1 -Sources   also (re)generate the decompiled Minecraft sources (see docs\API-NOTES.md)
# Older godzilla-*.jar files in those folders are replaced. Nothing on PATH / JAVA_HOME is changed (process scope only).
param([switch]$Clean, [switch]$Sources)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$mod = Join-Path $root "mod"
$env:JAVA_HOME = Join-Path $root "jdk"

if (-not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { throw "project JDK missing: $env:JAVA_HOME (see docs\API-NOTES.md, 'Toolchain')" }

$tasks = @()
if ($Clean) { $tasks += "clean" }
$tasks += "build"
Set-Location $mod
# --no-daemon: no java process is left behind afterwards
& "$mod\gradlew.bat" --no-daemon --console=plain @tasks
if ($LASTEXITCODE -ne 0) { throw "gradle build failed (exit $LASTEXITCODE)" }
if ($Sources) {
    & "$mod\gradlew.bat" --no-daemon --console=plain genSources
    if ($LASTEXITCODE -ne 0) { throw "gradle genSources failed (exit $LASTEXITCODE)" }
}

$jar = Get-ChildItem "$mod\build\libs" -Filter "godzilla-*.jar" | Where-Object { $_.Name -notmatch "-sources|-dev" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "no godzilla jar in $mod\build\libs" }

foreach ($dest in @("server\mods", "game\mods")) {
    $dir = Join-Path $root $dest
    if (-not (Test-Path $dir)) { Write-Host "skip $dest (does not exist yet)"; continue }
    try {
        Get-ChildItem $dir -Filter "godzilla-*.jar" | Remove-Item -Force -ErrorAction Stop
        Copy-Item $jar.FullName $dir -ErrorAction Stop
        Write-Host "deployed $($jar.Name) -> $dest"
    } catch {
        # a running server / game keeps its jar open on Windows
        Write-Warning "could not replace the jar in $dest (is the server or the game running? stop it and run tools\build.ps1 again): $($_.Exception.Message)"
    }
}
Write-Host "built $($jar.FullName) ($([math]::Round($jar.Length/1KB)) KB)"
