@echo off
setlocal
cd /d "%~dp0"

echo Building...
call mvn package -DskipTests -q
if errorlevel 1 goto error

echo Building Windows app...
if exist "target\dist" rmdir /s /q "target\dist"
copy /Y target\album-organizer-1.0.0.jar target\lib\ >NUL

jpackage ^
    --type app-image ^
    --name "Album Organizer" ^
    --app-version "1.1.0" ^
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
    --app-version "1.1.0" ^
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
echo   Installer: target\dist\Album Organizer-1.1.0.msi
goto end

:error
echo Build failed!
exit /b 1

:end
endlocal
