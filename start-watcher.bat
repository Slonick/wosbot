@echo off
setlocal enabledelayedexpansion

:: -------------------------------------------------------
:: WOS Telegram Watcher launcher
:: Searches for wos-tg-watcher.jar starting from this
:: script's folder and walking up to the project root.
:: -------------------------------------------------------

set "JAR="
set "SEARCH_DIR=%~dp0"

for /l %%i in (1,1,5) do (
    if exist "!SEARCH_DIR!wos-tg-watcher.jar" (
        set "JAR=!SEARCH_DIR!wos-tg-watcher.jar"
        goto :found
    )
    if exist "!SEARCH_DIR!wos-tgwatcher\target\wos-tg-watcher.jar" (
        set "JAR=!SEARCH_DIR!wos-tgwatcher\target\wos-tg-watcher.jar"
        goto :found
    )
    for %%P in ("!SEARCH_DIR!..") do set "SEARCH_DIR=%%~fP\"
)

echo ERROR: wos-tg-watcher.jar not found.
echo Looked in and around: %~dp0
echo Build the project first: mvn clean package -DskipTests
pause
exit /b 1

:found
echo Starting WOS Telegram Watcher (background)...
echo JAR: %JAR%
echo Log: %USERPROFILE%\.wosbot\tg-watcher.log
start "WOS-TG-Watcher" /b javaw -jar "%JAR%"
exit /b 0
