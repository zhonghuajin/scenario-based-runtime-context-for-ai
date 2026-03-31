# Define variables (consistent with start-zkserver.ps1)
$baseDir = Join-Path $env:TEMP "programs"
$zkDirName = "apache-zookeeper-3.10.0-SNAPSHOT-bin"
$binDir = Join-Path $baseDir "$zkDirName\bin"

if (-not (Test-Path -Path $binDir)) {
    Write-Error "Error: Zookeeper bin directory not found at $binDir. Please run start-zkserver.ps1 first."
    exit 1
}

Write-Host "Entering Zookeeper bin directory..." -ForegroundColor Cyan
Set-Location -Path $binDir

Write-Host "Starting Zookeeper client and connecting to local server..." -ForegroundColor Cyan
# Start Zookeeper client and connect to local server
.\zkCli.cmd -server 127.0.0.1
