@echo off
REM Launch script for Album Organizer (Windows)

REM Function to stop previous execution gracefully
echo Checking for running Album Organizer processes...
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /NH 2^>NUL ^| findstr "java.exe"') do (
    wmic process where "ProcessId=%%i" get CommandLine 2>NUL | findstr "AlbumOrganizerApp" >NUL
    if not errorlevel 1 (
        echo Found running Album Organizer process %%i. Stopping gracefully...
        taskkill /PID %%i /T >NUL 2>&1
    )
)

REM Wait for graceful shutdown
timeout /t 2 /nobreak >NUL

REM Force kill if still running
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /NH 2^>NUL ^| findstr "java.exe"') do (
    wmic process where "ProcessId=%%i" get CommandLine 2>NUL | findstr "AlbumOrganizerApp" >NUL
    if not errorlevel 1 (
        echo Force killing process %%i...
        taskkill /F /PID %%i /T >NUL 2>&1
    )
)

set SCRIPT_DIR=%~dp0

echo Starting Album Organizer...
java --module-path "%SCRIPT_DIR%target\lib" ^
     --add-modules javafx.controls,javafx.fxml ^
     -Dapple.awt.application.name="Album Organizer" ^
     -cp "%SCRIPT_DIR%target\album-organizer-1.0.0.jar;%SCRIPT_DIR%target\lib\*" ^
     com.albumorganizer.AlbumOrganizerApp

pause
