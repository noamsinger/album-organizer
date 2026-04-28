@echo off
REM Build script for Album Organizer (Windows)

echo ========================================
echo Building Album Organizer
echo ========================================

REM Clean and compile
echo.
echo Step 1: Cleaning and compiling...
call mvn clean compile
if errorlevel 1 goto error

REM Run tests
echo.
echo Step 2: Running tests...
call mvn test
if errorlevel 1 goto error

REM Package
echo.
echo Step 3: Packaging application...
call mvn package -DskipTests
if errorlevel 1 goto error

REM Check if build was successful
if exist "target\album-organizer-1.0.0.jar" (
    echo.
    echo ========================================
    echo Build successful!
    echo ========================================
    echo.
    echo Application JAR: target\album-organizer-1.0.0.jar
    echo Dependencies: target\lib\
    echo.
    echo To run the application:
    echo   mvn javafx:run
    echo   OR
    echo   run.bat
    echo.
    echo To create native installer (requires JDK 17+):
    echo   mvn jpackage:jpackage
    echo.
) else (
    goto error
)

goto end

:error
echo.
echo ========================================
echo Build failed!
echo ========================================
exit /b 1

:end
pause
