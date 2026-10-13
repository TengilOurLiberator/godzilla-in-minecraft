# One-command dev loop for one instance (own server + own flat test client). Run from your worktree (or the main checkout).
#
#   powershell -File tools\dev.ps1 instance 2          remember "this worktree is instance 2" (writes .gz_instance, git-ignored); do this once
#   powershell -File tools\dev.ps1 up                  build, start the server (NEW world, offline, hidden), start the test client and join
#   powershell -File tools\dev.ps1 rebuild             stop client and server, build, start both again KEEPING the world
#   powershell -File tools\dev.ps1 down                stop client and server (only this instance's processes)
#   powershell -File tools\dev.ps1 rcon "<cmd>" ...    RCON commands on this instance's server (output printed)
#   powershell -File tools\dev.ps1 shot <name>         screenshot of this instance's client -> <shots dir>\<name>.png
#   powershell -File tools\dev.ps1 status | log [N]    what is running; last N lines of the client log
# Add  -Instance N  to any verb to override the instance for that call (otherwise: $env:GZ_INSTANCE, then .gz_instance, then 0).
# Instance 0 is the main checkout's classic setup (ports 25567/25577); worktrees use 1..9 (ports 25567+N, 25577+N, player Tester<N>).
param([Parameter(Position = 0)][string]$Verb = "help",
      [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest = @(),
      [int]$Instance = -1)

$ErrorActionPreference = "Stop"
if ($Instance -ge 0) { $env:GZ_INSTANCE = "$Instance" }
if ($Verb -eq "instance") {
    if ($Rest.Count -ne 1 -or $Rest[0] -notmatch '^[0-9]$') { throw "usage: dev.ps1 instance <0..9>" }
    $wt = Split-Path $PSScriptRoot -Parent
    Set-Content -Path (Join-Path $wt ".gz_instance") -Value $Rest[0] -Encoding ascii
    Write-Host "this checkout ($wt) is now instance $($Rest[0])"
    return
}
. "$PSScriptRoot\common.ps1"
$tc = Join-Path $PSScriptRoot "testclient.py"
$rc = Join-Path $PSScriptRoot "rcon.py"

function Stop-Both {
    & $Python -I $tc stop
    & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\server.ps1" -Stop
}
function Start-Both([bool]$NewWorld) {
    $a = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "$PSScriptRoot\server.ps1", "-Offline", "-Background")
    if ($NewWorld) { $a += "-NewWorld" }
    & powershell @a
    if ($LASTEXITCODE -ne 0) { throw "server did not start (see $ServerDir\logs\latest.log)" }
    & $Python -I $tc start
    if ($LASTEXITCODE -ne 0) { throw "test client did not join (see $TestGameDir\client.log)" }
    Write-Host "instance $Inst is up: server localhost:$Port rcon $RconPort, player $PlayerName"
}
function Build { & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\build.ps1"; if ($LASTEXITCODE -ne 0) { throw "build failed" } }

try {
    switch ($Verb) {
        "up"      { Stop-Both; Build; Start-Both $true }
        "rebuild" { Stop-Both; Build; Start-Both $false }
        "down"    { Stop-Both }
        "rcon"    { if ($Rest.Count -eq 0) { throw 'usage: dev.ps1 rcon "<cmd>" ["<cmd>" ...]' }; & $Python -I $rc @Rest; exit $LASTEXITCODE }
        "shot"    { if ($Rest.Count -lt 1) { throw "usage: dev.ps1 shot <name>" }; & $Python -I $tc shot @Rest; exit $LASTEXITCODE }
        "status"  { & $Python -I $tc status; if (Test-Path "$WorkDir\server.pid") { Write-Host "server pid file: $(Get-Content "$WorkDir\server.pid")" } else { Write-Host "no background server recorded" } }
        "log"     { & $Python -I $tc log @Rest }
        default   { Get-Content $PSCommandPath -TotalCount 12 | ForEach-Object { $_ -replace '^# ?', '' } }
    }
} catch {
    Write-Host "dev.ps1 $Verb failed: $($_.Exception.Message)"
    exit 1
}
