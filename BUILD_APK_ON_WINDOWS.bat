@echo off
setlocal EnableDelayedExpansion
title Stepwgn NR-MZ25 13-Band DSQ — Automated Windows CLI APK Builder
color 0B

echo ============================================================================
echo   STEPWGN NR-MZ25 13-BAND DSQ + LOUDNESS + SPATIAL 3D APK BUILDER (CLI)
echo   Zero-Bloat Native APK (<50 KB, <8 MB RAM on Car Stereo)
echo ============================================================================
echo.

cd /d "%~dp0"
set "TOOLS_DIR=%~dp0.cli_tools"
set "SDK_DIR=%TOOLS_DIR%\android-sdk"
set "GRADLE_DIR=%TOOLS_DIR%\gradle-8.5"

if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
if not exist "%SDK_DIR%\cmdline-tools" mkdir "%SDK_DIR%\cmdline-tools"

:: 1. Check for Java 17+
echo [1/4] Checking Java JDK 17...
where javac >nul 2>nul
if %errorlevel% neq 0 (
    echo Java JDK not found in PATH. Installing Eclipse Temurin JDK 17 via Windows Winget...
    winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-source-agreements --accept-package-agreements
    for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%i"
    if defined JAVA_HOME set "PATH=!JAVA_HOME!\bin;!PATH!"
)

:: 2. Download Android Command-Line Tools if not present
if not exist "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" (
    echo [2/4] Downloading Official Google Android Command-Line Tools (~140 MB)...
    powershell -Command "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile '%TOOLS_DIR%\cmdline.zip'"
    powershell -Command "Expand-Archive -Path '%TOOLS_DIR%\cmdline.zip' -DestinationPath '%SDK_DIR%\cmdline-tools' -Force"
    move "%SDK_DIR%\cmdline-tools\cmdline-tools" "%SDK_DIR%\cmdline-tools\latest" >nul
)

:: 3. Install Android 34 Platform & Build-Tools via sdkmanager
if not exist "%SDK_DIR%\platforms\android-34" (
    echo [3/4] Installing Android SDK Platform 34 and Build-Tools 34.0.0...
    powershell -Command "1..10 | ForEach-Object { 'y' } | & '%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat' --sdk_root='%SDK_DIR%' --licenses"
    call "%SDK_DIR%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK_DIR%" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
)

:: 4. Download Gradle 8.5 CLI if not present
if not exist "%GRADLE_DIR%\bin\gradle.bat" (
    echo [4/4] Downloading Gradle 8.5 CLI Compiler...
    powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.5-bin.zip' -OutFile '%TOOLS_DIR%\gradle.zip'"
    powershell -Command "Expand-Archive -Path '%TOOLS_DIR%\gradle.zip' -DestinationPath '%TOOLS_DIR%' -Force"
)

:: Write local.properties with escaped SDK path
set "ESCAPED_SDK=%SDK_DIR:\=\\%"
echo sdk.dir=%ESCAPED_SDK%> "%~dp0local.properties"
set "ANDROID_HOME=%SDK_DIR%"

echo.
echo Compiling Stepwgn_NRMZ25_13Band_DSQ.apk...
call "%GRADLE_DIR%\bin\gradle.bat" assembleDebug --no-daemon

if exist "%~dp0app\build\outputs\apk\debug\app-debug.apk" (
    copy /Y "%~dp0app\build\outputs\apk\debug\app-debug.apk" "%~dp0Stepwgn_NRMZ25_13Band_DSQ.apk" >nul
    echo.
    echo ============================================================================
    echo   SUCCESS! Your APK is ready at:
    echo   %~dp0Stepwgn_NRMZ25_13Band_DSQ.apk
    echo   Copy this file to your USB flash drive and install it on the Stepwgn!
    echo ============================================================================
    explorer.exe /select,"%~dp0Stepwgn_NRMZ25_13Band_DSQ.apk"
) else (
    echo.
    echo [ERROR] Build did not finish. Check the messages above.
)
pause
