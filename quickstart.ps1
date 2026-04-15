<#
.SYNOPSIS
    Instrumentor Test Bug Fix Workflow Quickstart Script
.DESCRIPTION
    This script guides you through the full process of code instrumentation, compiling and running the instrumentor test, log denoising and analysis, and AI prompt generation.
#>

# Helper function to print a prominent pause prompt
function Pause-ForNextStep {
    param (
        [string]$CompletedStep,
        [string]$NextStep
    )
    Write-Host ""
    Write-Host "*****************************************************************" -ForegroundColor Yellow
    if (![string]::IsNullOrEmpty($CompletedStep)) {
        Write-Host "   $CompletedStep completed!" -ForegroundColor Green
    }
    Write-Host "   👉 Press [Enter] to continue to $NextStep ..." -ForegroundColor Yellow
    Write-Host "*****************************************************************" -ForegroundColor Yellow
    Read-Host
}

$workDir = $PWD.Path
$instrumentorTestPath = Join-Path $workDir "poc\instrumentor-test"

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "      Instrumentor Test Workflow Quickstart Script     " -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "Current working directory: $workDir"
Write-Host "Source and runtime path: $instrumentorTestPath"
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host ""

# ---------------------------------------------------------
# 🐛 BUG DEMONSTRATION & DISCLAIMER INFO
# ---------------------------------------------------------
Write-Host "-------------------------------------------------------" -ForegroundColor Yellow
Write-Host " 🐛 BUG DEMONSTRATION INFO & DISCLAIMER:" -ForegroundColor Yellow
Write-Host " This workflow uses the sample code located in: " -NoNewline -ForegroundColor Yellow
Write-Host "poc\instrumentor-test" -ForegroundColor Cyan
Write-Host ""
Write-Host " [The Pre-set Bug]" -ForegroundColor Yellow
Write-Host " There is an intentional concurrency bug in 'Test 8: Event-Driven Aggregation'." -ForegroundColor Yellow
Write-Host " Symptom: The final output array is [0, 0, 0] instead of the expected [500, 1000, 1500]." -ForegroundColor Yellow
Write-Host " Root Cause: A Happens-Before violation where the main thread reads the results" -ForegroundColor Yellow
Write-Host "             before the async EventBus finishes writing them." -ForegroundColor Yellow
Write-Host ""
Write-Host " [Disclaimer]" -ForegroundColor Yellow
Write-Host " Modern LLMs are incredibly powerful. Even without this project's methodology," -ForegroundColor Yellow
Write-Host " they can easily spot this specific bug just by reading the static source code." -ForegroundColor Yellow
Write-Host " Therefore, this Quickstart is NOT meant to prove the superiority of this tool" -ForegroundColor Yellow
Write-Host " on simple bugs. Instead, it serves as a sandbox to demonstrate the GENERAL WORKFLOW:" -ForegroundColor Yellow
Write-Host "   1. Code Instrumentation" -ForegroundColor Yellow
Write-Host "   2. Execution & Log Generation" -ForegroundColor Yellow
Write-Host "   3. Log Denoising" -ForegroundColor Yellow
Write-Host "   4. AI Prompt Generation" -ForegroundColor Yellow
Write-Host "-------------------------------------------------------" -ForegroundColor Yellow
Write-Host ""

# ---------------------------------------------------------
# Check current Java version (requires >= 17)
# ---------------------------------------------------------
$isValidJdk = $false
$currentVersion = "Unknown"

try {
    # java -version output is typically on stderr, capture with 2>&1
    $javaVersionOutput = java -version 2>&1
    foreach ($line in $javaVersionOutput) {
        # Match patterns like version "17.0.1" or version "1.8.0"
        if ($line -match 'version "(\d+)') {
            $majorVersion = [int]$matches[1]
            
            # Handle Java 8 and earlier (e.g., 1.8.x extracts 1)
            if ($majorVersion -eq 1) {
                if ($line -match 'version "1\.(\d+)') {
                    $currentVersion = "1.$([int]$matches[1])"
                }
            } else {
                $currentVersion = $majorVersion
            }

            if ($majorVersion -ge 17) {
                $isValidJdk = $true
            }
            break
        }
    }
} catch {
    Write-Host "Java command not detected in environment variables." -ForegroundColor Yellow
}

