@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ======================================================
echo   Album Organizer -- Windows build
echo ======================================================
echo.
echo Checking build prerequisites...
echo.

:: --- Java 21 (Temurin) -------------------------------------------------------
set JAVA_OK=0
where java >NUL 2>&1
if %errorlevel%==0 (
    for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set RAW=%%v
    set RAW=!RAW:"=!
    for /f "tokens=1 delims=." %%m in ("!RAW!") do set MAJOR=%%m
    if !MAJOR! GEQ 17 (
        echo [OK]  Java !RAW!
        set JAVA_OK=1
    )
)
if %JAVA_OK%==0 goto :install_java
goto :check_maven

:install_java
echo [..]  Java 17+ not found -- installing Temurin 21 via winget...
winget install --id EclipseAdoptium.Temurin.21.JDK --silent --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
    echo [XX]  winget install failed. Install manually:
    echo       https://adoptium.net/temurin/releases/?version=21
    exit /b 1
)
echo [OK]  Java 21 installed. Re-open this terminal so JAVA_HOME takes effect, then re-run build.bat.
exit /b 0

:: --- Maven -------------------------------------------------------------------
:check_maven
call :find_on_path mvn MVN_CMD
if not defined MVN_CMD call :find_file_recursive "%ProgramFiles%"                              "mvn.cmd" MVN_CMD
if not defined MVN_CMD call :find_file_recursive "%ProgramFiles(x86)%"                        "mvn.cmd" MVN_CMD
if not defined MVN_CMD call :find_file_recursive "%LOCALAPPDATA%\Microsoft\WinGet\Packages"   "mvn.cmd" MVN_CMD
if not defined MVN_CMD call :find_file_recursive "%USERPROFILE%\.m2"                          "mvn.cmd" MVN_CMD
if defined MVN_CMD goto :maven_found

echo [..]  Maven not found -- installing via winget...
winget install --id Apache.Maven --silent --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
    echo [XX]  winget install failed. Install manually:
    echo       https://maven.apache.org/download.cgi
    echo       Then add Maven bin\ to your PATH.
    exit /b 1
)
call :find_file_recursive "%ProgramFiles%"                            "mvn.cmd" MVN_CMD
call :find_file_recursive "%ProgramFiles(x86)%"                      "mvn.cmd" MVN_CMD
call :find_file_recursive "%LOCALAPPDATA%\Microsoft\WinGet\Packages" "mvn.cmd" MVN_CMD
if defined MVN_CMD goto :maven_found
echo [!!]  Maven installed but mvn.cmd not found in expected locations.
echo       Re-open this terminal so mvn is on PATH, then re-run build.bat.
exit /b 0

:maven_found
for %%F in ("!MVN_CMD!") do set "MVN_BIN=%%~dpF"
if "!MVN_BIN:~-1!"=="\" set "MVN_BIN=!MVN_BIN:~0,-1!"
set "PATH=!MVN_BIN!;!PATH!"
echo [OK]  Maven

:: --- jpackage (bundled with JDK 14+) -----------------------------------------
:check_jpackage
call :find_on_path jpackage JP_CMD
if defined JP_CMD goto :jpackage_found
if defined JAVA_HOME if exist "!JAVA_HOME!\bin\jpackage.exe" set "JP_CMD=!JAVA_HOME!\bin\jpackage.exe"
if defined JP_CMD goto :jpackage_found
call :find_file_recursive "%ProgramFiles%\Eclipse Adoptium"             "jpackage.exe" JP_CMD
if defined JP_CMD goto :jpackage_found
call :find_file_recursive "%ProgramFiles%\Java"                         "jpackage.exe" JP_CMD
if defined JP_CMD goto :jpackage_found
call :find_file_recursive "%ProgramFiles%\Microsoft"                    "jpackage.exe" JP_CMD
if defined JP_CMD goto :jpackage_found
call :find_file_recursive "%LOCALAPPDATA%\Microsoft\WinGet\Packages"    "jpackage.exe" JP_CMD
if defined JP_CMD goto :jpackage_found

echo [XX]  jpackage not found.
echo       jpackage is bundled with JDK 17+. Make sure JDK 17+ is installed
echo       and JAVA_HOME\bin is on your PATH, then re-run build.bat.
exit /b 1

:jpackage_found
for %%F in ("!JP_CMD!") do set "JP_BIN=%%~dpF"
if "!JP_BIN:~-1!"=="\" set "JP_BIN=!JP_BIN:~0,-1!"
set "PATH=!JP_BIN!;!PATH!"
if not defined JAVA_HOME set "JAVA_HOME=!JP_BIN!\.."
echo [OK]  jpackage

:: --- WiX Toolset (required for .msi installer) --------------------------------
:check_wix
call :find_on_path candle WIX_CMD
if defined WIX_CMD goto :wix_found
call :find_file_recursive "%ProgramFiles(x86)%"                        "candle.exe" WIX_CMD
if defined WIX_CMD goto :wix_found
call :find_file_recursive "%ProgramFiles%"                             "candle.exe" WIX_CMD
if defined WIX_CMD goto :wix_found
call :find_file_recursive "%LOCALAPPDATA%\Microsoft\WinGet\Packages"   "candle.exe" WIX_CMD
if defined WIX_CMD goto :wix_found

