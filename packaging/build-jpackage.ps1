<#
.SYNOPSIS
    Builds the shaded jSerialComm-bundled jar with Maven and packages it with jpackage (Windows only).

.DESCRIPTION
    jpackage cannot cross-package for other operating systems; run this script on Windows
    to produce a Windows installer/app-image. Run build-jpackage.sh on Linux/macOS for those targets.
    The jar built by the maven-shade-plugin already contains jSerialComm's native libraries for
    all platforms, so no per-arch handling is needed here.

.PARAMETER Type
    jpackage output type: msi, exe or app-image. Default: app-image.

.PARAMETER AppName
    Display name of the packaged application.

.EXAMPLE
    .\packaging\build-jpackage.ps1
    .\packaging\build-jpackage.ps1 -Type exe
#>
[CmdletBinding()]
param(
    [ValidateSet('msi', 'exe', 'app-image')]
    [string]$Type = "app-image",
    [string]$AppName = "TouchDRO Desktop",
    [string]$VendorName = "DRO Desktop Project"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage not found on PATH. Install a JDK 17+ that includes jpackage and add its bin directory to PATH."
}
if (-not (Get-Command jlink -ErrorAction SilentlyContinue)) {
    throw "jlink not found on PATH. Install a JDK 17+ that includes jlink and add its bin directory to PATH."
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try {
    Write-Host "==> Building shaded jar with Maven..."
    cmd.exe /d /c "mvn -q -Dmaven.test.skip=true clean package 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

    $pomContent = Get-Content -Raw -Path (Join-Path $ProjectRoot 'pom.xml')
    $artifactId = [regex]::Match($pomContent, '<artifactId>([^<]+)</artifactId>').Groups[1].Value
    $version = [regex]::Match($pomContent, '<version>([^<]+)</version>').Groups[1].Value
    $jarName = "$artifactId-$version-allplatforms.jar"
    $jarPath = Join-Path $ProjectRoot "target\$jarName"

    if (-not (Test-Path $jarPath)) { throw "Jar not found at $jarPath" }

    $outDir = Join-Path $ProjectRoot "target\jpackage"
    if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
    New-Item -ItemType Directory -Path $outDir | Out-Null

    $runtimeDir = Join-Path $ProjectRoot "target\runtime"
    if (Test-Path $runtimeDir) { Remove-Item -Recurse -Force $runtimeDir }
    jlink `
        --add-modules java.desktop,java.prefs,jdk.unsupported `
        --strip-debug `
        --no-header-files `
        --no-man-pages `
        --output $runtimeDir

    # jpackage requires a purely numeric app-version, strip pre-release suffixes like "-beta".
    $appVersion = ($version -split '-')[0]

    $iconArgs = @()
    $iconPath = Join-Path $ProjectRoot "assets\touchdro.ico"
    if (Test-Path $iconPath) {
        $iconArgs = @("--icon", $iconPath)
    } else {
        Write-Warning "No icon found at assets\touchdro.ico, packaging without a custom icon."
    }

    $windowsInstallerArgs = @()
    if ($Type -ne 'app-image') {
        $windowsInstallerArgs = @("--win-shortcut", "--win-menu")
    }

    Write-Host "==> Running jpackage ($Type) for Windows..."
    jpackage `
        --type $Type `
        --input (Join-Path $ProjectRoot 'target') `
        --dest $outDir `
        --name $AppName `
        --app-version $appVersion `
        --vendor $VendorName `
        --main-jar $jarName `
        --main-class com.drodesktop.Main `
        --runtime-image $runtimeDir `
        --java-options "--enable-native-access=ALL-UNNAMED" `
        @windowsInstallerArgs `
        @iconArgs

    Write-Host "==> Done. Output in $outDir"
}
finally {
    Pop-Location
}
