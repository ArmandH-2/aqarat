@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo =========================================
echo              Aqarat Launcher             
echo =========================================
echo.

:: 1. Check if Java is available
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java is not detected on your system.
    echo Running setup to help install requirements...
    echo.
    call "%~dp0setup.bat"
    goto end
)

:: 2. Ensure config/local.properties exists
if not exist "config\local.properties" (
    if exist "config\local.properties.example" (
        echo [INFO] Creating config/local.properties from example...
        copy "config\local.properties.example" "config\local.properties" >nul
    )
)

:: 3. Ensure upload directories exist
if not exist "uploads\images" mkdir "uploads\images"
if not exist "uploads\proofs" mkdir "uploads\proofs"

:: 4. Run application using Maven Wrapper
echo [INFO] Starting Aqarat JavaFX Application...
echo.
call "%~dp0mvnw.cmd" javafx:run

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Application closed with an error.
    echo Please make sure your database server (SQL Server) is running and config/local.properties is configured properly.
    echo.
    pause
)

:end
