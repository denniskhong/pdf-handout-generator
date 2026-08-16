@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo Error: Java 21 or newer is required.
    pause
    exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo Error: Maven is required to build the project.
    pause
    exit /b 1
)
where powershell >nul 2>nul
if errorlevel 1 (
    echo Error: PowerShell is required to create the release ZIP.
    pause
    exit /b 1
)

echo Building PDF Handout Generator v1...
call mvn clean package
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

set "TARGET_DIR=%CD%\target"
set "STAGING_DIR=%TARGET_DIR%\pdf-handout-generator-v1"
set "JAR_NAME=pdf-handout-generator-v1.jar"
set "ZIP_NAME=pdf-handout-generator-v1.zip"
set "FINAL_ZIP=%TARGET_DIR%\%ZIP_NAME%"
set "TEMP_ZIP=%CD%\%ZIP_NAME%.tmp.zip"

if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
mkdir "%STAGING_DIR%"
copy /y "%TARGET_DIR%\%JAR_NAME%" "%STAGING_DIR%\%JAR_NAME%" >nul
copy /y "README.md" "%STAGING_DIR%\README.md" >nul
copy /y "LICENSE" "%STAGING_DIR%\LICENSE" >nul

>"%STAGING_DIR%\run.bat" echo @echo off
>>"%STAGING_DIR%\run.bat" echo setlocal
>>"%STAGING_DIR%\run.bat" echo where java ^>nul 2^>nul
>>"%STAGING_DIR%\run.bat" echo if errorlevel 1 ^(
>>"%STAGING_DIR%\run.bat" echo     echo Java 21 or newer is required.
>>"%STAGING_DIR%\run.bat" echo     pause
>>"%STAGING_DIR%\run.bat" echo     exit /b 1
>>"%STAGING_DIR%\run.bat" echo ^)
>>"%STAGING_DIR%\run.bat" echo java -jar "%%~dp0pdf-handout-generator-v1.jar"
>>"%STAGING_DIR%\run.bat" echo if errorlevel 1 pause
>>"%STAGING_DIR%\run.bat" echo endlocal

>"%STAGING_DIR%\run.sh" echo #!/usr/bin/env sh
>>"%STAGING_DIR%\run.sh" echo set -eu
>>"%STAGING_DIR%\run.sh" echo APP_DIR=$^(CDPATH= cd -- "$^(dirname -- "$0"^)" ^&^& pwd^)
>>"%STAGING_DIR%\run.sh" echo if ! command -v java ^>/dev/null 2^>^&1; then
>>"%STAGING_DIR%\run.sh" echo     echo "Java 21 or newer is required." ^>^&2
>>"%STAGING_DIR%\run.sh" echo     exit 1
>>"%STAGING_DIR%\run.sh" echo fi
>>"%STAGING_DIR%\run.sh" echo exec java -jar "$APP_DIR/pdf-handout-generator-v1.jar"
copy /y "%STAGING_DIR%\run.sh" "%STAGING_DIR%\run.command" >nul

if exist "%FINAL_ZIP%" del /q "%FINAL_ZIP%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%STAGING_DIR%' -DestinationPath '%FINAL_ZIP%' -Force"
if errorlevel 1 (
    echo ZIP creation failed.
    pause
    exit /b 1
)

rem Preserve the release ZIP outside target, remove all intermediates, then restore it.
if exist "%TEMP_ZIP%" del /q "%TEMP_ZIP%"
copy /y "%FINAL_ZIP%" "%TEMP_ZIP%" >nul
rmdir /s /q "%TARGET_DIR%"
mkdir "%TARGET_DIR%"
move /y "%TEMP_ZIP%" "%FINAL_ZIP%" >nul

echo.
echo Build complete.
echo Release ZIP: %FINAL_ZIP%
echo All intermediate build artifacts have been removed.
pause
endlocal
