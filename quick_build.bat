@echo off
set "PATH=C:\apache-maven-3.9.12\bin;%PATH%"
echo ==========================================
echo      WOS Bot Quick Recompile Script
echo ==========================================

echo Stopping any running Java and ADB processes...
taskkill /F /IM java.exe >nul 2>&1
taskkill /F /IM javaw.exe >nul 2>&1
taskkill /F /IM adb.exe >nul 2>&1
timeout /t 2 >nul

call mvn clean
if errorlevel 1 (
    echo [ERROR] Clean failed! Check if files are locked.
    pause
    exit /b %errorlevel%
)
call mvn clean install package
if errorlevel 1 (
    echo [ERROR] Build failed!
    pause
    exit /b %errorlevel%
)
timeout /t 0 >nul
