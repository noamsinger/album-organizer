#!/bin/bash
cd "$(dirname "$0")"

echo "Building..."
mvn package -DskipTests -q

# Convert icon to ICNS
if [ ! -f "target/app-icon.icns" ]; then
    echo "Converting icon to ICNS..."
    ICONSET="target/app-icon.iconset"
    mkdir -p "$ICONSET"
    sips -z 16 16       src/main/resources/app-icon.png --out "$ICONSET/icon_16x16.png"      >/dev/null 2>&1
    sips -z 32 32       src/main/resources/app-icon.png --out "$ICONSET/icon_16x16@2x.png"   >/dev/null 2>&1
    sips -z 32 32       src/main/resources/app-icon.png --out "$ICONSET/icon_32x32.png"      >/dev/null 2>&1
    sips -z 64 64       src/main/resources/app-icon.png --out "$ICONSET/icon_32x32@2x.png"   >/dev/null 2>&1
    sips -z 128 128     src/main/resources/app-icon.png --out "$ICONSET/icon_128x128.png"    >/dev/null 2>&1
    sips -z 256 256     src/main/resources/app-icon.png --out "$ICONSET/icon_128x128@2x.png" >/dev/null 2>&1
    sips -z 256 256     src/main/resources/app-icon.png --out "$ICONSET/icon_256x256.png"    >/dev/null 2>&1
    sips -z 512 512     src/main/resources/app-icon.png --out "$ICONSET/icon_256x256@2x.png" >/dev/null 2>&1
    sips -z 512 512     src/main/resources/app-icon.png --out "$ICONSET/icon_512x512.png"    >/dev/null 2>&1
    sips -z 1024 1024   src/main/resources/app-icon.png --out "$ICONSET/icon_512x512@2x.png" >/dev/null 2>&1
    iconutil -c icns "$ICONSET" -o target/app-icon.icns
fi

# Build .app bundle
APP_BUNDLE="target/dist/Album Organizer.app"
if [ ! -d "$APP_BUNDLE" ] || [ "target/album-organizer-1.0.0.jar" -nt "$APP_BUNDLE" ]; then
    echo "Building macOS app bundle..."
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
        --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.swing"
    rm target/lib/album-organizer-1.0.0.jar
fi

# Build .dmg installer
DMG_FILE="target/dist/Album Organizer-1.1.0.dmg"
if [ ! -f "$DMG_FILE" ] || [ "$APP_BUNDLE" -nt "$DMG_FILE" ]; then
    echo "Building macOS installer (.dmg)..."
    rm -f "$DMG_FILE"
    jpackage \
        --type dmg \
        --name "Album Organizer" \
        --app-version "1.1.0" \
        --app-image "$APP_BUNDLE" \
        --icon target/app-icon.icns \
        --dest target/dist
fi

echo ""
echo "Build complete:"
echo "  App:       $APP_BUNDLE"
echo "  Installer: $DMG_FILE"
echo ""
echo "Run with: open \"$APP_BUNDLE\""
