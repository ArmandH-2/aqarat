@echo off
setlocal
cd /d "%~dp0"
echo Running Aqarat Setup...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
echo.
pause
