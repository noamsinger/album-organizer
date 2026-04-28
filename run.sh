#!/bin/bash

# Function to gracefully stop previous execution
stop_previous_execution() {
    PIDS=$(pgrep -f "com.albumorganizer.AlbumOrganizerApp" 2>/dev/null)
    # Also check for app-image launcher
    PIDS="$PIDS $(pgrep -f "Album Organizer" 2>/dev/null)"
    PIDS=$(echo "$PIDS" | tr ' ' '\n' | grep -v '^$' | sort -u)

    if [ -z "$PIDS" ]; then
        return 0
    fi

    echo "Found running Album Organizer process(es): $PIDS"
    echo "Stopping previous execution gracefully..."

    for PID in $PIDS; do
        kill -TERM "$PID" 2>/dev/null
    done

    WAIT_TIME=0
    MAX_WAIT=10
    while [ $WAIT_TIME -lt $MAX_WAIT ]; do
        if ! pgrep -f "com.albumorganizer.AlbumOrganizerApp" > /dev/null 2>&1 && \
           ! pgrep -f "Album Organizer" > /dev/null 2>&1; then
            echo "Previous execution stopped successfully."
            return 0
        fi
        sleep 1
        WAIT_TIME=$((WAIT_TIME + 1))
    done

    REMAINING=$(pgrep -f "com.albumorganizer.AlbumOrganizerApp" 2>/dev/null)
    REMAINING="$REMAINING $(pgrep -f 'Album Organizer' 2>/dev/null)"
    for PID in $REMAINING; do
        kill -9 "$PID" 2>/dev/null
    done
    echo "Force killed process(es)."
}

cd "$(dirname "$0")"

stop_previous_execution
sleep 1

echo "Building application..."
mvn package -DskipTests -q

# On macOS, build a proper .app bundle so the dock shows the correct name and icon
if [[ "$OSTYPE" == "darwin"* ]]; then
    APP_BUNDLE="target/dist/Album Organizer.app"

    # Convert PNG icon to ICNS if not already done
    if [ ! -f "target/app-icon.icns" ]; then
        echo "Converting icon to ICNS..."
        ICONSET="target/app-icon.iconset"
        mkdir -p "$ICONSET"
        sips -z 16 16     src/main/resources/app-icon.png --out "$ICONSET/icon_16x16.png"    > /dev/null 2>&1
        sips -z 32 32     src/main/resources/app-icon.png --out "$ICONSET/icon_16x16@2x.png" > /dev/null 2>&1
        sips -z 32 32     src/main/resources/app-icon.png --out "$ICONSET/icon_32x32.png"    > /dev/null 2>&1
        sips -z 64 64     src/main/resources/app-icon.png --out "$ICONSET/icon_32x32@2x.png" > /dev/null 2>&1
        sips -z 128 128   src/main/resources/app-icon.png --out "$ICONSET/icon_128x128.png"  > /dev/null 2>&1
        sips -z 256 256   src/main/resources/app-icon.png --out "$ICONSET/icon_128x128@2x.png" > /dev/null 2>&1
        sips -z 256 256   src/main/resources/app-icon.png --out "$ICONSET/icon_256x256.png"  > /dev/null 2>&1
        iconutil -c icns "$ICONSET" -o target/app-icon.icns
    fi

    # Rebuild app bundle if jar is newer
    if [ ! -d "$APP_BUNDLE" ] || [ "target/album-organizer-1.0.0.jar" -nt "$APP_BUNDLE" ]; then
        echo "Building macOS app bundle..."
        rm -rf "target/dist"
        # Copy main jar into lib dir temporarily for jpackage input
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
    fi

    echo "Starting Album Organizer..."
    open "$APP_BUNDLE"
else
    echo "Starting Album Organizer..."
    java --module-path target/lib \
      --add-modules javafx.controls,javafx.fxml \
      -Dfile.encoding=UTF-8 \
      -cp target/album-organizer-1.0.0.jar:target/lib/* \
      com.albumorganizer.AlbumOrganizerApp
fi
