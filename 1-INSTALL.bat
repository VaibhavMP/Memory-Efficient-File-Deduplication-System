@echo off
echo ========================================
echo STEP 1: INSTALLING JAVA
echo ========================================
echo.
echo Downloading Java 17...
powershell -Command "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile '%TEMP%\java17.zip'"
echo.
echo Extracting Java...
powershell -Command "Expand-Archive -Path '%TEMP%\java17.zip' -DestinationPath 'C:\java' -Force"
echo.
echo Setting up Java...
for /f "tokens=*" %%i in ('dir /b /ad "C:\java\jdk-17*"') do set "JAVA_HOME=C:\java\%%i"
for /f "tokens=*" %%i in ('dir /b /ad "C:\java\jdk-17*"') do set "PATH=C:\java\%%i\bin;%PATH%"
echo.
echo [OK] Java installed!
echo.
echo ========================================
echo STEP 2: INSTALLING MAVEN
echo ========================================
echo.
echo Downloading Maven...
powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '%TEMP%\maven.zip'"
echo.
echo Extracting Maven...
powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath 'C:\maven' -Force"
echo.
echo Setting up Maven...
set "MAVEN_HOME=C:\maven\apache-maven-3.9.6"
set "PATH=C:\maven\apache-maven-3.9.6\bin;%PATH%"
echo.
echo [OK] Maven installed!
echo.
echo ========================================
echo Installation complete!
echo Please CLOSE this window and open a NEW Command Prompt
echo Then type: setup.bat
echo ========================================
echo.
pause
