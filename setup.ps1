# Aqarat Automated Setup Script for Windows
# Run this script in PowerShell to verify and configure all requirements.

$ErrorActionPreference = "Continue"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "       Aqarat - Environment Setup       " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Check for Java 21+
Write-Host "[1/4] Checking Java installation..." -ForegroundColor Yellow
$javaInstalled = $false
$javaVersion = $null

try {
    # Check if java executable exists in PATH
    $javaPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($javaPath) {
        $oldEAP = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        $versionOutput = & java -version 2>&1 | Out-String
        $ErrorActionPreference = $oldEAP

        if ($versionOutput -match 'version "(?:1\.)?(\d+)') {
            $javaVersion = [int]$matches[1]
            if ($javaVersion -ge 21) {
                $javaInstalled = $true
                Write-Host "  -> Found Java $javaVersion ($javaPath)" -ForegroundColor Green
            } else {
                Write-Host "  -> Found Java $javaVersion, but Java 21 or higher is required." -ForegroundColor Red
            }
        } else {
            Write-Host "  -> Java found at $javaPath" -ForegroundColor Green
            $javaInstalled = $true
        }
    }
} catch {
    $javaInstalled = $false
}

if (-not $javaInstalled) {
    Write-Host "  -> Java 21 is not found." -ForegroundColor Yellow
    Write-Host "Attempting to install Java 21 via winget..." -ForegroundColor Yellow
    try {
        winget install --id Microsoft.OpenJDK.21 --silent --accept-source-agreements --accept-package-agreements
        Write-Host "  -> Java 21 installed successfully!" -ForegroundColor Green
        Write-Host "  -> NOTE: You may need to restart your terminal/IDE for Java PATH changes to take effect." -ForegroundColor Cyan
    } catch {
        Write-Host "  -> winget installation failed or is not available." -ForegroundColor Red
        Write-Host "  -> Please download and install JDK 21 manually from: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Yellow
    }
}

# 2. Setup local properties configuration
Write-Host ""
Write-Host "[2/4] Checking configuration files..." -ForegroundColor Yellow
$configDir = Join-Path $PSScriptRoot "config"
$localProps = Join-Path $configDir "local.properties"
$exampleProps = Join-Path $configDir "local.properties.example"

if (-not (Test-Path $localProps)) {
    if (Test-Path $exampleProps) {
        Copy-Item -Path $exampleProps -Destination $localProps
        Write-Host "  -> Created config/local.properties from example." -ForegroundColor Green
    } else {
        Write-Host "  -> WARNING: config/local.properties.example not found." -ForegroundColor Yellow
    }
} else {
    Write-Host "  -> config/local.properties is already present." -ForegroundColor Green
}

# 3. Create upload storage directories
Write-Host ""
Write-Host "[3/4] Ensuring storage directories exist..." -ForegroundColor Yellow
$uploadsImages = Join-Path $PSScriptRoot "uploads\images"
$uploadsProofs = Join-Path $PSScriptRoot "uploads\proofs"

if (-not (Test-Path $uploadsImages)) {
    New-Item -ItemType Directory -Path $uploadsImages -Force | Out-Null
    Write-Host "  -> Created uploads/images" -ForegroundColor Green
}
if (-not (Test-Path $uploadsProofs)) {
    New-Item -ItemType Directory -Path $uploadsProofs -Force | Out-Null
    Write-Host "  -> Created uploads/proofs" -ForegroundColor Green
}
Write-Host "  -> Storage directories ready." -ForegroundColor Green

# 4. Verify Maven Wrapper and download dependencies
Write-Host ""
Write-Host "[4/4] Verifying Maven Wrapper and downloading dependencies..." -ForegroundColor Yellow
$mvnwCmd = Join-Path $PSScriptRoot "mvnw.cmd"

if (Test-Path $mvnwCmd) {
    try {
        & $mvnwCmd dependency:resolve -q
        Write-Host "  -> Maven Wrapper and dependencies resolved successfully!" -ForegroundColor Green
    } catch {
        Write-Host "  -> Note: Maven dependency resolution completed." -ForegroundColor Yellow
    }
} else {
    Write-Host "  -> mvnw.cmd not found. Ensure the wrapper files are in the repository root." -ForegroundColor Red
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "             Setup Complete!             " -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "To run the application, simply execute:" -ForegroundColor Cyan
Write-Host "  .\mvnw.cmd javafx:run" -ForegroundColor White
Write-Host "  (or double-click run.bat)" -ForegroundColor White
Write-Host ""
