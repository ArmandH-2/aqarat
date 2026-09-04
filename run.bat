@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo =================================================
echo                 Aqarat Launcher                 
echo =================================================
echo.

:: 1. Forceful Java 21 Discovery (bypasses any older Java 8/11 on system)
set "JDK21_FOUND=0"

:: 1.1 Probe standard Windows JDK 21 installation folders first
for /d %%D in (
    "C:\Program Files\Microsoft\jdk-21*"
    "C:\Program Files\Eclipse Adoptium\jdk-21*"
    "C:\Program Files\Java\jdk-21*"
    "C:\Program Files\BellSoft\LibericaJDK-21*"
    "C:\Program Files\Amazon Corretto\jdk21*"
) do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        set "PATH=%%D\bin;!PATH!"
        set "JDK21_FOUND=1"
        goto java_ready
    )
)

:: 1.2 Check JAVA_HOME if set
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        for /f "tokens=3" %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1 ^| findstr /i "version"') do (
            set "VER_RAW=%%~v"
            set "VER_MAJOR=!VER_RAW:~0,2!"
            if !VER_MAJOR! geq 21 (
                set "PATH=%JAVA_HOME%\bin;!PATH!"
                set "JDK21_FOUND=1"
                goto java_ready
            )
        )
    )
)

:: 1.3 Check default java in PATH
where java >nul 2>&1
if %ERRORLEVEL% equ 0 (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set "VER_RAW=%%~v"
        set "VER_MAJOR=!VER_RAW:~0,2!"
        if !VER_MAJOR! geq 21 (
            set "JDK21_FOUND=1"
            goto java_ready
        )
    )
)

:java_ready
:: 1.4 If Java 21 or config is completely missing, trigger automated setup
if %JDK21_FOUND% equ 0 (
    echo [WARNING] Java 21 is not detected or an older Java version was found.
    echo Launching automated setup to configure JDK 21 and requirements...
    echo.
    call "%~dp0setup.bat"
    goto end
)

if not exist "config\local.properties" (
    echo [INFO] config\local.properties missing. Running setup...
    echo.
    call "%~dp0setup.bat"
    goto end
)

:: 2. Ensure upload directories exist
if not exist "uploads\images" mkdir "uploads\images"
if not exist "uploads\proofs" mkdir "uploads\proofs"

:: 3. Launch application via Maven Wrapper
echo [INFO] Starting Aqarat Desktop Application...
echo.
call "%~dp0mvnw.cmd" javafx:run

if %ERRORLEVEL% neq 0 (
    echo.
    echo =================================================
    echo [ERROR] Application exited with an error.
    echo.
    echo If this is your first time running, please run setup.bat:
    echo   1. Double-click setup.bat (or run in PowerShell)
    echo   2. Ensure SQL Server is running
    echo =================================================
    echo.
    pause
)

:end

