@echo off
setlocal
chcp 65001 >nul
set "APPDIR=%LOCALAPPDATA%\Programs\nip-traffic-system"
if not "%~1"=="" set "APPDIR=%~1"
if /I "%~x1"==".exe" set "APPDIR=%~dp1"
if "%~1"=="" if not exist "%APPDIR%\resources\app.asar" set "APPDIR=%ProgramFiles%\nip-traffic-system"
if not exist "%APPDIR%\resources\app.asar" (
  echo Installation not found. Drag the installed application EXE onto this CMD file.
  pause
  exit /b 1
)
set "APP_EXE="
for /f "delims=" %%F in ('dir /b /a:-d /o:-s "%APPDIR%\*.exe" 2^>nul') do if not defined APP_EXE set "APP_EXE=%APPDIR%\%%F"
if not defined APP_EXE exit /b 1
set "ELECTRON_RUN_AS_NODE=1"
set "NODE_PATH="
set "NODE_OPTIONS="
"%APP_EXE%" "%~dp0set-database-port.cjs" "%APPDIR%\resources\app.asar"
echo.
echo Result: port-change-report.json
pause
