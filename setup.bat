@echo off
setlocal enabledelayedexpansion
echo ========================================
echo STARTING APPLICATION
echo ========================================
echo.

REM Try to find Java in common locations
set JAVA_FOUND=
set JAVA_PATH=

REM Check PATH
java -version >nul 2>&1
if %errorlevel% equ 0 (
    set JAVA_FOUND=1
    echo [OK] Java found in PATH
    java -version 2>&1 | findstr /C:"version"
)

REM Check common installation paths
if not defined JAVA_FOUND (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17*bin\java.exe" (
        for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\Eclipse Adoptium\jdk-17*"') do (
            set "JAVA_PATH=C:\Program Files\Eclipse Adoptium\%%i\bin"
            set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%i"
        )
        if defined JAVA_PATH (
            set JAVA_FOUND=1
            set "PATH=%JAVA_PATH%;%PATH%"
            echo [OK] Java found at %JAVA_HOME%
        )
    )
)

if not defined JAVA_FOUND (
    if exist "C:\Program Files\Java\jdk*\bin\java.exe" (
        for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\Java\jdk*"') do (
            set "JAVA_PATH=C:\Program Files\Java\%%i\bin"
            set "JAVA_HOME=C:\Program Files\Java\%%i"
        )
        if defined JAVA_PATH (
            set JAVA_FOUND=1
            set "PATH=%JAVA_PATH%;%PATH%"
            echo [OK] Java found at %JAVA_HOME%
        )
    )
)

if not defined JAVA_FOUND (
    for /f "tokens=*" %%i in ('dir /b /ad "C:\java\jdk*" 2^>nul') do (
        if exist "C:\java\%%i\bin\java.exe" (
            set "JAVA_PATH=C:\java\%%i\bin"
            set "JAVA_HOME=C:\java\%%i"
            set JAVA_FOUND=1
        )
    )
    if defined JAVA_FOUND (
        set "PATH=!JAVA_PATH!;%PATH%"
        echo [OK] Java found at !JAVA_HOME!
    )
)

if not defined JAVA_FOUND (
    echo [ERROR] Java is not installed or not found!
    echo.
    echo Please install Java 17 from:
    echo https://adoptium.net/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)

echo.

REM Try to find Maven in common locations
set MAVEN_FOUND=
set MAVEN_PATH=

REM Check PATH
mvn -version >nul 2>&1
if %errorlevel% equ 0 (
    set MAVEN_FOUND=1
    echo [OK] Maven found in PATH
)

REM Check common installation paths
if not defined MAVEN_FOUND (
    if exist "C:\Program Files\apache-maven*\bin\mvn.cmd" (
        for /f "tokens=*" %%i in ('dir /b /ad "C:\Program Files\apache-maven*"') do (
            set "MAVEN_PATH=C:\Program Files\%%i\bin"
            set "MAVEN_HOME=C:\Program Files\%%i"
        )
        if defined MAVEN_PATH (
            set MAVEN_FOUND=1
            set "PATH=%MAVEN_PATH%;%PATH%"
            echo [OK] Maven found at %MAVEN_HOME%
        )
    )
)

if not defined MAVEN_FOUND (
    for /f "tokens=*" %%i in ('dir /b /ad "C:\maven\apache-maven*" 2^>nul') do (
        if exist "C:\maven\%%i\bin\mvn.cmd" (
            set "MAVEN_PATH=C:\maven\%%i\bin"
            set "MAVEN_HOME=C:\maven\%%i"
            set MAVEN_FOUND=1
        )
    )
    if defined MAVEN_FOUND (
        set "PATH=!MAVEN_PATH!;%PATH%"
        echo [OK] Maven found at !MAVEN_HOME!
    )
)

if not defined MAVEN_FOUND (
    echo [ERROR] Maven is not installed or not found!
    echo.
    echo Please install Maven from:
    echo https://maven.apache.org/download.cgi
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Starting the application...
echo ========================================
echo.

REM Go to project directory
cd /d "%~dp0"

REM Run Maven
call mvn spring-boot:run

pause
