#!/bin/bash
set -e
cd "$(dirname "$0")"

# ── Prerequisites ────────────────────────────────────────────────────────────
echo "Checking prerequisites..."

# Homebrew
if ! command -v brew &>/dev/null; then
    echo "Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi

# Java 17+
JAVA_VER=$(java -version 2>&1 | awk -F'"' '/version/{print $2}' | cut -d. -f1)
if [ -z "$JAVA_VER" ] || [ "$JAVA_VER" -lt 17 ]; then
    echo "Installing Java 21 (temurin)..."
    brew install --cask temurin@21
    # Reload JAVA_HOME for this session
    export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)"
fi

# Maven
if ! command -v mvn &>/dev/null; then
    echo "Installing Maven..."
    brew install maven
fi

# jpackage is bundled with JDK 14+ — verify it's available
if ! command -v jpackage &>/dev/null; then
    echo "jpackage not found. Make sure JAVA_HOME points to a JDK 17+ installation."
    echo "  brew install --cask temurin@21"
    exit 1
fi

echo "Prerequisites OK (java $(java -version 2>&1 | awk -F'"' '/version/{print $2}'), mvn $(mvn -q --version 2>&1 | head -1 | awk '{print $3}'))"
echo ""

echo "Building..."
mvn package -DskipTests -q

# Always rebuild icon
echo "Building icons..."
ICONSET="target/app-icon.iconset"
rm -rf "$ICONSET" target/app-icon.icns
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

# Always rebuild app bundle
APP_BUNDLE="target/dist/Album Organizer.app"
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
    --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.swing"
rm target/lib/album-organizer-1.0.0.jar

# Always rebuild DMG installer
DMG_FILE="target/dist/Album Organizer-1.1.0.dmg"
echo "Building macOS installer (.dmg)..."
jpackage \
    --type dmg \
    --name "Album Organizer" \
    --app-version "1.1.0" \
    --app-image "$APP_BUNDLE" \
    --icon target/app-icon.icns \
    --dest target/dist

echo ""
echo "Build complete:"
echo "  App:       $APP_BUNDLE"
echo "  Installer: $DMG_FILE"
echo ""
echo "Run with: open \"$APP_BUNDLE\""
