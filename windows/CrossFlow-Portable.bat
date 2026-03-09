@echo off
REM CrossFlow Portable Launcher
REM This launcher runs the CrossFlow application from the bundled JAR

setlocal enabledelayedexpansion

REM Get the directory where this batch file is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Look for the CrossFlow JAR
set JAR_FILE=CrossFlow.jar
if not exist "%JAR_FILE%" (
    echo Error: %JAR_FILE% not found in %SCRIPT_DIR%
    echo Please ensure CrossFlow.jar is in the same directory as this script.
    timeout /t 5 /nobreak
    exit /b 1
)

REM Check if Java is available
where java >nul 2>&1
if errorlevel 1 (
    echo Error: Java not found on this system.
    echo Please ensure Java 11 or later is installed and available in PATH.
    echo Download from: https://www.oracle.com/java/technologies/downloads/
    timeout /t 10 /nobreak
    exit /b 1
)

REM Get Java version info for debugging
for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| find "version"') do set JAVA_VERSION=%%i
echo CrossFlow - Clipboard Sync for Windows
echo Using Java %JAVA_VERSION%
echo.

REM Launch CrossFlow with optimized JVM settings
REM -Xmx512m: Maximum heap size (512 MB should be plenty for this app)
REM -Dfile.encoding=UTF-8: Ensure UTF-8 encoding for text handling
java -Xmx512m -Dfile.encoding=UTF-8 -jar "%JAR_FILE%"

if errorlevel 1 (
    echo.
    echo CrossFlow encountered an error
    timeout /t 5 /nobreak
    exit /b 1
)

endlocal
exit /b 0
