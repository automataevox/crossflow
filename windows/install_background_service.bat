@echo off
REM This script installs CrossFlow as a background service using NSSM (Non-Sucking Service Manager)
REM Download NSSM from: https://nssm.cc/download
REM Extract nssm.exe to the same directory as this script

setlocal enabledelayedexpansion

REM Check if NSSM exists
if not exist "nssm.exe" (
    echo ERROR: nssm.exe not found in current directory
    echo Download it from: https://nssm.cc/download
    echo Extract and run this script from the same folder as nssm.exe
    pause
    exit /b 1
)

REM Find the CrossFlow installation directory
for /f "tokens=2*" %%A in ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\CrossFlow" /v InstallLocation 2^>nul') do (
    set "INSTALL_PATH=%%B"
)

if not defined INSTALL_PATH (
    echo CrossFlow installation not found
    echo Make sure CrossFlow is installed first
    pause
    exit /b 1
)

echo Found CrossFlow at: !INSTALL_PATH!

REM Install the service
echo Installing CrossFlow Background Sync Service...
nssm install CrossflowSync "!INSTALL_PATH!\CrossFlow.exe"

if errorlevel 1 (
    echo Failed to install service. Try running as Administrator.
    pause
    exit /b 1
)

REM Configure service to start on boot
nssm set CrossflowSync Start SERVICE_AUTO_START

REM Configure to restart if it crashes
nssm set CrossflowSync AppExit Default Restart

REM Start the service
echo Starting CrossFlow Background Sync Service...
nssm start CrossflowSync

echo OK - CrossFlow Background Sync Service installed and started
echo Service will run automatically on next boot
pause
