param(
  [string]$JdkVersion = "25",
  [string]$JdkDir = "$PSScriptRoot\..\.jdk",
  [string]$WixDir = "$PSScriptRoot\..\.wix",
  [string]$MavenArgs = "clean compile"
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Get-TemurinJdkAsset {
  param([string]$Version)
  $url = "https://api.adoptium.net/v3/assets/latest/$Version/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse"
  $asset = (Invoke-RestMethod -Uri $url -Headers @{Accept='application/json'} | Select-Object -First 1)
  return $asset.binary.package
}

function Ensure-Dir([string]$Path) {
  if (!(Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
  }
}

function Ensure-Jdk {
  param([string]$Version, [string]$Dir)
  Ensure-Dir $Dir

  $javaExe = Join-Path $Dir "bin\java.exe"
  if (Test-Path -LiteralPath $javaExe) {
    Write-Host "[bootstrap] JDK already present: $Dir"
    return $Dir
  }

  Write-Host "[bootstrap] Downloading Temurin JDK $Version ..."
  $pkg = Get-TemurinJdkAsset -Version $Version
  $zipName = $pkg.name
  $zipUrl = $pkg.link
  $zipPath = Join-Path $Dir $zipName

  Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath

  $extractRoot = Join-Path $Dir "_extract"
  if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
  Ensure-Dir $extractRoot

  Expand-Archive -LiteralPath $zipPath -DestinationPath $extractRoot -Force
  Remove-Item -Force $zipPath

  # The zip contains a single top-level directory like: jdk-25.0.2+10
  $top = Get-ChildItem -Directory $extractRoot | Select-Object -First 1
  if (-not $top) { throw "JDK zip extraction did not produce a directory" }

  # Move contents of the extracted JDK folder into $Dir
  Get-ChildItem -LiteralPath $top.FullName -Force | ForEach-Object {
    Move-Item -Force -LiteralPath $_.FullName -Destination $Dir
  }
  Remove-Item -Recurse -Force $extractRoot

  if (!(Test-Path -LiteralPath $javaExe)) {
    throw "JDK bootstrap failed; java.exe not found at $javaExe"
  }
  Write-Host "[bootstrap] JDK installed to: $Dir"
  return $Dir
}

function Ensure-Wix {
  param([string]$Dir)
  Ensure-Dir $Dir

  # jpackage supports WiX v3 (light.exe/candle.exe) or WiX v4/v5 (wix.exe).
  # We prefer WiX v3.11 binaries because it is stable and fully compatible.
  $candleExisting = Get-ChildItem -LiteralPath $Dir -Recurse -Filter "candle.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
  $lightExisting = Get-ChildItem -LiteralPath $Dir -Recurse -Filter "light.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($candleExisting -and $lightExisting) {
    Write-Host "[bootstrap] WiX already present: $($candleExisting.DirectoryName)"
    return $candleExisting.DirectoryName
  }

  Write-Host "[bootstrap] Downloading WiX 3.11 binaries (for jpackage) ..."
  $zipUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip"
  $zipPath = Join-Path $Dir "wix311-binaries.zip"
  Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath

  $dest = Join-Path $Dir "wix311"
  if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
  Ensure-Dir $dest
  Expand-Archive -LiteralPath $zipPath -DestinationPath $dest -Force
  Remove-Item -Force $zipPath

  $candle = Join-Path $dest "candle.exe"
  $light = Join-Path $dest "light.exe"
  if (!(Test-Path -LiteralPath $candle) -or !(Test-Path -LiteralPath $light)) {
    throw "WiX bootstrap failed; candle.exe/light.exe not found under $dest"
  }
  Write-Host "[bootstrap] WiX installed to: $dest"
  return $dest
}

function Write-ToolchainsXml {
  param([string]$JdkHome)
  $m2 = Join-Path $env:USERPROFILE ".m2"
  Ensure-Dir $m2
  $toolchainsPath = Join-Path $m2 "toolchains.xml"

  $xml = @"
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>$JdkVersion</version>
      <vendor>any</vendor>
    </provides>
    <configuration>
      <jdkHome>$JdkHome</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
"@
  Set-Content -LiteralPath $toolchainsPath -Value $xml -Encoding UTF8
  Write-Host "[bootstrap] Wrote Maven toolchains: $toolchainsPath"
}

$jdkHome = Ensure-Jdk -Version $JdkVersion -Dir $JdkDir
$wixBin = Ensure-Wix -Dir $WixDir

Write-ToolchainsXml -JdkHome $jdkHome

$env:JAVA_HOME = $jdkHome
$env:PATH = "$wixBin;$jdkHome\bin;" + $env:PATH

Write-Host "[bootstrap] JAVA_HOME=$env:JAVA_HOME"
Write-Host "[bootstrap] PATH includes WiX and JDK bin"

# Run Maven wrapper using our JAVA_HOME
Push-Location (Join-Path $PSScriptRoot "..")
try {
  # Allow passing multiple goals/phases like: "clean compile".
  $argsList = @()
  if ($MavenArgs) {
    $argsList = $MavenArgs -split '\s+'
  }
  & .\mvnw.cmd @argsList
} finally {
  Pop-Location
}
