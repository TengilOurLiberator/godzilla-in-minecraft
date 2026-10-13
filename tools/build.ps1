# Build the mod of THIS checkout (main checkout or a git worktree) with the project JDK and deploy the jar.
#   .\tools\build.ps1            build, then copy build\libs\godzilla-<version>.jar into this instance's server\mods and test game mods
#   .\tools\build.ps1 -Clean     clean first
#   .\tools\build.ps1 -Sources   make the Minecraft/Fabric/Vivecraft sources browsable (runs tools\sources.ps1, shared in the main checkout's work\)
# Instance (GZ_INSTANCE, default 0; see tools\gz_env.py):
#   0: deploys to the main checkout's server\mods, game\mods (the VR client) and work\testgame\mods
#   N: deploys to D:\godzilla-vr\work\inst<N>\server\mods and work\inst<N>\testgame\mods (the server folder is created on first use)
# Older godzilla-*.jar files in those folders are replaced. Nothing on PATH / JAVA_HOME is changed (process scope only).
# The first build in a new worktree creates its own mod\.gradle Loom cache (see docs\API-NOTES.md for the time it takes).
param([switch]$Clean, [switch]$Sources)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
$mod = Join-Path $Wt "mod"
$env:JAVA_HOME = $Jdk

if ($Sources) { & "$PSScriptRoot\sources.ps1"; return }
if (-not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { throw "project JDK missing: $env:JAVA_HOME (see docs\API-NOTES.md, 'Toolchain')" }

$tasks = @()
if ($Clean) { $tasks += "clean" }
$tasks += "build"
Push-Location $mod
# --no-daemon: no java process is left behind afterwards. Instances run next to each other, so keep the Gradle JVM modest.
$gradleArgs = @("--no-daemon", "--console=plain")
if ($Inst -gt 0) { $gradleArgs += "-Dorg.gradle.jvmargs=-Xmx2G" }
try { & "$mod\gradlew.bat" @gradleArgs @tasks } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw "gradle build failed (exit $LASTEXITCODE)" }

$jar = Get-GodzillaJar
if (-not $jar) { throw "no godzilla jar in $LibsDir" }

Initialize-InstanceServer
$dests = @("$ServerDir\mods", "$TestGameDir\mods")
if ($Inst -eq 0) { $dests += (Join-Path $Main "game\mods") }
foreach ($dir in $dests) {
    if (-not (Test-Path $dir)) { Write-Host "skip $dir (does not exist yet)"; continue }
    try {
        Get-ChildItem $dir -Filter "godzilla-*.jar" | Remove-Item -Force -ErrorAction Stop
        Copy-Item $jar.FullName $dir -ErrorAction Stop
        Write-Host "deployed $($jar.Name) -> $dir"
    } catch {
        # a running server / game keeps its jar open on Windows
        Write-Warning "could not replace the jar in $dir (is the server or the game running? stop it and run tools\build.ps1 again): $($_.Exception.Message)"
    }
}
Write-Host "built $($jar.FullName) ($([math]::Round($jar.Length/1KB)) KB) for instance $Inst"
