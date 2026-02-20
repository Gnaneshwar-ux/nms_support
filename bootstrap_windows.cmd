@echo off
setlocal

REM Bootstrap JDK + WiX + Maven build for fresh clones on Windows.
REM This does not require Java to be pre-installed.

set "MAVEN_ARGS=%*"
if "%MAVEN_ARGS%"=="" set "MAVEN_ARGS=clean compile"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap_devtools_windows.ps1" -MavenArgs "%MAVEN_ARGS%"
exit /b %ERRORLEVEL%
