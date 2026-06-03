#!/bin/bash
set -e
cd "$(dirname "$0")"

mvn package -DskipTests -q

FX_MODS=""
for mod in controls fxml swing graphics base; do
  jar=$(find ~/.m2/repository/org/openjfx/javafx-${mod}/21.0.1 -name "*mac-aarch64.jar" 2>/dev/null | head -1)
  [ -n "$jar" ] && FX_MODS="$FX_MODS:$jar"
done
FX_MODS="${FX_MODS#:}"

java \
  --module-path "$FX_MODS" \
  --add-modules=javafx.controls,javafx.fxml,javafx.swing \
  -cp "target/album-organizer-1.0.0.jar:$(ls target/lib/*.jar | tr '\n' ':')" \
  com.albumorganizer.AlbumOrganizerApp
