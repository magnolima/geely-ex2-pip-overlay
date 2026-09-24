[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateSet('Check', 'Install', 'Disable')][string]$Action = 'Check',
    [string]$Adb = 'adb'
)
$ErrorActionPreference = 'Stop'
$package = 'com.geely.ex2.mica.pipoverlayprobe'
$expectedSystemUi = '0C9AB49B1CCF83133B3CBCAB1CC1D980E97C6472FE9D06F65E2BEF07D0737332'
$expectedOverlay = '47F434857D7F2E175ECED4F591342667AA31B91A61636EEE0FFF19BAE68EF725'

function Invoke-Adb {
    param([string[]]$Arguments)
    $result = & $Adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n").Trim()
}
function Restart-SystemUi {
    $processIds = Invoke-Adb @('shell', 'pidof', 'com.android.systemui')
    if ($processIds -notmatch '^\d+( \d+)*$') { throw 'PID da SystemUI invalido.' }
    Invoke-Adb @('shell', 'kill', $processIds) | Out-Null
}
function Get-OverlayList { Invoke-Adb @('shell', 'cmd', 'overlay', 'list', '--user', '0') }

if ((Invoke-Adb @('get-state')) -ne 'device') { throw 'Dispositivo indisponivel.' }
if ((Invoke-Adb @('shell', 'id', '-u')) -ne '0') {
    throw 'Este kit requer ADB shell com root (uid 0), como na central validada.'
}

if ($Action -eq 'Disable') {
    Invoke-Adb @('shell', 'cmd', 'overlay', 'disable', '--user', '0', $package) | Out-Null
    if ((Get-OverlayList) -match ('\[x\]\s+' + [regex]::Escape($package))) {
        throw 'O overlay continua habilitado; reinicializacao cancelada.'
    }
    Restart-SystemUi
    Write-Output 'Overlay desativado. APK mantido instalado; a SystemUI foi reiniciada.'
    exit 0
}

if ((Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')) -ne '28') {
    throw 'Kit validado somente no Android 9/API 28.'
}
$paths = Invoke-Adb @('shell', 'pm', 'path', 'com.android.systemui')
if ($paths -notmatch '^package:(/[^\r\n ]+\.apk)$') { throw 'APK unico da SystemUI nao identificado.' }
$systemUiPath = $Matches[1]
$hashOutput = Invoke-Adb @('shell', 'sha256sum', $systemUiPath)
if (($hashOutput -split '\s+')[0] -ne $expectedSystemUi) {
    throw 'SystemUI diferente da validada. Nao aplique este array sem analisar a ROM.'
}
$features = Invoke-Adb @('shell', 'pm', 'list', 'features')
if ($features -notmatch 'feature:android.software.picture_in_picture') {
    throw 'ROM nao declara suporte a PiP.'
}
$apk = Join-Path $PSScriptRoot 'pip-overlay-platform.apk'
if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash -ne $expectedOverlay) {
    throw 'APK do overlay diferente do validado.'
}
Write-Output 'Pre-verificacao OK: root, API 28, PiP e hashes dos APKs.'
Write-Output (Get-OverlayList)
if ($Action -eq 'Check') { exit 0 }
if ((Get-OverlayList) -match ('\[x\]\s+' + [regex]::Escape($package))) {
    Write-Output 'Overlay ja habilitado. Nenhuma alteracao feita.'
    exit 0
}

$installResult = Invoke-Adb @('install', '-r', '--user', '0', $apk)
if ($installResult -notmatch '(?m)^Success\s*$') { throw $installResult }
# Um diretorio novo impede reutilizar o marcador de uma tentativa anterior.
$remoteDir = '/data/local/tmp/mica-pip-install-' + [guid]::NewGuid().ToString('N')
Invoke-Adb @('shell', 'mkdir', '-p', $remoteDir) | Out-Null
Invoke-Adb @('push', (Join-Path $PSScriptRoot 'rollback.sh'), "$remoteDir/rollback.sh") | Out-Null
Invoke-Adb @('push', (Join-Path $PSScriptRoot 'guard.sh'), "$remoteDir/guard.sh") | Out-Null
$guardId = Invoke-Adb @('shell', "nohup sh $remoteDir/guard.sh $remoteDir >$remoteDir/guard.log 2>&1 </dev/null & echo `$!")
if ($guardId -notmatch '^\d+$') { throw 'Nao foi possivel iniciar a reversao automatica.' }
Invoke-Adb @('shell', 'kill', '-0', $guardId) | Out-Null
try {
    Invoke-Adb @('shell', 'cmd', 'overlay', 'enable', '--user', '0', $package) | Out-Null
    if ((Get-OverlayList) -notmatch ('\[x\]\s+' + [regex]::Escape($package))) {
        throw 'Overlay nao habilitado.'
    }
    Restart-SystemUi
    Start-Sleep -Seconds 3
    $firstPid = Invoke-Adb @('shell', 'pidof', 'com.android.systemui')
    $dump = Invoke-Adb @('shell', 'dumpsys', 'activity', 'service', 'com.android.systemui/.SystemUIService')
    if ($dump -notmatch 'dumping service: com.android.systemui.pip.PipUI' -or $dump -notmatch 'PipManager') {
        throw 'Controlador de PiP nao inicializou.'
    }
    Start-Sleep -Seconds 2
    if ((Invoke-Adb @('shell', 'pidof', 'com.android.systemui')) -ne $firstPid) {
        throw 'SystemUI reiniciou durante a verificacao.'
    }
    Invoke-Adb @('shell', 'touch', "$remoteDir/keep-enabled") | Out-Null
    Write-Output 'Overlay habilitado e PipUI carregado. Valide toque, arraste, tela cheia e fechamento.'
    Write-Output "Reversao: .\Manage-PipOverlay.ps1 -Serial $Serial -Action Disable"
} catch {
    Write-Warning 'Falha na ativacao. Tentando reverter; o guard tambem reverte apos 90 segundos.'
    Invoke-Adb @('shell', 'sh', "$remoteDir/rollback.sh") | Out-Null
    Invoke-Adb @('shell', 'touch', "$remoteDir/keep-enabled") | Out-Null
    throw
}
