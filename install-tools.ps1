# Auto Install Java and Maven Script
# Run this as Administrator

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Installing Java 17 and Maven" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Install Java 17 using Chocolatey if available, otherwise download
Write-Host "[1/4] Checking for Java..." -ForegroundColor Yellow
$javaCheck = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCheck) {
    Write-Host "Java not found. Installing OpenJDK 17..." -ForegroundColor Yellow
    
    # Try winget first
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if ($winget) {
        Write-Host "Using winget to install Java..." -ForegroundColor Green
        winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-source-agreements --accept-package-agreements
    } else {
        Write-Host "Downloading Java 17..." -ForegroundColor Yellow
        $javaUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip"
        $javaZip = "$env:TEMP\jdk17.zip"
        Invoke-WebRequest -Uri $javaUrl -OutFile $javaZip
        
        Write-Host "Extracting Java..." -ForegroundColor Yellow
        Expand-Archive -Path $javaZip -DestinationPath "C:\Java" -Force
        
        # Set JAVA_HOME
        [Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Java\jdk-17.0.8+7", "Machine")
        $env:JAVA_HOME = "C:\Java\jdk-17.0.8+7"
        $env:PATH = "C:\Java\jdk-17.0.8+7\bin;$env:PATH"
        
        Remove-Item $javaZip -Force
    }
    Write-Host "[OK] Java installed!" -ForegroundColor Green
} else {
    Write-Host "[OK] Java is already installed!" -ForegroundColor Green
}

Write-Host ""
Write-Host "[2/4] Verifying Java..." -ForegroundColor Yellow
java -version

Write-Host ""
Write-Host "[3/4] Installing Maven..." -ForegroundColor Yellow
$mvnCheck = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCheck) {
    Write-Host "Maven not found. Installing Maven..." -ForegroundColor Yellow
    
    $mvnUrl = "https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
    $mvnZip = "$env:TEMP\maven.zip"
    
    Write-Host "Downloading Maven..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $mvnUrl -OutFile $mvnZip
    
    Write-Host "Extracting Maven..." -ForegroundColor Yellow
    Expand-Archive -Path $mvnZip -DestinationPath "C:\Maven" -Force
    
    # Set MAVEN_HOME
    [Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\Maven\apache-maven-3.9.6", "Machine")
    [Environment]::SetEnvironmentVariable("PATH", "C:\Maven\apache-maven-3.9.6\bin;$env:PATH", "Machine")
    
    Remove-Item $mvnZip -Force
    Write-Host "[OK] Maven installed!" -ForegroundColor Green
} else {
    Write-Host "[OK] Maven is already installed!" -ForegroundColor Green
}

Write-Host ""
Write-Host "[4/4] Verifying Maven..." -ForegroundColor Yellow
$mvnPath = "C:\Maven\apache-maven-3.9.6\bin\mvn.cmd"
if (Test-Path $mvnPath) {
    & $mvnPath -version
} else {
    mvn -version
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "Installation Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Please close this window and run setup.bat to start the application." -ForegroundColor Cyan
Write-Host ""
pause
