# Start the local Godzilla server of this instance (default instance 0: port 25567, RCON 25577, server\; instance N: see tools\gz_env.py).
#   .\tools\server.ps1                  run in this window (it stays open while the server runs)
#   .\tools\server.ps1 -NewWorld        throw the world away first
#   .\tools\server.ps1 -Offline         accept clients without a Minecraft account (test client); also turns off secure-profile enforcement
#   .\tools\server.ps1 -Background      run hidden (log: <server dir>\logs\latest.log), return when RCON answers
#   .\tools\server.ps1 -Stop            stop THIS instance's background server (RCON "stop"), wait for the process to exit
# Instance: $env:GZ_INSTANCE = 1 (or tools\dev.ps1 instance 1). Instances > 0 run from a copy of the template server in
# D:\godzilla-vr\work\inst<N>\server with -Xmx3G; the godzilla jar of THIS checkout's build is copied into its mods at start.
param([switch]$NewWorld, [switch]$Offline, [switch]$Background, [switch]$Stop)

. "$PSScriptRoot\common.ps1"
$srv = $ServerDir
$java = Join-Path $Jdk "bin\java.exe"
$pidFile = Join-Path $WorkDir "server.pid"
$marker = "-Dgodzilla.instance=$Inst"      # in the command line: identifies this instance's server process

function Get-ServerProcess {
    if (Test-Path $pidFile) {
        $id = [int](Get-Content $pidFile)
        if (Test-OurProcess $id $marker) { return (Get-Process -Id $id -ErrorAction SilentlyContinue) }
    }
    return $null
}

if ($Stop) {
    $p = Get-ServerProcess
    if (-not $p) { Write-Host "no background server of instance $Inst is running"; Remove-Item $pidFile -ErrorAction SilentlyContinue; exit 0 }
    & $Python -I (Join-Path $PSScriptRoot "rcon.py") "stop" *> $null
    if (-not $p.WaitForExit(90000)) { Stop-Process -Id $p.Id -Force }
    Remove-Item $pidFile -ErrorAction SilentlyContinue
    Write-Host "server of instance $Inst stopped"
    exit 0
}

if (Get-ServerProcess) { Write-Host "a background server of instance $Inst is already running (pid $((Get-ServerProcess).Id))"; exit 1 }

Initialize-InstanceServer
if ($NewWorld -and (Test-Path "$srv\godzilla-world")) { Remove-Item "$srv\godzilla-world" -Recurse -Force -Confirm:$false }

# this checkout's jar into the server's mods (a server that is not running does not lock it)
$freshWorld = -not (Test-Path "$srv\godzilla-world")
$jar = Get-GodzillaJar
if ($jar) {
    Get-ChildItem "$srv\mods" -Filter "godzilla-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item $jar.FullName "$srv\mods"
} else { Write-Warning "no godzilla jar in $LibsDir (run tools\build.ps1); starting with what is in $srv\mods" }
Set-ViveServerConfig $srv

$props = Get-Content "$srv\server.properties"
$mode = if ($Offline) { "false" } else { "true" }
$props = $props | ForEach-Object {
    if ($_ -like "online-mode=*") { "online-mode=$mode" }
    elseif ($_ -like "enforce-secure-profile=*") { "enforce-secure-profile=$mode" }
    else { $_ }
}
$props | Out-File -Encoding ascii "$srv\server.properties"

$mem = if ($Inst -eq 0) { @("-Xms4G", "-Xmx10G") } else { @("-Xms1G", "-Xmx3G") }
$javaArgs = $mem + @($marker, "-jar", "fabric-server-launch.jar", "nogui")
if ($Background) {
    # Created through WMI so the java process inherits none of this shell's handles (otherwise a calling tool that waits for
    # the pipes to close would hang for as long as the server runs). The console output is the same as logs\latest.log.
    $cmdLine = "`"$java`" " + ($javaArgs -join " ")
    $r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmdLine; CurrentDirectory = $srv }
    if ($r.ReturnValue -ne 0) { Write-Host "could not start the server (WMI return $($r.ReturnValue))"; exit 1 }
    $p = Get-Process -Id $r.ProcessId
    $p.Id | Out-File -Encoding ascii $pidFile
    Write-Host "server of instance $Inst starting (pid $($p.Id), port $Port, rcon $RconPort), waiting for RCON..."
    $deadline = (Get-Date).AddSeconds(300)
    do {
        Start-Sleep -Seconds 2
        if ($p.HasExited) { Write-Host "server exited early (code $($p.ExitCode)); see $srv\logs\latest.log"; Remove-Item $pidFile -ErrorAction SilentlyContinue; exit 1 }
        & $Python -I (Join-Path $PSScriptRoot "rcon.py") "list" *> $null
    } until ($LASTEXITCODE -eq 0 -or (Get-Date) -gt $deadline)
    if ($LASTEXITCODE -ne 0) { Write-Host "RCON did not answer within 300 s"; exit 1 }
    if ($freshWorld) {
        # a new flat world picks its spawn at y=-63, inside the stone, and a joining player suffocates; put it on the sea instead
        & $Python -I (Join-Path $PSScriptRoot "rcon.py") "forceload add 0 0" "setworldspawn 0 64 0" *> $null
    }
    Write-Host "server is up (RCON answers)"
    exit 0
}

Set-Location $srv
& $java @javaArgs
