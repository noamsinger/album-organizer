#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "Building..."
mvn package -DskipTests -q

APP_BUNDLE="target/dist/Album Organizer.app"

if [ ! -d "$APP_BUNDLE" ]; then
    echo "Assembling app bundle..."
    rm -rf "target/dist"
    cp target/album-organizer-1.0.0.jar target/lib/
    jpackage \
        --type app-image \
        --name "Album Organizer" \
        --app-version "1.1.0" \
        --input target/lib \
        --main-jar album-organizer-1.0.0.jar \
        --main-class com.albumorganizer.AlbumOrganizerApp \
        --dest target/dist \
        --java-options "-Dfile.encoding=UTF-8" \
        --java-options "-Dapple.awt.application.name=Album\\ Organizer" \
        --java-options "--module-path \$APPDIR" \
        --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.swing"
    rm target/lib/album-organizer-1.0.0.jar
else
    # Update the jar inside the existing bundle so we don't rebuild jpackage every run
    cp target/album-organizer-1.0.0.jar "$APP_BUNDLE/Contents/app/album-organizer-1.0.0.jar"
fi

echo "Launching..."
open "$APP_BUNDLE"
