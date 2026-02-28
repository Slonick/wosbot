@echo off
set "PATH=C:\apache-maven-3.9.12\bin;%PATH%"
echo ==========================================
echo      WOS Bot Quick Recompile Script
echo ==========================================

REM Optional: Kill running bot instance if you want to ensure files are not locked
REM taskkill /F /IM java.exe
call mvn clean
if errorlevel 1 (
    echo [ERROR] Clean failed! Check if files are locked.
    pause
    exit /b %errorlevel%
)
call mvn package -DskipTests
if errorlevel 1 (
    echo [ERROR] Build failed!
    pause
    exit /b %errorlevel%
)
timeout /t 0 >nul
