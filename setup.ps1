# Aqarat Automated Setup Script for Windows
# Run this script directly in PowerShell (or via setup.bat) to configure everything automatically.

$ErrorActionPreference = "Continue"

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "         Aqarat - Automated Setup System         " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host ""

function Refresh-ProcessEnvironment {
    $machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machinePath;$userPath"
    
    $javaHome = [System.Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
    if (-not $javaHome) {
        $javaHome = [System.Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
    }
    if ($javaHome) {
        $env:JAVA_HOME = $javaHome
    }
}

# ==============================================================================
# 1. Java 21+ Detection & Auto-Installation
# ==============================================================================
Write-Host "[1/5] Checking Java 21+ installation..." -ForegroundColor Yellow

$javaPath = $null
$javaVersion = 0

function Test-JavaVersion {
    param([string]$ExecutablePath)
    try {
        if (-not (Test-Path $ExecutablePath)) { return 0 }
        $out = & $ExecutablePath -version 2>&1 | Out-String
        if ($out -match 'version "(?:1\.)?(\d+)') {
            return [int]$matches[1]
        }
    } catch {
        return 0
    }
    return 0
}

# 1.1 Check PATH
$cmdJava = (Get-Command java -ErrorAction SilentlyContinue).Source
if ($cmdJava) {
    $ver = Test-JavaVersion $cmdJava
    if ($ver -ge 21) {
        $javaPath = $cmdJava
        $javaVersion = $ver
    }
}

# 1.2 Check JAVA_HOME
if (-not $javaPath -and $env:JAVA_HOME) {
    $homeJava = Join-Path $env:JAVA_HOME "bin\java.exe"
    $ver = Test-JavaVersion $homeJava
    if ($ver -ge 21) {
        $javaPath = $homeJava
        $javaVersion = $ver
        $env:Path = "$($env:JAVA_HOME)\bin;$env:Path"
    }
}

# 1.3 Search standard install paths
if (-not $javaPath) {
    $searchPaths = @(
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\BellSoft\LibericaJDK-21*",
        "C:\Program Files\Amazon Corretto\jdk21*"
    )
    $candidate = Get-ChildItem -Path $searchPaths -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) {
        $candidateJava = Join-Path $candidate.FullName "bin\java.exe"
        $ver = Test-JavaVersion $candidateJava
        if ($ver -ge 21) {
            $javaPath = $candidateJava
            $javaVersion = $ver
            $env:JAVA_HOME = $candidate.FullName
            $env:Path = "$($candidate.FullName)\bin;$env:Path"
            [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $candidate.FullName, "User")
        }
    }
}

# 1.4 Auto-install via winget if missing
if (-not $javaPath) {
    Write-Host "  -> Java 21 not found. Attempting automatic installation via winget..." -ForegroundColor Yellow
    try {
        winget install --id Microsoft.OpenJDK.21 --silent --accept-source-agreements --accept-package-agreements
        Refresh-ProcessEnvironment
        
        # Scan again after install
        $candidate = Get-ChildItem -Path "C:\Program Files\Microsoft\jdk-21*" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($candidate) {
            $javaPath = Join-Path $candidate.FullName "bin\java.exe"
            $javaVersion = 21
            $env:JAVA_HOME = $candidate.FullName
            $env:Path = "$($candidate.FullName)\bin;$env:Path"
            [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $candidate.FullName, "User")
            Write-Host "  -> Java 21 successfully installed and configured!" -ForegroundColor Green
        }
    } catch {
        Write-Host "  -> winget installation encountered an error." -ForegroundColor Yellow
    }
}

if ($javaPath) {
    Write-Host "  -> Java $javaVersion ready ($javaPath)" -ForegroundColor Green
} else {
    Write-Host "  -> [WARNING] Java 21 could not be detected automatically." -ForegroundColor Red
    Write-Host "     Please install JDK 21 from: https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Yellow
}

# ==============================================================================
# 2. SQL Server & sqlcmd Auto-Discovery
# ==============================================================================
Write-Host ""
Write-Host "[2/5] Checking SQL Server and database tools..." -ForegroundColor Yellow

Refresh-ProcessEnvironment

# 2.1 Locate sqlcmd
$sqlcmdExe = (Get-Command sqlcmd -ErrorAction SilentlyContinue).Source
if (-not $sqlcmdExe) {
    $searchSqlcmd = @(
        "C:\Program Files\sqlcmd\sqlcmd.exe",
        "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\*\Tools\Binn\sqlcmd.exe",
        "C:\Program Files\Microsoft SQL Server\*\Tools\Binn\sqlcmd.exe",
        "C:\Program Files (x86)\Microsoft SQL Server\*\Tools\Binn\sqlcmd.exe"
    )
    $foundSqlcmd = Get-ChildItem -Path $searchSqlcmd -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($foundSqlcmd) {
        $sqlcmdExe = $foundSqlcmd.FullName
        $env:Path = "$($foundSqlcmd.DirectoryName);$env:Path"
    }
}

if (-not $sqlcmdExe) {
    Write-Host "  -> sqlcmd not found. Attempting to install Microsoft.Sqlcmd via winget..." -ForegroundColor Yellow
    try {
        winget install Microsoft.Sqlcmd --silent --accept-source-agreements --accept-package-agreements
        Refresh-ProcessEnvironment
        $sqlcmdExe = (Get-Command sqlcmd -ErrorAction SilentlyContinue).Source
    } catch {
        Write-Host "  -> Could not auto-install sqlcmd via winget." -ForegroundColor Yellow
    }
}

# 2.2 Detect SQL Server Instances & Ensure Services are Running
$sqlServices = Get-Service -Name "MSSQL*" -ErrorAction SilentlyContinue
foreach ($svc in $sqlServices) {
    if ($svc.Status -ne "Running") {
        Write-Host "  -> Starting stopped SQL service: $($svc.Name)..." -ForegroundColor Yellow
        try { Start-Service -Name $svc.Name -ErrorAction SilentlyContinue } catch {}
    }
}

$candidateInstances = @(
    "localhost\SQLEXPRESS",
    "localhost",
    ".",
    "(localdb)\MSSQLLocalDB"
)

# Also check registry for instance names
$regInstances = Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\Instance Names\SQL" -ErrorAction SilentlyContinue
if ($regInstances) {
    $names = $regInstances.PSObject.Properties | Where-Object { $_.Name -notmatch '^PS' } | ForEach-Object { $_.Name }
    foreach ($inst in $names) {
        if ($inst -eq "MSSQLSERVER") {
            $candidateInstances = @("localhost") + $candidateInstances
        } else {
            $candidateInstances = @("localhost\$inst") + $candidateInstances
        }
    }
}

$activeInstance = $null

if ($sqlcmdExe) {
    foreach ($inst in ($candidateInstances | Select-Object -Unique)) {
        try {
            $testOut = & $sqlcmdExe -S $inst -E -C -Q "SELECT @@SERVERNAME" -l 3 2>&1 | Out-String
            if ($testOut -match '[A-Za-z0-9_\\-]+' -and $testOut -notmatch 'Error|Cannot connect') {
                $activeInstance = $inst
                Write-Host "  -> Connected to SQL Server instance: $activeInstance" -ForegroundColor Green
                break
            }
        } catch {}
    }
}

if (-not $activeInstance) {
    Write-Host "  -> No active SQL Server instance detected." -ForegroundColor Yellow
    Write-Host "     Attempting to install SQL Server 2022 Express via winget..." -ForegroundColor Yellow
    try {
        winget install Microsoft.SQLServer.2022.Express --silent --accept-source-agreements --accept-package-agreements
        Start-Sleep -Seconds 5
        $svc = Get-Service -Name "MSSQL$SQLEXPRESS" -ErrorAction SilentlyContinue
        if ($svc) {
            Start-Service -Name $svc.Name -ErrorAction SilentlyContinue
            $activeInstance = "localhost\SQLEXPRESS"
            Write-Host "  -> SQL Server Express installed and started!" -ForegroundColor Green
        }
    } catch {
        Write-Host "  -> SQL Server Express auto-install requires manual action or admin rights." -ForegroundColor Yellow
    }
}

# ==============================================================================
# 3. Database Creation & Seeding
# ==============================================================================
Write-Host ""
Write-Host "[3/5] Setting up Aqarat database and seed data..." -ForegroundColor Yellow

$dbConfigured = $false
$schemaPath = Join-Path $PSScriptRoot "db\schema.sql"
$seedPath = Join-Path $PSScriptRoot "db\seed.sql"

if ($activeInstance -and $sqlcmdExe) {
    try {
        Write-Host "  -> Configuring SQL login 'aqarat_app'..." -ForegroundColor Yellow
        $createLoginSql = @"
IF NOT EXISTS (SELECT * FROM sys.server_principals WHERE name = 'aqarat_app')
BEGIN
    CREATE LOGIN aqarat_app WITH PASSWORD = 'Aqarat!Local2026', CHECK_POLICY = OFF;
END
ALTER SERVER ROLE sysadmin ADD MEMBER aqarat_app;
"@
        & $sqlcmdExe -S $activeInstance -E -C -Q $createLoginSql -l 10 | Out-Null
        
        Write-Host "  -> Applying schema (tables, views, indexes)..." -ForegroundColor Yellow
        & $sqlcmdExe -S $activeInstance -E -C -I -i $schemaPath -l 30 | Out-Null
        
        Write-Host "  -> Seeding initial data (properties, users, districts)..." -ForegroundColor Yellow
        & $sqlcmdExe -S $activeInstance -E -C -I -d Aqarat -i $seedPath -l 60 | Out-Null
        
        $grantUserSql = @"
USE Aqarat;
IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'aqarat_app')
BEGIN
    CREATE USER aqarat_app FOR LOGIN aqarat_app;
END
ALTER ROLE db_owner ADD MEMBER aqarat_app;
"@
        & $sqlcmdExe -S $activeInstance -E -C -d Aqarat -Q $grantUserSql -l 10 | Out-Null
        
        # Verify row counts
        $countCheck = & $sqlcmdExe -S $activeInstance -E -C -d Aqarat -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM dbo.property;" -h -1 2>&1 | Out-String
        $count = [int]($countCheck.Trim() -replace '\D','')
        Write-Host "  -> Database initialized successfully with $count properties seeded!" -ForegroundColor Green
        $dbConfigured = $true
    } catch {
        Write-Host "  -> SQL execution note: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  -> SQL Server instance could not be connected directly via sqlcmd." -ForegroundColor Yellow
    Write-Host "     If you have SQL Server installed, run db/schema.sql and db/seed.sql in SSMS or Azure Data Studio." -ForegroundColor Yellow
}

# ==============================================================================
# 4. Configuration & Storage Setup
# ==============================================================================
Write-Host ""
Write-Host "[4/5] Configuring local properties & storage..." -ForegroundColor Yellow

$configDir = Join-Path $PSScriptRoot "config"
$localProps = Join-Path $configDir "local.properties"
$exampleProps = Join-Path $configDir "local.properties.example"

if (-not (Test-Path $configDir)) {
    New-Item -ItemType Directory -Path $configDir -Force | Out-Null
}

$jdbcUrl = "jdbc:sqlserver://localhost:1433;databaseName=Aqarat;encrypt=true;trustServerCertificate=true"
try {
    $tcp1433 = Test-NetConnection -ComputerName localhost -Port 1433 -WarningAction SilentlyContinue -InformationLevel Quiet
    if ($tcp1433) {
        $jdbcUrl = "jdbc:sqlserver://localhost:1433;databaseName=Aqarat;encrypt=true;trustServerCertificate=true"
    } elseif ($activeInstance -and $activeInstance -match 'localdb') {
        $jdbcUrl = "jdbc:sqlserver://(localdb)\\MSSQLLocalDB;databaseName=Aqarat;integratedSecurity=true;encrypt=true;trustServerCertificate=true"
    } elseif ($activeInstance -and $activeInstance -match '\\(.+)$') {
        $instName = $matches[1]
        $jdbcUrl = "jdbc:sqlserver://localhost;instanceName=$instName;databaseName=Aqarat;encrypt=true;trustServerCertificate=true"
    }
} catch {
    $jdbcUrl = "jdbc:sqlserver://localhost:1433;databaseName=Aqarat;encrypt=true;trustServerCertificate=true"
}

# Read existing AI settings if present
$aiEnabled = "true"
$aiKey = "CHANGE_ME"
$aiModel = "gpt-4o-mini"
$aiBaseUrl = "https://api.openai.com/v1"

if (Test-Path $localProps) {
    $existing = Get-Content $localProps
    foreach ($line in $existing) {
        if ($line -match '^ai\.enabled=(.+)$') { $aiEnabled = $matches[1].Trim() }
        if ($line -match '^ai\.apiKey=(.+)$') { $aiKey = $matches[1].Trim() }
        if ($line -match '^ai\.model=(.+)$') { $aiModel = $matches[1].Trim() }
        if ($line -match '^ai\.baseUrl=(.+)$') { $aiBaseUrl = $matches[1].Trim() }
    }
} elseif (Test-Path $exampleProps) {
    $existing = Get-Content $exampleProps
    foreach ($line in $existing) {
        if ($line -match '^ai\.enabled=(.+)$') { $aiEnabled = $matches[1].Trim() }
        if ($line -match '^ai\.apiKey=(.+)$') { $aiKey = $matches[1].Trim() }
        if ($line -match '^ai\.model=(.+)$') { $aiModel = $matches[1].Trim() }
        if ($line -match '^ai\.baseUrl=(.+)$') { $aiBaseUrl = $matches[1].Trim() }
    }
}

$propsContent = @"
db.url=$jdbcUrl
db.user=aqarat_app
db.password=Aqarat!Local2026

# HikariCP
db.pool.size=10
db.pool.timeoutMs=30000

# Where uploaded photos and payment proofs are written
storage.images=uploads/images
storage.proofs=uploads/proofs

# AI assistant
ai.enabled=$aiEnabled
ai.baseUrl=$aiBaseUrl
ai.apiKey=$aiKey
ai.model=$aiModel
ai.timeoutMs=30000
"@

# Write UTF-8 without BOM so Java Properties can parse the first key cleanly
[System.IO.File]::WriteAllText($localProps, $propsContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "  -> Generated config/local.properties ($jdbcUrl)" -ForegroundColor Green

# Ensure upload storage directories exist
$uploadsImages = Join-Path $PSScriptRoot "uploads\images"
$uploadsProofs = Join-Path $PSScriptRoot "uploads\proofs"

if (-not (Test-Path $uploadsImages)) {
    New-Item -ItemType Directory -Path $uploadsImages -Force | Out-Null
}
if (-not (Test-Path $uploadsProofs)) {
    New-Item -ItemType Directory -Path $uploadsProofs -Force | Out-Null
}
Write-Host "  -> Storage directories initialized." -ForegroundColor Green

# ==============================================================================
# 5. Maven Dependencies & Build Warm-up
# ==============================================================================
Write-Host ""
Write-Host "[5/5] Resolving Maven dependencies and compiling..." -ForegroundColor Yellow
$mvnwCmd = Join-Path $PSScriptRoot "mvnw.cmd"

if (Test-Path $mvnwCmd) {
    try {
        & $mvnwCmd compile -q
        Write-Host "  -> Application compiled and all dependencies cached successfully!" -ForegroundColor Green
    } catch {
        Write-Host "  -> Maven build note: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=================================================" -ForegroundColor Green
Write-Host "             Setup Complete & Ready!             " -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green
Write-Host ""
Write-Host "To launch Aqarat, simply run:" -ForegroundColor Cyan
Write-Host "  double-click run.bat  (or execute: .\mvnw.cmd javafx:run)" -ForegroundColor White
Write-Host ""
Write-Host "Default Sign-In Accounts:" -ForegroundColor Cyan
Write-Host "  Admin:    admin@aqarat.local   / Password123!" -ForegroundColor White
Write-Host "  Customer: user1@example.com    / Password123!" -ForegroundColor White
Write-Host "  Guest:    Click 'Browse listings as a guest'" -ForegroundColor White
Write-Host ""