echo [..]  WiX Toolset not found -- installing via winget...
winget install --id WixToolset.WiXToolset --silent --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
    echo [!!]  WiX install failed. MSI packaging will likely fail.
    echo       Install manually: https://wixtoolset.org/releases/
    echo       Then add WiX bin to PATH and re-run build.bat.
    goto :check_git
)
call :find_file_recursive "%ProgramFiles(x86)%"                        "candle.exe" WIX_CMD
call :find_file_recursive "%ProgramFiles%"                             "candle.exe" WIX_CMD
call :find_file_recursive "%LOCALAPPDATA%\Microsoft\WinGet\Packages"   "candle.exe" WIX_CMD
if defined WIX_CMD goto :wix_found
echo [!!]  WiX installed but candle.exe not found. Re-open terminal after adding WiX bin to PATH.
goto :check_git

:wix_found
for %%F in ("!WIX_CMD!") do set "WIX_BIN=%%~dpF"
if "!WIX_BIN:~-1!"=="\" set "WIX_BIN=!WIX_BIN:~0,-1!"
set "PATH=!WIX_BIN!;!PATH!"
echo [OK]  WiX Toolset

:: --- Git (optional) -----------------------------------------------------------
:check_git
where git >NUL 2>&1
if not errorlevel 1 goto :git_ok
echo [..]  Git not found -- installing via winget (needed for local AI servers)...
winget install --id Git.Git --silent --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
    echo [!!]  Git install failed. Install manually: https://git-scm.com/download/win
) else (
    echo [OK]  Git installed
)
goto :check_python
:git_ok
echo [OK]  Git

:: --- Python 3 (optional) ------------------------------------------------------
:check_python
where python >NUL 2>&1
if not errorlevel 1 goto :python_ok
echo [..]  Python 3 not found -- installing via winget (needed for local AI servers)...
winget install --id Python.Python.3.11 --silent --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
    echo [!!]  Python install failed. Install manually: https://www.python.org/downloads/
) else (
    echo [OK]  Python 3 installed
)
goto :prereqs_done
:python_ok
echo [OK]  Python

:prereqs_done
echo.
echo All build prerequisites satisfied.
echo.

:: --- Optional AI tool guidance -----------------------------------------------
echo ----------------------------------------------------------------------
echo   Optional: Local AI servers
echo   Not required to build or run -- enable in Settings -^> AI Enhancement
echo ----------------------------------------------------------------------
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
echo     Save to: %USERPROFILE%\.config\album-organizer\models\RealESRGAN_x4plus.onnx
echo ----------------------------------------------------------------------
echo.

:: --- Build -------------------------------------------------------------------
echo Building JAR...
echo [..] Downloading dependencies (first build downloads ~200 MB -- this may take a few minutes)...
:: Delete any partial downloads left by a previous failed attempt
if exist "%USERPROFILE%\.m2\repository\com\microsoft\onnxruntime" (
    del /s /q "%USERPROFILE%\.m2\repository\com\microsoft\onnxruntime\*.part" >NUL 2>&1
    del /s /q "%USERPROFILE%\.m2\repository\com\microsoft\onnxruntime\*.lastUpdated" >NUL 2>&1
)
call "!MVN_CMD!" package -DskipTests -P javafx-windows -Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 -Dmaven.artifact.threads=1
if errorlevel 1 goto error

echo Building Windows app bundle...
if exist "target\dist" rmdir /s /q "target\dist"
copy /Y target\album-organizer-1.0.0.jar target\lib\ >NUL

"!JP_CMD!" ^
    --type app-image ^
    --name "Album Organizer" ^
    --app-version "1.5.0" ^
    --input target\lib ^
    --main-jar album-organizer-1.0.0.jar ^
    --main-class com.albumorganizer.AlbumOrganizerApp ^
    --icon src\main\resources\app-icon.png ^
    --dest target\dist ^
    --win-console ^
    --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.graphics,javafx.base ^
    --jlink-options "--module-path target\lib" ^
    --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 goto error

del /Q target\lib\album-organizer-1.0.0.jar >NUL 2>&1

echo Building Windows installer (.msi)...
"!JP_CMD!" ^
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
goto :eof

:: ============================================================
:: Subroutine: find_on_path <name> <result_var>
::   Sets result_var to the full path to <name> if found on PATH.
:: ============================================================
:find_on_path
set "%~2="
for /f "delims=" %%F in ('where "%~1" 2^>NUL') do (
    if not defined %~2 set "%~2=%%F"
)
goto :eof

:: ============================================================
:: Subroutine: find_file_recursive <root> <filename> <result_var>
::   Recursively searches <root> for <filename>.
::   Sets result_var to the first match (no surrounding quotes).
::   Does nothing if <root> does not exist or result_var already set.
:: ============================================================
:find_file_recursive
if not exist "%~1" goto :eof
if defined %~3 goto :eof
for /f "delims=" %%F in ('dir /s /b "%~1\%~2" 2^>NUL') do (
    if not defined %~3 set "%~3=%%F"
)
goto :eof
