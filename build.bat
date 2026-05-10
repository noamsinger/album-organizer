@echo off
cd /d "%~dp0"

echo Building...
call mvn package -DskipTests -q
if errorlevel 1 goto error

set "APP_EXE=target\dist\Album Organizer\Album Organizer.exe"

if not exist "%APP_EXE%" (
    echo Building Windows app...
    if exist "target\dist" rmdir /s /q "target\dist"
    copy target\album-organizer-1.0.0.jar target\lib\ >NUL
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
        --java-options "--module-path %%APPDIR%%" ^
        --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.swing"
    del target\lib\album-organizer-1.0.0.jar >NUL 2>&1
)

echo Starting Album Organizer...
start "" "%APP_EXE%" %*
goto end

:error
echo Build failed!
exit /b 1

:end
