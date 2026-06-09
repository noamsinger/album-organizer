@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ══════════════════════════════════════════════════════
echo   Album Organizer — Windows build
echo ══════════════════════════════════════════════════════
echo.
echo Checking build prerequisites...
echo.

:: ── Java 21 (Temurin) ────────────────────────────────────────────────────────
set JAVA_OK=0
where java >NUL 2>&1
if %errorlevel%==0 (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set RAW=%%v
    )
    :: RAW is like "21.0.3" or "17.0.1" (with quotes)
    set RAW=!RAW:"=!
    for /f "tokens=1 delims=." %%m in ("!RAW!") do set MAJOR=%%m
    if !MAJOR! GEQ 17 (
        echo [OK] Java !RAW!
        set JAVA_OK=1
    )
)
if %JAVA_OK%==0 (
    echo [..] Java 17+ not found -- installing Temurin 21 via winget...
    winget install --id EclipseAdoptium.Temurin.21.JDK --silent --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo [FAIL] winget install failed. Install manually:
        echo        https://adoptium.net/temurin/releases/?version=21
        exit /b 1
    )
    echo [OK] Java 21 installed. Re-open this terminal so JAVA_HOME takes effect, then re-run build.bat.
    exit /b 0
)

:: ── Maven ────────────────────────────────────────────────────────────────────
where mvn >NUL 2>&1
if errorlevel 1 (
    echo [..] Maven not found -- installing via winget...
    winget install --id Apache.Maven --silent --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo [FAIL] winget install failed. Install manually:
        echo        https://maven.apache.org/download.cgi
        echo        Then add Maven bin\ to your PATH.
        exit /b 1
    )
    echo [OK] Maven installed. Re-open this terminal so mvn is on PATH, then re-run build.bat.
    exit /b 0
) else (
    for /f "tokens=3" %%v in ('mvn --version 2^>^&1 ^| findstr /i "Apache Maven"') do echo [OK] Maven %%v
)

:: ── jpackage (bundled with JDK 14+) ─────────────────────────────────────────
where jpackage >NUL 2>&1
if errorlevel 1 (
    echo [FAIL] jpackage not found.
    echo        Make sure JAVA_HOME\bin is on your PATH.
    echo        jpackage is included in JDK 17+. Re-open terminal after installing Java.
    exit /b 1
) else (
    echo [OK] jpackage
)

:: ── WiX Toolset (required for .msi installer) ────────────────────────────────
where candle >NUL 2>&1
if errorlevel 1 (
    echo [..] WiX Toolset not found -- installing via winget...
    winget install --id WixToolset.WiXToolset --silent --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo [WARN] WiX install failed. MSI packaging will likely fail.
        echo        Install manually: https://wixtoolset.org/releases/
        echo        Then add WiX bin to PATH and re-run build.bat.
    ) else (
        echo [OK] WiX Toolset installed. Re-open this terminal so candle is on PATH.
        exit /b 0
    )
) else (
    echo [OK] WiX Toolset
)

:: ── Git (optional -- needed to clone local AI servers) ───────────────────────
where git >NUL 2>&1
if errorlevel 1 (
    echo [..] Git not found -- installing via winget (needed for local AI servers)...
    winget install --id Git.Git --silent --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo [WARN] Git install failed. Install manually: https://git-scm.com/download/win
    ) else (
        echo [OK] Git installed
    )
) else (
    echo [OK] Git
)

:: ── Python 3 (optional -- needed for ComfyUI / SD / InvokeAI) ───────────────
where python >NUL 2>&1
if errorlevel 1 (
    echo [..] Python 3 not found -- installing via winget (needed for local AI servers)...
    winget install --id Python.Python.3.11 --silent --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo [WARN] Python install failed. Install manually: https://www.python.org/downloads/
    ) else (
        echo [OK] Python 3 installed
    )
) else (
    echo [OK] Python
)

echo.
echo All build prerequisites satisfied.
echo.

:: ── Optional AI tool guidance ─────────────────────────────────────────────────
echo ── Optional: Local AI servers ───────────────────────────────────────────
echo   The app supports several local AI providers. They are NOT required to
echo   build or run -- enable them individually in Settings - AI Enhancement.
echo.
echo   ComfyUI (image + video, localhost:8188):
echo     git clone https://github.com/comfyanonymous/ComfyUI
echo     cd ComfyUI ^&^& python -m venv venv ^&^& venv\Scripts\activate
echo     pip install -r requirements.txt
echo     python main.py --listen
echo     Video: cd custom_nodes ^&^& git clone https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite
echo.
echo   Stable Diffusion WebUI (image, localhost:7860):
echo     git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui
echo     cd stable-diffusion-webui ^&^& webui-user.bat  (add COMMANDLINE_ARGS=--api)
echo.
echo   InvokeAI (image, localhost:9090):
echo     pip install invokeai ^&^& invokeai-web
echo.
echo   Real-ESRGAN model (image upscale, no GPU needed):
echo     Download RealESRGAN_x4plus.onnx from:
echo     https://github.com/xinntao/Real-ESRGAN/releases
echo     Save to: %%USERPROFILE%%\.config\album-organizer\models\RealESRGAN_x4plus.onnx
echo ─────────────────────────────────────────────────────────────────────────
echo.

:: ── Build ─────────────────────────────────────────────────────────────────────
echo Building JAR...
call mvn package -DskipTests -q
if errorlevel 1 goto error

echo Building Windows app bundle...
if exist "target\dist" rmdir /s /q "target\dist"
copy /Y target\album-organizer-1.0.0.jar target\lib\ >NUL

jpackage ^
    --type app-image ^
    --name "Album Organizer" ^
    --app-version "1.5.0" ^
    --input target\lib ^
    --main-jar album-organizer-1.0.0.jar ^
    --main-class com.albumorganizer.AlbumOrganizerApp ^
    --icon src\main\resources\app-icon.png ^
    --dest target\dist ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.swing"
if errorlevel 1 goto error

del /Q target\lib\album-organizer-1.0.0.jar >NUL 2>&1

echo Building Windows installer (.msi)...
jpackage ^
    --type msi ^
    --name "Album Organizer" ^
    --app-version "1.5.0" ^
    --app-image "target\dist\Album Organizer" ^
    --icon src\main\resources\app-icon.png ^
    --dest target\dist ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut
if errorlevel 1 goto error

echo.
echo Build complete:
echo   App:       target\dist\Album Organizer\Album Organizer.exe
echo   Installer: target\dist\Album Organizer-1.5.0.msi
goto end

:error
echo.
echo BUILD FAILED. See errors above.
exit /b 1

:end
endlocal
