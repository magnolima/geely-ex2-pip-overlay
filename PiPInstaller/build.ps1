[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PlatformKey,
    [Parameter(Mandatory = $true)][string]$PlatformCertificate,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$BuildToolsVersion = '35.0.0',
    [string]$CompileApi = '35'
)
$ErrorActionPreference = 'Stop'
function Invoke-Checked {
    param([string]$File, [string[]]$Arguments)
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Falha em $File (codigo $LASTEXITCODE)." }
}
if (-not $AndroidSdk -or -not $JavaHome) { throw 'Informe AndroidSdk e JavaHome (JDK 17 ou superior).' }
$sdkTools = Join-Path $AndroidSdk "build-tools\$BuildToolsVersion"
$androidJar = Join-Path $AndroidSdk "platforms\android-$CompileApi\android.jar"
$java = Join-Path $JavaHome 'bin\java.exe'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
$aapt = Join-Path $sdkTools 'aapt.exe'
$zipalign = Join-Path $sdkTools 'zipalign.exe'
$signer = Join-Path $sdkTools 'lib\apksigner.jar'
$d8 = Join-Path $sdkTools 'lib\d8.jar'
foreach ($required in @($PlatformKey, $PlatformCertificate, $androidJar, $java, $javac, $jar, $aapt, $zipalign, $signer, $d8)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Arquivo ausente: $required" }
}
$PlatformKey = (Resolve-Path -LiteralPath $PlatformKey).Path
$PlatformCertificate = (Resolve-Path -LiteralPath $PlatformCertificate).Path
$overlay = Join-Path $PSScriptRoot 'assets\pip-overlay-platform.apk'
if ((Get-FileHash -LiteralPath $overlay -Algorithm SHA256).Hash -ne '47F434857D7F2E175ECED4F591342667AA31B91A61636EEE0FFF19BAE68EF725') {
    throw 'Overlay incorporado diferente da versao validada.'
}
# Uma pasta por compilacao evita apagar arquivos e reutilizar classes antigas.
$buildDir = Join-Path $PSScriptRoot ('build\' + [guid]::NewGuid().ToString('N'))
$dist = Join-Path $PSScriptRoot 'dist'
$generated = Join-Path $buildDir 'generated'
$classes = Join-Path $buildDir 'classes'
$dex = Join-Path $buildDir 'dex'
foreach ($directory in @($buildDir, $dist, $generated, $classes, $dex)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$resources = Join-Path $buildDir 'resources.apk'
Invoke-Checked $aapt @('package', '-f', '-m', '-J', $generated, '-M', (Join-Path $PSScriptRoot 'AndroidManifest.xml'),
    '-S', (Join-Path $PSScriptRoot 'res'), '-A', (Join-Path $PSScriptRoot 'assets'), '-I', $androidJar, '-F', $resources)
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src'), $generated -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
Invoke-Checked $javac (@('--release', '8', '-encoding', 'UTF-8', '-classpath', $androidJar, '-d', $classes) + $sources)
$classesJar = Join-Path $buildDir 'classes.jar'
Invoke-Checked $jar @('cf', $classesJar, '-C', $classes, '.')
Invoke-Checked $java @('-Xmx512m', '-cp', $d8, 'com.android.tools.r8.D8', '--release', '--min-api', '28', '--lib', $androidJar, '--output', $dex, $classesJar)
Push-Location $dex
try { Invoke-Checked $aapt @('add', $resources, 'classes.dex') } finally { Pop-Location }
$aligned = Join-Path $buildDir 'aligned.apk'
Invoke-Checked $zipalign @('-f', '4', $resources, $aligned)
$signed = Join-Path $buildDir 'pip-control.apk'
Invoke-Checked $java @('-jar', $signer, 'sign', '--key', $PlatformKey, '--cert', $PlatformCertificate, '--out', $signed, $aligned)
Invoke-Checked $java @('-jar', $signer, 'verify', '--verbose', '--print-certs', $signed)
$certDetails = & $java -jar $signer verify --print-certs $signed
if ($LASTEXITCODE -ne 0 -or ($certDetails -join "`n") -notmatch 'SHA-256 digest: c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8') {
    throw 'Certificado diferente da plataforma da ROM validada. APK nao publicado em dist.'
}
$output = Join-Path $dist 'PiP-Control-1.0.0.apk'
Copy-Item -LiteralPath $signed -Destination $output
Get-FileHash -LiteralPath $output -Algorithm SHA256
Write-Output "APK pronto: $output"
