@echo off
setlocal
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0repair-installed.ps1" %*
echo.
echo Please keep repair-result.txt and installed-runtime-report.json.
pause
