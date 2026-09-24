@echo off
setlocal
if "%~1"=="" goto usage
if not "%~2"=="" goto usage
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Connect-PipOverlay.ps1" -Endpoint "%~1" -Action Disable
exit /b %errorlevel%
:usage
echo Uso: disable-pip.bat ^<ip^>:^<porta^>
echo Exemplo: disable-pip.bat 192.168.1.172:5555
exit /b 2
