@echo off
REM This script registers CrossFlow to auto-start when user logs in
REM Run this after installing CrossFlow

setlocal enabledelayedexpansion

REM Get the installation path
for /f "tokens=2*" %%A in ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\CrossFlow" /v InstallLocation 2^>nul') do (
    set "INSTALL_PATH=%%B"
)

if not defined INSTALL_PATH (
    echo CrossFlow installation not found in registry
    pause
    exit /b 1
)

echo Found CrossFlow at: !INSTALL_PATH!

REM Add registry entry to auto-start on user login
reg add "HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run" /v CrossFlow /t REG_SZ /d "!INSTALL_PATH!\CrossFlow.exe" /f

if errorlevel 1 (
    echo Failed to add startup registry entry. Please run as Administrator.
    pause
    exit /b 1
)

echo OK - CrossFlow will now auto-start on login
pause
