# Start the local Godzilla server (port 25567, RCON 25577) with the project JDK.
#   .\tools\server.ps1                  run in this window (it stays open while the server runs)
#   .\tools\server.ps1 -NewWorld        throw the world away first
#   .\tools\server.ps1 -Offline         accept clients without a Minecraft account (test client); also turns off secure-profile enforcement
#   .\tools\server.ps1 -Background      run hidden (log: server\logs\latest.log), return when RCON answers
#   .\tools\server.ps1 -Stop            stop a running server (RCON "stop"), wait for the process to exit
param([switch]$NewWorld, [switch]$Offline, [switch]$Background, [switch]$Stop)

$root = Split-Path $PSScriptRoot -Parent
$srv = Join-Path $root "server"
$java = Join-Path $root "jdk\bin\java.exe"
$python = "D:\voice-assistant\.venv\Scripts\python.exe"
$pidFile = Join-Path $root "work\server.pid"
New-Item -ItemType Directory -Force (Join-Path $root "work") | Out-Null

function Get-ServerProcess {
    if (Test-Path $pidFile) {
        $p = Get-Process -Id ([int](Get-Content $pidFile)) -ErrorAction SilentlyContinue
        if ($p -and $p.ProcessName -eq "java") { return $p }
    }
    return $null
}

if ($Stop) {
    $p = Get-ServerProcess
    if (-not $p) { Write-Host "no server started by -Background is running"; exit 0 }
    & $python -I (Join-Path $PSScriptRoot "rcon.py") "stop" | Out-Null
    if (-not $p.WaitForExit(90000)) { Stop-Process -Id $p.Id -Force }
    Remove-Item $pidFile -ErrorAction SilentlyContinue
    Write-Host "server stopped"
    exit 0
}

if (Get-ServerProcess) { Write-Host "a background server is already running (pid $((Get-ServerProcess).Id))"; exit 1 }

if ($NewWorld -and (Test-Path "$srv\godzilla-world")) { Remove-Item "$srv\godzilla-world" -Recurse -Force -Confirm:$false }

$props = Get-Content "$srv\server.properties"
$mode = if ($Offline) { "false" } else { "true" }
$props = $props | ForEach-Object {
    if ($_ -like "online-mode=*") { "online-mode=$mode" }
    elseif ($_ -like "enforce-secure-profile=*") { "enforce-secure-profile=$mode" }
    else { $_ }
}
$props | Out-File -Encoding ascii "$srv\server.properties"

$javaArgs = @("-Xms4G", "-Xmx10G", "-jar", "fabric-server-launch.jar", "nogui")
if ($Background) {
    $log = "$srv\logs\latest.log"
    # Created through WMI so the java process inherits none of this shell's handles (otherwise a calling tool that waits for
    # the pipes to close would hang for as long as the server runs). The console output is the same as server\logs\latest.log.
    $cmdLine = "`"$java`" " + ($javaArgs -join " ")
    $r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmdLine; CurrentDirectory = $srv }
    if ($r.ReturnValue -ne 0) { Write-Host "could not start the server (WMI return $($r.ReturnValue))"; exit 1 }
    $p = Get-Process -Id $r.ProcessId
    $p.Id | Out-File -Encoding ascii $pidFile
    Write-Host "server starting (pid $($p.Id)), waiting for RCON..."
    $deadline = (Get-Date).AddSeconds(300)
    do {
        Start-Sleep -Seconds 2
        if ($p.HasExited) { Write-Host "server exited early (code $($p.ExitCode)); see $log and $srv\logs\latest.log"; exit 1 }
        & $python -I (Join-Path $PSScriptRoot "rcon.py") "list" *> $null
    } until ($LASTEXITCODE -eq 0 -or (Get-Date) -gt $deadline)
    if ($LASTEXITCODE -ne 0) { Write-Host "RCON did not answer within 300 s"; exit 1 }
    Write-Host "server is up (RCON answers)"
    exit 0
}

Set-Location $srv
& $java @javaArgs
