# Set to stop execution on error
$ErrorActionPreference = "Stop"

# Ensure the ZK_HOME environment variable is set
if (-not $env:ZK_HOME) {
    Write-Error "Error: ZK_HOME environment variable is not set. Please set it to your ZooKeeper source root directory."
    exit 1
}

# Define variables (use system temp directory as base directory, similar to Linux's /tmp)
$baseDir = Join-Path $env:TEMP "programs"
$zkDirName = "apache-zookeeper-3.10.0-SNAPSHOT-bin"
$zkDirPath = Join-Path $baseDir $zkDirName
$tarballPath = Join-Path $env:ZK_HOME "zookeeper-assembly\target\apache-zookeeper-3.10.0-SNAPSHOT-bin.tar.gz"

Write-Host "1. Create and switch to working directory: $baseDir" -ForegroundColor Green
if (-not (Test-Path -Path $baseDir)) {
    New-Item -ItemType Directory -Path $baseDir | Out-Null
}
Set-Location -Path $baseDir

Write-Host "2. Check and clean up old version directory..." -ForegroundColor Green
if (Test-Path -Path $zkDirPath) {
    Remove-Item -Path $zkDirPath -Recurse -Force
    Write-Host "   Old directory deleted."
}

Write-Host "3. Extracting latest Zookeeper package..." -ForegroundColor Green
if (-not (Test-Path -Path $tarballPath)) {
    Write-Error "Error: Tarball not found at $tarballPath. Did you run 'mvn clean install'?"
    exit 1
}
tar -xzf $tarballPath

Write-Host "4. Copying zoo.cfg configuration file..." -ForegroundColor Green
$confDir = Join-Path $zkDirPath "conf"
$sourceCfg = Join-Path $env:ZK_HOME "conf\zoo.cfg"

if (Test-Path -Path $sourceCfg) {
    Copy-Item -Path $sourceCfg -Destination $confDir -Force
} else {
    Write-Host "Warning: zoo.cfg not found in $sourceCfg. Please ensure it is created." -ForegroundColor Yellow
}

Write-Host "5. Preparing to start Zookeeper..." -ForegroundColor Green
$binDir = Join-Path $zkDirPath "bin"
Set-Location -Path $binDir

# Start Zookeeper service
.\zkServer.cmd
