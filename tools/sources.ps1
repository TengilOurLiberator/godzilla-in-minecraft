# Make the Minecraft, Fabric API and Vivecraft sources browsable under work\ (not in git). Safe to run again.
#   work\mcsrc\common\   decompiled Minecraft 26.3 (server + shared code), net\minecraft\...
#   work\mcsrc\client\   decompiled Minecraft 26.3 client-only code (renderers, models, GUI)
#   work\fabricsrc\<module>\   Fabric API 0.162.0+26.3 sources, one folder per module (fetched from maven.fabricmc.net)
#   work\vivesrc\        Vivecraft 26.3-1.3.17 decompiled with Vineflower (org\vivecraft\api\... is the public API)
# Minecraft is unobfuscated, so the decompiled code has the real names (parameter names too).
param([switch]$Force)

$ErrorActionPreference = "Stop"
# Always works on the MAIN checkout (D:\godzilla-vr, or GZ_MAIN): the output in its work\ is shared and read-only for everybody,
# and the Loom cache that has the decompiled sources is the main checkout's mod\.gradle.
$root = if ($env:GZ_MAIN) { $env:GZ_MAIN } else { "D:\godzilla-vr" }
$mod = Join-Path $root "mod"
$work = Join-Path $root "work"
$jdk = Join-Path $root "jdk"
$env:JAVA_HOME = $jdk
$jar = Join-Path $jdk "bin\jar.exe"
$ProgressPreference = "SilentlyContinue"

# 1. Minecraft: Loom's genSources decompiles with Vineflower into the Loom cache, then unpack the *-sources.jar files
$cache = Join-Path $mod ".gradle\loom-cache\minecraftMaven\net\minecraft"
$srcJars = if (Test-Path $cache) { Get-ChildItem $cache -Recurse -Filter "*-sources.jar" } else { @() }
if ($Force -or $srcJars.Count -lt 2) {
    Set-Location $mod
    & "$mod\gradlew.bat" --no-daemon --console=plain genSources
    if ($LASTEXITCODE -ne 0) { throw "genSources failed" }
    $srcJars = Get-ChildItem $cache -Recurse -Filter "*-sources.jar"
}
foreach ($j in $srcJars) {
    $kind = if ($j.Name -match "clientOnly") { "client" } else { "common" }
    $out = Join-Path $work "mcsrc\$kind"
    if ((Test-Path $out) -and -not $Force) { Write-Host "mcsrc\$kind exists"; continue }
    Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory $out | Out-Null
    Push-Location $out; & $jar xf $j.FullName; Pop-Location
    Write-Host "unpacked $($j.Name) -> work\mcsrc\$kind"
}

# 2. Fabric API module sources (the modules Loom resolved are in the Gradle cache; names and versions come from there)
$fabricOut = Join-Path $work "fabricsrc"
$modules = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\net.fabricmc.fabric-api"
if (Test-Path $modules) {
    foreach ($m in Get-ChildItem $modules -Directory) {
        if ($m.Name -eq "fabric-api") { continue }
        $dst = Join-Path $fabricOut $m.Name
        if ((Test-Path $dst) -and -not $Force) { continue }
        $j = Get-ChildItem $m.FullName -Recurse -Filter "*.jar" | Where-Object { $_.Name -notmatch "sources" } | Select-Object -First 1
        if (-not $j) { continue }
        $ver = $j.Name.Substring($m.Name.Length + 1).Replace(".jar", "")
        $url = "https://maven.fabricmc.net/net/fabricmc/fabric-api/$($m.Name)/$ver/$($m.Name)-$ver-sources.jar"
        $tmp = Join-Path $work "$($m.Name).jar"
        try {
            New-Item -ItemType Directory -Force $fabricOut | Out-Null
            Invoke-WebRequest $url -OutFile $tmp -UseBasicParsing
            Remove-Item $dst -Recurse -Force -ErrorAction SilentlyContinue
            New-Item -ItemType Directory $dst | Out-Null
            Push-Location $dst; & $jar xf $tmp; Pop-Location
            Remove-Item $tmp
        } catch { Write-Warning "no sources for $($m.Name) $ver" }
    }
    Write-Host "fabric api sources in work\fabricsrc"
}

# 3. Vivecraft: decompile the mod jar (Vineflower ships with Loom)
$vive = Join-Path $root "downloads\vivecraft-26.3-1.3.17-fabric.jar"
$viveOut = Join-Path $work "vivesrc"
if ((Test-Path $vive) -and ($Force -or -not (Test-Path $viveOut))) {
    $vf = Get-ChildItem (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.vineflower\vineflower") -Recurse -Filter "vineflower-*.jar" |
        Where-Object { $_.Name -notmatch "sources|javadoc" } | Select-Object -First 1
    if ($vf) {
        Remove-Item $viveOut -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory $viveOut | Out-Null
        & "$jdk\bin\java.exe" -jar $vf.FullName --silent=true $vive $viveOut | Out-Null
        Write-Host "vivecraft decompiled to work\vivesrc"
    }
}
