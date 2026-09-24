[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Endpoint,
    [Parameter(Mandatory = $true)]
    [ValidateSet('Install', 'Disable')][string]$Action
)
$ErrorActionPreference = 'Stop'

try {
    # Aceita IPv4 decimal explicito, evitando IDs ambiguos e argumentos extras.
    if ($Endpoint -notmatch '^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3}):(\d{1,5})$') {
        throw 'Use IPv4:porta, por exemplo 192.168.1.172:5555.'
    }
    $octets = 1..4 | ForEach-Object { [int]$Matches[$_] }
    $port = [int]$Matches[5]
    if (($octets | Where-Object { $_ -gt 255 }).Count -gt 0 -or $port -lt 1 -or $port -gt 65535) {
        throw 'Endereco IPv4 ou porta fora do intervalo valido.'
    }
    $serial = ($octets -join '.') + ':' + $port
    $manager = Join-Path $PSScriptRoot 'Manage-PipOverlay.ps1'
    if (-not (Test-Path -LiteralPath $manager -PathType Leaf)) {
        throw 'Manage-PipOverlay.ps1 ausente. Extraia a pasta inteira do kit.'
    }

    $adbPath = $null
    foreach ($candidate in @(
        (Join-Path $PSScriptRoot 'platform-tools\adb.exe'),
        (Join-Path $PSScriptRoot 'adb.exe')
    )) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $adbPath = $candidate
            break
        }
    }
    if (-not $adbPath) {
        $adbCommand = Get-Command adb.exe -CommandType Application -ErrorAction SilentlyContinue
        if ($adbCommand) { $adbPath = $adbCommand.Source }
    }
    if (-not $adbPath) {
        throw 'ADB nao encontrado. Adicione platform-tools ao PATH ou copie a pasta platform-tools para dentro do kit.'
    }

    Write-Host "Conectando ao ADB em $serial..."
    & $adbPath connect $serial
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao conectar pelo ADB.' }
    # adb connect pode retornar codigo zero mesmo quando a conexao falha.
    $state = & $adbPath -s $serial get-state
    if ($LASTEXITCODE -ne 0 -or ($state -join "`n").Trim() -ne 'device') {
        throw 'A central nao esta pronta no ADB. Confira IP, porta e autorizacao na central.'
    }

    $powershellExe = Join-Path $PSHOME 'powershell.exe'
    if ($Action -eq 'Install') {
        Write-Host 'Verificando compatibilidade...'
        & $powershellExe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $manager -Serial $serial -Action Check -Adb $adbPath
        if ($LASTEXITCODE -ne 0) { throw 'Check falhou. A instalacao nao foi iniciada.' }
        Write-Host 'Instalando e habilitando o PiP. A SystemUI pode desaparecer brevemente.'
    } else {
        # Reversao nao depende de passar no hash de compatibilidade da instalacao.
        Write-Host 'Desativando o overlay e reiniciando a SystemUI...'
    }
    & $powershellExe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $manager -Serial $serial -Action $Action -Adb $adbPath
    if ($LASTEXITCODE -ne 0) { throw "A operacao $Action falhou. Consulte a mensagem acima." }
    Write-Host 'Operacao concluida.'
    exit 0
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