if ($isValidJdk) {
    Write-Host "[Environment Check] System Java version is $currentVersion, meets requirement (>= 17), skipping path configuration." -ForegroundColor Green
} else {
    if ($currentVersion -ne "Unknown") {
        Write-Host "[Environment Check] System Java version is $currentVersion, lower than required JDK 17." -ForegroundColor Yellow
    } else {
        Write-Host "[Environment Check] No valid Java environment detected." -ForegroundColor Yellow
    }
    
    Write-Host "Please ensure you have JDK 17 or higher installed." -ForegroundColor Yellow
    $jdkPath = Read-Host "Enter the installation path of JDK (>=17) (e.g., C:\Program Files\Java\jdk-17) [Press Enter to skip]"
    
    if (![string]::IsNullOrWhiteSpace($jdkPath)) {
        $env:JAVA_HOME = $jdkPath
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Write-Host "Temporarily added the specified JDK to environment variables." -ForegroundColor Green
    } else {
        Write-Host "No path entered. Will attempt to use current environment; this may cause compilation or runtime failures." -ForegroundColor Red
    }
}

Pause-ForNextStep -CompletedStep "[Environment Setup]" -NextStep "[Step 1] Code Instrumentation"

# ---------------------------------------------------------
# Step 1. Instrument the Code
# ---------------------------------------------------------
Write-Host "`n>>> [Step 1] Instrumenting target code..." -ForegroundColor Cyan
Set-Location $workDir
$targetFoldersFile = Join-Path $workDir "target-folders.txt"

# Automatically generate target-folders.txt containing the instrumentor-test path
Set-Content -Path $targetFoldersFile -Value $instrumentorTestPath
Write-Host "target-folders.txt configured with content: $instrumentorTestPath"

Write-Host "Executing: .\run-instrumentation-demo.ps1"
.\run-instrumentation-demo.ps1 -TargetFoldersFile $targetFoldersFile -SkipBuildAndTest

Pause-ForNextStep -CompletedStep "[Step 1] Code Instrumentation" -NextStep "[Step 2] Compile and Run Instrumentor Test"

# ---------------------------------------------------------
# Step 2. Compile and Run the Reproducer (Instrumentor Test)
# ---------------------------------------------------------
Write-Host "`n>>> [Step 2] Compiling and running instrumentor test..." -ForegroundColor Cyan
Set-Location $instrumentorTestPath

Write-Host "Executing: mvn clean package -DskipTests"
mvn clean package -DskipTests

Write-Host "Executing: java -jar target\instrumentor-test-1.0-SNAPSHOT.jar"
java -jar target\instrumentor-test-1.0-SNAPSHOT.jar

Write-Host "Program execution finished. Please verify that instrumentor-events-*.txt and instrumentor-log-*.txt have been generated in $instrumentorTestPath" -ForegroundColor Green

Pause-ForNextStep -CompletedStep "[Step 2] Compile and Run" -NextStep "[Step 3] Analyze Logs and Extract Denoised Data"

# ---------------------------------------------------------
# Step 3. Analyze Logs to Extract Denoised Data
# ---------------------------------------------------------
Write-Host "`n>>> [Step 3] Analyzing logs and extracting denoised data..." -ForegroundColor Cyan
Set-Location $workDir

# Automatically locate the latest log files
$logFile = Get-ChildItem -Path $instrumentorTestPath -Filter "instrumentor-log-*.txt" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$eventsFile = Get-ChildItem -Path $instrumentorTestPath -Filter "instrumentor-events-*.txt" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($logFile -and $eventsFile) {
    Write-Host "Found log file: $($logFile.FullName)"
    Write-Host "Found events file: $($eventsFile.FullName)"
    
    .\process-logs-demo.ps1 `
        -TargetFoldersFile ".\target-folders.txt" `
        -LogFile $logFile.FullName `
        -CommentMappingFile ".\comment-mapping.txt" `
        -EventsFile $eventsFile.FullName
} else {
    Write-Host "Could not find generated log or events file. Please check if Step 2 executed successfully and generated the logs." -ForegroundColor Red
}

Pause-ForNextStep -CompletedStep "[Step 3] Log Analysis" -NextStep "[Step 4] Generate AI Prompt"

# ---------------------------------------------------------
# Step 4. Generate the AI Prompt
# ---------------------------------------------------------
Write-Host "`n>>> [Step 4] Generating AI Prompt..." -ForegroundColor Cyan
$aiAppPath = Join-Path $workDir "poc\denoised-data-ai-app"
if (Test-Path $aiAppPath) {
    Set-Location $aiAppPath
    Write-Host "About to run Python script to generate the prompt. Please follow the console prompts for interactive input:" -ForegroundColor Green
    python generate_bug_localization_prompt.py
} else {
    Write-Host "AI Prompt generation script directory not found: $aiAppPath" -ForegroundColor Red
}

Write-Host "`n=======================================================" -ForegroundColor Magenta
Write-Host "  🎉 Workflow execution completed!" -ForegroundColor Green
Write-Host "  Please submit the generated AI_Bug_Localization_Prompt.md to the AI model for analysis." -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Magenta