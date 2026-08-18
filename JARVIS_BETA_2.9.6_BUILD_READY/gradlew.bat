@echo off
setlocal

rem ============================================================
rem JARVIS BETA 2.9.6 - Gradle 8.9 Launcher
rem Windows / PowerShell compatible
rem ============================================================

set "ROOT=%~dp0"

rem Remove the trailing backslash from ROOT.
rem This prevents Windows command-line parsing from merging
rem Gradle arguments such as --version into the project path.
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "DIST_VERSION=8.9"

if defined GRADLE_USER_HOME (
    set "DIST_ROOT=%GRADLE_USER_HOME%"
) else (
    set "DIST_ROOT=%USERPROFILE%\.gradle"
)

set "DIST_DIR=%DIST_ROOT%\wrapper\dists\jarvis-gradle-%DIST_VERSION%"
set "GRADLE_HOME=%DIST_DIR%\gradle-%DIST_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"

rem ------------------------------------------------------------
rem Check whether Gradle is already installed system-wide.
rem ------------------------------------------------------------
where gradle >nul 2>&1

if %ERRORLEVEL% EQU 0 (
    call gradle --project-dir "%ROOT%" %*
    exit /b %ERRORLEVEL%
)

rem ------------------------------------------------------------
rem Check cached Gradle 8.9 installation.
rem ------------------------------------------------------------
if exist "%GRADLE_BIN%" goto RUN_GRADLE

rem ------------------------------------------------------------
rem Download Gradle 8.9.
rem ------------------------------------------------------------
set "URL=https://services.gradle.org/distributions/gradle-%DIST_VERSION%-bin.zip"
set "ZIP=%DIST_DIR%\gradle-%DIST_VERSION%-bin.zip"

if not exist "%DIST_DIR%" (
    mkdir "%DIST_DIR%"
)

if errorlevel 1 (
    echo ERROR: Could not create Gradle distribution directory.
    exit /b 1
)

echo.
echo Gradle %DIST_VERSION% not installed; downloading...
echo URL: %URL%
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
    "Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ZIP%'"

if errorlevel 1 (
    echo.
    echo ERROR: Failed to download Gradle %DIST_VERSION%.
    echo Check your internet connection and try again.
    exit /b 1
)

rem ------------------------------------------------------------
rem Extract Gradle.
rem ------------------------------------------------------------
echo.
echo Extracting Gradle %DIST_VERSION%...
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
    "Expand-Archive -Force '%ZIP%' '%DIST_DIR%'"

if errorlevel 1 (
    echo.
    echo ERROR: Failed to extract Gradle %DIST_VERSION%.
    exit /b 1
)

del /q "%ZIP%" >nul 2>&1

if not exist "%GRADLE_BIN%" (
    echo.
    echo ERROR: Gradle executable was not found after extraction:
    echo %GRADLE_BIN%
    exit /b 1
)

rem ------------------------------------------------------------
rem Run Gradle.
rem ------------------------------------------------------------
:RUN_GRADLE

echo.
echo Using Gradle %DIST_VERSION%...
echo Project: "%ROOT%"
echo.

call "%GRADLE_BIN%" --project-dir "%ROOT%" %*

set "EXIT_CODE=%ERRORLEVEL%"

exit /b %EXIT_CODE%