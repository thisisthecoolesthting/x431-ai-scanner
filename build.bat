@echo off
REM Double-click wrapper for build.ps1. Bypasses the script-execution policy for this run only.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
pause
