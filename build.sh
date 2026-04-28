#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "========================================"
echo "Building Album Organizer"
echo "========================================"

echo ""
echo "Step 1: Cleaning and compiling..."
mvn clean compile

echo ""
echo "Step 2: Running tests..."
mvn test

echo ""
echo "Step 3: Packaging JAR and dependencies..."
mvn package -DskipTests

if [ ! -f "target/album-organizer-1.0.0.jar" ]; then
    echo "Build failed - JAR not found"
    exit 1
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
    echo ""
    echo "Step 4: Converting icon to ICNS..."
    ICONSET="target/app-icon.iconset"
    rm -rf "$ICONSET" target/app-icon.icns
    mkdir -p "$ICONSET"
    sips -z 16 16     src/main/resources/app-icon.png --out "$ICONSET/icon_16x16.png"      > /dev/null
    sips -z 32 32     src/main/resources/app-icon.png --out "$ICONSET/icon_16x16@2x.png"   > /dev/null
    sips -z 32 32     src/main/resources/app-icon.png --out "$ICONSET/icon_32x32.png"      > /dev/null
    sips -z 64 64     src/main/resources/app-icon.png --out "$ICONSET/icon_32x32@2x.png"   > /dev/null
    sips -z 128 128   src/main/resources/app-icon.png --out "$ICONSET/icon_128x128.png"    > /dev/null
    sips -z 256 256   src/main/resources/app-icon.png --out "$ICONSET/icon_128x128@2x.png" > /dev/null
    sips -z 256 256   src/main/resources/app-icon.png --out "$ICONSET/icon_256x256.png"    > /dev/null
    iconutil -c icns "$ICONSET" -o target/app-icon.icns

    echo ""
    echo "Step 5: Building macOS app bundle..."
    rm -rf "target/dist"
    cp target/album-organizer-1.0.0.jar target/lib/
    jpackage \
        --type app-image \
        --name "Album Organizer" \
        --app-version "1.1.0" \
        --input target/lib \
        --main-jar album-organizer-1.0.0.jar \
        --main-class com.albumorganizer.AlbumOrganizerApp \
        --icon target/app-icon.icns \
        --dest target/dist \
        --java-options "-Dfile.encoding=UTF-8" \
        --java-options "-Dapple.awt.application.name=Album\\ Organizer" \
        --java-options "--module-path \$APPDIR" \
        --java-options "--add-modules=javafx.controls,javafx.fxml"
    rm target/lib/album-organizer-1.0.0.jar

    echo ""
    echo "========================================"
    echo "Build successful!"
    echo "========================================"
    echo ""
    echo "App bundle: target/dist/Album Organizer.app"
    echo "Run with:   ./run.sh"
else
    echo ""
    echo "========================================"
    echo "Build successful!"
    echo "========================================"
    echo ""
    echo "JAR: target/album-organizer-1.0.0.jar"
    echo "Run with: ./run.sh"
fi
