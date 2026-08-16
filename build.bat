@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "PROJECT_DIR=%CD%"
set "TARGET_DIR=%PROJECT_DIR%\target"
set "APP_BASENAME=pdf-handout-generator"

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

rem Locate PdfHandoutApp.java without assuming a particular package path.
set "SOURCE_FILE="
for /r "%PROJECT_DIR%\src\main\java" %%F in (PdfHandoutApp.java) do (
    if not defined SOURCE_FILE set "SOURCE_FILE=%%~fF"
)

if not defined SOURCE_FILE (
    echo Error: PdfHandoutApp.java was not found under src\main\java.
    pause
    exit /b 1
)

rem Extract the quoted value assigned to APP_VERSION from the Java source.
rem PowerShell performs the regular-expression match more reliably than FINDSTR.
set "VERSION="
for /f "usebackq delims=" %%V in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$text = Get-Content -LiteralPath '%SOURCE_FILE%' -Raw; $match = [regex]::Match($text, 'private\s+static\s+final\s+String\s+APP_VERSION\s*=\s*\"([^\"]+)\"\s*;'); if ($match.Success) { $match.Groups[1].Value }"`) do (
    if not defined VERSION set "VERSION=%%V"
)

if not defined VERSION (
    echo Error: Could not extract APP_VERSION from:
    echo %SOURCE_FILE%
    pause
    exit /b 1
)

rem Allow semantic-version characters only: letters, digits, period, plus, hyphen.
echo(%VERSION%| findstr /r /x "[0-9A-Za-z.+-][0-9A-Za-z.+-]*" >nul
if errorlevel 1 (
    echo Error: APP_VERSION contains unsupported filename characters: %VERSION%
    pause
    exit /b 1
)

set "RELEASE_NAME=%APP_BASENAME%-v%VERSION%"
set "STAGING_DIR=%TARGET_DIR%\%RELEASE_NAME%"
set "JAR_NAME=%RELEASE_NAME%.jar"
set "ZIP_NAME=%RELEASE_NAME%.zip"
set "FINAL_ZIP=%TARGET_DIR%\%ZIP_NAME%"
set "TEMP_ZIP=%PROJECT_DIR%\%ZIP_NAME%.tmp.zip"

echo Building PDF Handout Generator v%VERSION%...
call mvn clean package
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

rem Find the executable shaded JAR while excluding the retained thin JAR.
set "FAT_JAR="
set /a FAT_JAR_COUNT=0
for %%F in ("%TARGET_DIR%\*.jar") do (
    if exist "%%~fF" (
        echo %%~nxF | findstr /b /i "original-" >nul
        if errorlevel 1 (
            echo %%~nxF | findstr /i /e "-sources.jar -javadoc.jar" >nul
            if errorlevel 1 (
                set /a FAT_JAR_COUNT+=1
                set "FAT_JAR=%%~fF"
            )
        )
    )
)

if not "%FAT_JAR_COUNT%"=="1" (
    echo Error: Expected exactly one executable JAR in target, found %FAT_JAR_COUNT%.
    dir /b "%TARGET_DIR%\*.jar" 2>nul
    pause
    exit /b 1
)

if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
mkdir "%STAGING_DIR%"

copy /y "%FAT_JAR%" "%STAGING_DIR%\%JAR_NAME%" >nul
if errorlevel 1 goto :copy_error

copy /y "%PROJECT_DIR%\README.md" "%STAGING_DIR%\README.md" >nul
if errorlevel 1 goto :copy_error

copy /y "%PROJECT_DIR%\LICENSE" "%STAGING_DIR%\LICENSE" >nul
if errorlevel 1 goto :copy_error

if exist "%PROJECT_DIR%\tech_specs.md" (
    copy /y "%PROJECT_DIR%\tech_specs.md" "%STAGING_DIR%\tech_specs.md" >nul
    if errorlevel 1 goto :copy_error
)

rem Generate the Windows launcher with the versioned JAR filename.
>"%STAGING_DIR%\run.bat" echo @echo off
>>"%STAGING_DIR%\run.bat" echo setlocal
>>"%STAGING_DIR%\run.bat" echo where java ^>nul 2^>nul
>>"%STAGING_DIR%\run.bat" echo if errorlevel 1 ^(
>>"%STAGING_DIR%\run.bat" echo     echo Java 21 or newer is required.
>>"%STAGING_DIR%\run.bat" echo     pause
>>"%STAGING_DIR%\run.bat" echo     exit /b 1
>>"%STAGING_DIR%\run.bat" echo ^)
>>"%STAGING_DIR%\run.bat" echo java -jar "%%~dp0%JAR_NAME%"
>>"%STAGING_DIR%\run.bat" echo if errorlevel 1 pause
>>"%STAGING_DIR%\run.bat" echo endlocal

rem Generate the Linux launcher with the versioned JAR filename.
>"%STAGING_DIR%\run.sh" echo #!/usr/bin/env sh
>>"%STAGING_DIR%\run.sh" echo set -eu
>>"%STAGING_DIR%\run.sh" echo APP_DIR=$^(CDPATH= cd -- "$^(dirname -- "$0"^)" ^&^& pwd^)
>>"%STAGING_DIR%\run.sh" echo if ! command -v java ^>/dev/null 2^>^&1; then
>>"%STAGING_DIR%\run.sh" echo     echo "Java 21 or newer is required." ^>^&2
>>"%STAGING_DIR%\run.sh" echo     exit 1
>>"%STAGING_DIR%\run.sh" echo fi
>>"%STAGING_DIR%\run.sh" echo exec java -jar "$APP_DIR/%JAR_NAME%"

rem The macOS launcher uses the same POSIX shell content.
copy /y "%STAGING_DIR%\run.sh" "%STAGING_DIR%\run.command" >nul
if errorlevel 1 goto :copy_error

if exist "%FINAL_ZIP%" del /q "%FINAL_ZIP%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -LiteralPath '%STAGING_DIR%' -DestinationPath '%FINAL_ZIP%' -Force"
if errorlevel 1 (
    echo Error: ZIP creation failed.
    pause
    exit /b 1
)

rem Preserve only the finished release ZIP under target.
if exist "%TEMP_ZIP%" del /q "%TEMP_ZIP%"
copy /y "%FINAL_ZIP%" "%TEMP_ZIP%" >nul
if errorlevel 1 goto :copy_error

rmdir /s /q "%TARGET_DIR%"
mkdir "%TARGET_DIR%"
move /y "%TEMP_ZIP%" "%FINAL_ZIP%" >nul
if errorlevel 1 goto :copy_error

echo.
echo Build complete.
echo Application version: %VERSION%
echo Release ZIP: %FINAL_ZIP%
echo All intermediate build artifacts have been removed.
pause
exit /b 0

:copy_error
echo Error: A required file operation failed.
pause
exit /b 1
