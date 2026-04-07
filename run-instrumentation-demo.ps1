<#
.SYNOPSIS
Executes the instrumentation build, instrumentation process, and testing flow.
.PARAMETER TargetFoldersFile
Specifies a file containing target folder paths (one per line).
Lines starting with '#' and empty lines are ignored.
Defaults to '.\target-folders.txt'.
.PARAMETER TargetFolders
Specifies one or more target folder paths for instrumentation (space-separated string or array).
If specified, takes precedence over TargetFoldersFile.
.PARAMETER SkipBuildAndTest
If specified, skips the second build and test execution steps (Steps 5 & 6).
.EXAMPLE
.\run-instrumentation-demo.ps1
.EXAMPLE
.\run-instrumentation-demo.ps1 -TargetFoldersFile ".\my-folders.txt"
.EXAMPLE
.\run-instrumentation-demo.ps1 -TargetFolders ".\demos\instrumentor-test\src\main\java\com\example\instrumentor\happens"
.EXAMPLE
.\run-instrumentation-demo.ps1 -TargetFolders @(".\path\to\dir1", ".\path\to\dir2")
.EXAMPLE
.\run-instrumentation-demo.ps1 -SkipBuildAndTest
#>
param(
    [Parameter(Mandatory=$false, HelpMessage="Specify a file containing target folder paths (one per line)")]
    [string]$TargetFoldersFile = ".\target-folders.txt",

    [Parameter(Mandatory=$false, HelpMessage="Specify one or more target folder paths for instrumentation")]
    [string[]]$TargetFolders,

    [Parameter(Mandatory=$false, HelpMessage="Skip the second build and test execution steps")]
    [switch]$SkipBuildAndTest
)

if (-not $TargetFolders) {
    if (-not (Test-Path $TargetFoldersFile)) {
        Write-Error "Error: Target folders file does not exist: $TargetFoldersFile"
        exit 1
    }
    $TargetFolders = Get-Content $TargetFoldersFile | Where-Object {
        $_.Trim() -ne '' -and -not $_.TrimStart().StartsWith('#')
    }
    if ($TargetFolders.Count -eq 0) {
        Write-Error "Error: No target folders found in file: $TargetFoldersFile"
        exit 1
    }
    Write-Host "Loaded $($TargetFolders.Count) target folder(s) from file: $TargetFoldersFile" -ForegroundColor Yellow
}

foreach ($folder in $TargetFolders) {
    if (-not (Test-Path $folder)) {
        Write-Error "Error: Target folder does not exist: $folder"
        exit 1
    }
}
Write-Host "Target folders: $($TargetFolders -join ', ')" -ForegroundColor Yellow

# 1. Restore source code
Write-Host "Restoring the instrumented source folders using Git..." -ForegroundColor Cyan
foreach ($folder in $TargetFolders) {
    git restore $folder
    git clean -fd $folder
}

# 2. Check Java environment variables
Write-Host "Checking Java environment variables..." -ForegroundColor Cyan
if (-not $env:JAVA_HOME) {
    Write-Error "Error: JAVA_HOME Environment variable not configured. Please set JAVA_HOME to point to your JDK installation directory."
    exit 1
}
Write-Host "Using JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 3. First build
Write-Host "Executing mvn clean package to build the instrumentor..." -ForegroundColor Cyan
mvn -f .\demos\pom.xml clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed"
    exit 1
}

# 4. Execute Instrumentor related Java commands
Write-Host "Executing code instrumentation (Instrumentor)..." -ForegroundColor Cyan

# 4.1 Main instrumentation
java -jar .\demos\instrumentor\target\instrumentor-1.0-SNAPSHOT.jar @TargetFolders
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Main instrumentation step returned non-zero exit code: $LASTEXITCODE"
}

# 4.2 Encoding mapping
java -jar .\demos\instrumentor-with-encoding\target\instrumentor-with-encoding-1.0-SNAPSHOT.jar @TargetFolders
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Encoding mapping step returned non-zero exit code: $LASTEXITCODE"
}

# 4.3 Activator
java -jar .\demos\instrumentor-activator\target\instrumentor-activator-1.0-SNAPSHOT.jar @TargetFolders
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Activator step returned non-zero exit code: $LASTEXITCODE"
}

# 5 & 6. Second build and tests
if (-not $SkipBuildAndTest) {
    Write-Host "Executing mvn clean package again..." -ForegroundColor Cyan
    mvn -f .\demos\pom.xml clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Second Maven build failed"
        exit 1
    }

    Write-Host "Executing SyncTest..." -ForegroundColor Cyan
    java -cp .\demos\instrumentor-test\target\instrumentor-test-1.0-SNAPSHOT.jar com.example.instrumentor.happens.before.SyncTest
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Test execution returned non-zero exit code: $LASTEXITCODE"
    }
} else {
    Write-Host "Skipping second build and test execution (SkipBuildAndTest flag is set)" -ForegroundColor Yellow
}

Write-Host "Instrumentation and testing phase completed. Please check the generated log file timestamp and use process-logs-demo.ps1 for subsequent processing." -ForegroundColor Green