@echo off
setlocal
cd /d "%~dp0"

echo =================================================
echo        Aqarat - 1-Click Environment Setup        
echo =================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"

echo.
pause

