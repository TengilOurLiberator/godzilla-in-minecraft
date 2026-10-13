# Dot-sourced by the other scripts:  . "$PSScriptRoot\common.ps1"
# Resolves the instance and the checkout (see tools\gz_env.py, same rules):
#   instance N (0..9): env GZ_INSTANCE > file <worktree>\.gz_instance > 0
#   $Wt   = checkout the script lives in (mod source and build output); $Main = D:\godzilla-vr (jdk, downloads, server template, work, game)
$script:Wt = Split-Path $PSScriptRoot -Parent
$script:Main = if ($env:GZ_MAIN) { $env:GZ_MAIN } else { "D:\godzilla-vr" }

$v = "$env:GZ_INSTANCE".Trim()
if (-not $v -and (Test-Path "$Wt\.gz_instance")) { $v = (Get-Content "$Wt\.gz_instance" -Raw).Trim() }
$script:Inst = if ($v) { [int]$v } else { 0 }
if ($Inst -lt 0 -or $Inst -gt 9) { throw "GZ_INSTANCE must be 0..9 (got $Inst): ports are 25567+N and 25577+N" }
$env:GZ_INSTANCE = "$Inst"      # python children and the rest of the session see the same instance

$script:Jdk = Join-Path $Main "jdk"
$script:Python = "D:\voice-assistant\.venv\Scripts\python.exe"
$script:WorkDir = if ($Inst -eq 0) { Join-Path $Main "work" } else { Join-Path $Main "work\inst$Inst" }
$script:ServerDir = if ($Inst -eq 0) { Join-Path $Main "server" } else { Join-Path $WorkDir "server" }
$script:TestGameDir = if ($Inst -eq 0) { Join-Path $Main "work\testgame" } else { Join-Path $WorkDir "testgame" }
$script:Port = 25567 + $Inst
$script:RconPort = 25577 + $Inst
$script:PlayerName = if ($Inst -eq 0) { "Tester" } else { "Tester$Inst" }
$script:LibsDir = Join-Path $Wt "mod\build\libs"
New-Item -ItemType Directory -Force $WorkDir | Out-Null

function Get-GodzillaJar {
    if (-not (Test-Path $LibsDir)) { return $null }
    Get-ChildItem $LibsDir -Filter "godzilla-*.jar" | Where-Object { $_.Name -notmatch "-sources|-dev" } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

# Teleport and climbey off for the game; worldScale.limitRange stays false (a limited range disconnects a 16x player).
function Set-ViveServerConfig([string]$dir) {
    $f = Join-Path $dir "config\vivecraft-server-config.toml"
    if (-not (Test-Path $f)) { return }
    $lines = Get-Content $f
    $section = ""; $changed = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*\[([^\]]+)\]') { $section = $Matches[1] }
        elseif (($section -eq "teleport" -or $section -eq "climbey") -and $lines[$i] -match '^(\s*)enabled\s*=\s*true\s*$') {
            $lines[$i] = $Matches[1] + "enabled = false"; $changed = $true
        }
        elseif ($section -eq "worldScale" -and $lines[$i] -match '^(\s*)limitRange\s*=\s*true\s*$') {
            $lines[$i] = $Matches[1] + "limitRange = false"; $changed = $true
        }
    }
    if ($changed) { [IO.File]::WriteAllLines($f, $lines, (New-Object Text.UTF8Encoding $false)) }   # no BOM
}

# Instance servers are copies of the template server (D:\godzilla-vr\server) without world, logs and the godzilla jar.
function Initialize-InstanceServer {
    if ($Inst -eq 0) { return }
    if (-not (Test-Path "$ServerDir\fabric-server-launch.jar")) {
        Write-Host "creating server for instance $Inst in $ServerDir (copy of $Main\server) ..."
        New-Item -ItemType Directory -Force $ServerDir | Out-Null
        robocopy (Join-Path $Main "server") $ServerDir /E /XD godzilla-world logs crash-reports /XF *.log /NFL /NDL /NJH /NJS /NP | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "robocopy of the server template failed ($LASTEXITCODE)" }
        $global:LASTEXITCODE = 0
        Get-ChildItem "$ServerDir\mods" -Filter "godzilla-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    }
    # ports of this instance (the template has instance 0's)
    $want = @{ "server-port" = "$Port"; "query.port" = "$Port"; "rcon.port" = "$RconPort"; "motd" = "Godzilla VR instance $Inst" }
    $props = Get-Content "$ServerDir\server.properties" | ForEach-Object {
        $k = ($_ -split "=", 2)[0]
        if ($want.ContainsKey($k)) { "$k=$($want[$k])" } else { $_ }
    }
    $props | Out-File -Encoding ascii "$ServerDir\server.properties"
}

# true if process $id is alive and its command line contains $needle (guards against a reused pid)
function Test-OurProcess([int]$id, [string]$needle) {
    $p = Get-CimInstance Win32_Process -Filter "ProcessId=$id" -ErrorAction SilentlyContinue
    return ($p -and $p.CommandLine -and $p.CommandLine.Contains($needle))
}
