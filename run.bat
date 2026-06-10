@echo off
setlocal
cd /d "%~dp0"

if not exist "target\dist\AlbumOrganizer\AlbumOrganizer.exe" (
    echo [..] Executable not found. Running build.bat first...
    call build.bat
    if errorlevel 1 (
        echo [XX] Build failed. Cannot run application.
        exit /b 1
    )
)

echo Running AlbumOrganizer...
"target\dist\AlbumOrganizer\AlbumOrganizer.exe"
