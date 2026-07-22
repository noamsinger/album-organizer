#!/bin/bash
set -e
cd "$(dirname "$0")"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[OK]  $1${NC}"; }
warn() { echo -e "${YELLOW}[!!]  $1${NC}"; }
fail() { echo -e "${RED}[XX]  $1${NC}"; }
info() { echo "      $1"; }

echo "======================================================"
echo "  Album Organizer -- macOS build"
echo "======================================================"
echo ""
echo "Checking build prerequisites..."
echo ""

# --- Homebrew -----------------------------------------------------------------
if ! command -v brew &>/dev/null; then
    warn "Homebrew not found -- installing..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    if [ -f /opt/homebrew/bin/brew ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
else
    ok "Homebrew $(brew --version | head -1 | awk '{print $2}')"
fi

# --- Java 21 (Temurin) -------------------------------------------------------
JAVA_VER=$(java -version 2>&1 | awk -F'"' '/version/{print $2}' | cut -d. -f1)
if [ -z "$JAVA_VER" ] || [ "$JAVA_VER" -lt 17 ]; then
    warn "Java 17+ not found -- installing Temurin 21..."
    brew install --cask temurin@21
    export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)"
else
    ok "Java $JAVA_VER ($(java -version 2>&1 | head -1))"
fi

if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
fi

# --- Maven -------------------------------------------------------------------
if ! command -v mvn &>/dev/null; then
    warn "Maven not found -- installing..."
    brew install maven
else
    ok "Maven $(mvn --version 2>&1 | head -1 | awk '{print $3}')"
fi

# --- jpackage (bundled with JDK 14+) -----------------------------------------
if ! command -v jpackage &>/dev/null; then
    JP="$JAVA_HOME/bin/jpackage"
    if [ -x "$JP" ]; then
        export PATH="$JAVA_HOME/bin:$PATH"
        ok "jpackage (via JAVA_HOME)"
    else
        fail "jpackage not found."
        info "Make sure JAVA_HOME points to a JDK 17+ installation."
        info "  brew install --cask temurin@21"
        exit 1
    fi
else
    ok "jpackage"
fi

# --- sips + iconutil (macOS built-in) ----------------------------------------
if ! command -v sips &>/dev/null || ! command -v iconutil &>/dev/null; then
    fail "sips/iconutil not found. These are built into macOS -- are you on macOS?"
    exit 1
else
    ok "sips + iconutil (macOS built-in)"
fi

# --- Python 3 (optional -- needed for ComfyUI, Stable Diffusion, InvokeAI) --
if ! command -v python3 &>/dev/null; then
    warn "Python 3 not found -- installing (needed to run local AI servers)..."
    brew install python
else
    ok "Python $(python3 --version 2>&1 | awk '{print $2}') (optional: local AI servers)"
fi

# --- Git (optional -- needed to clone local AI servers) ----------------------
if ! command -v git &>/dev/null; then
    warn "Git not found -- installing (needed to clone local AI servers)..."
    brew install git
else
    ok "Git $(git --version | awk '{print $3}') (optional: local AI servers)"
fi

# --- curl (needed by Homebrew install + ComfyUI API) -------------------------
if ! command -v curl &>/dev/null; then
    warn "curl not found -- installing..."
    brew install curl
else
    ok "curl $(curl --version | head -1 | awk '{print $2}')"
fi

echo ""
echo "All build prerequisites satisfied."
echo ""

# --- Optional AI tool guidance -----------------------------------------------
echo "----------------------------------------------------------------------"
echo "  Optional: Local AI servers"
echo "  Not required to build or run -- enable in Settings -> AI Enhancement"
echo "----------------------------------------------------------------------"
echo ""
echo "  ComfyUI (image + video, localhost:8188):"
info "git clone https://github.com/comfyanonymous/ComfyUI && cd ComfyUI"
info "python3 -m venv venv && source venv/bin/activate"
info "pip install -r requirements.txt"
info "python main.py --listen"
info "Video support: cd custom_nodes && git clone https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite"
echo ""
echo "  Stable Diffusion WebUI (image, localhost:7860):"
info "git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui"
info "cd stable-diffusion-webui && ./webui.sh --api"
echo ""
echo "  InvokeAI (image, localhost:9090):"
info "pip install invokeai && invokeai-web"
echo ""
echo "  Real-ESRGAN (image upscale, no GPU needed):"
info "mkdir -p ~/.config/album-organizer/models"
info "curl -L -o ~/.config/album-organizer/models/RealESRGAN_x4plus.onnx \\"
info "  https://github.com/xinntao/Real-ESRGAN/releases/latest/download/RealESRGAN_x4plus.onnx"
echo "----------------------------------------------------------------------"
echo ""

# --- Build -------------------------------------------------------------------
# Select the right JavaFX native profile based on CPU architecture
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    MVN_PROFILE="-P javafx-mac-aarch64"
else
    MVN_PROFILE="-P javafx-mac"
fi

echo "Building JAR..."
mvn package -DskipTests -q $MVN_PROFILE

echo "Building icons..."
ICONSET="target/app-icon.iconset"
rm -rf "$ICONSET" target/app-icon.icns
mkdir -p "$ICONSET"
sips -z 16   16    src/main/resources/app-icon.png --out "$ICONSET/icon_16x16.png"      >/dev/null 2>&1
sips -z 32   32    src/main/resources/app-icon.png --out "$ICONSET/icon_16x16@2x.png"   >/dev/null 2>&1
sips -z 32   32    src/main/resources/app-icon.png --out "$ICONSET/icon_32x32.png"      >/dev/null 2>&1
sips -z 64   64    src/main/resources/app-icon.png --out "$ICONSET/icon_32x32@2x.png"   >/dev/null 2>&1
sips -z 128  128   src/main/resources/app-icon.png --out "$ICONSET/icon_128x128.png"    >/dev/null 2>&1
sips -z 256  256   src/main/resources/app-icon.png --out "$ICONSET/icon_128x128@2x.png" >/dev/null 2>&1
sips -z 256  256   src/main/resources/app-icon.png --out "$ICONSET/icon_256x256.png"    >/dev/null 2>&1
sips -z 512  512   src/main/resources/app-icon.png --out "$ICONSET/icon_256x256@2x.png" >/dev/null 2>&1
sips -z 512  512   src/main/resources/app-icon.png --out "$ICONSET/icon_512x512.png"    >/dev/null 2>&1
sips -z 1024 1024  src/main/resources/app-icon.png --out "$ICONSET/icon_512x512@2x.png" >/dev/null 2>&1
iconutil -c icns "$ICONSET" -o target/app-icon.icns

APP_BUNDLE="target/dist/AlbumOrganizer.app"
DMG_FILE="target/dist/AlbumOrganizer-1.5.0.dmg"

# Collect JavaFX mac-aarch64 jars for module path
FX_MODS_PATH=""
for mod in base graphics controls fxml swing; do
  jar=$(find ~/.m2/repository/org/openjfx/javafx-${mod} -name "*mac-aarch64.jar" 2>/dev/null | sort -V | tail -1)
  [ -n "$jar" ] && FX_MODS_PATH="${FX_MODS_PATH}:${jar}"
done
FX_MODS_PATH="${FX_MODS_PATH#:}"

echo "Building macOS app bundle..."
rm -rf "target/dist"
cp target/album-organizer-1.0.0.jar target/lib/
jpackage \
    --type app-image \
    --name "AlbumOrganizer" \
    --app-version "1.5.0" \
    --module-path "$FX_MODS_PATH" \
    --add-modules javafx.controls,javafx.fxml,javafx.swing,java.net.http,java.desktop,java.naming,java.sql,jdk.crypto.ec \
    --input target/lib \
    --main-jar album-organizer-1.0.0.jar \
    --main-class com.albumorganizer.AlbumOrganizerApp \
    --icon target/app-icon.icns \
    --dest target/dist \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "-Dapple.awt.application.name=AlbumOrganizer" \
    --java-options "-Dapple.laf.useScreenMenuBar=true"
rm target/lib/album-organizer-1.0.0.jar

echo "Building macOS installer (.dmg)..."
jpackage \
    --type dmg \
    --name "AlbumOrganizer" \
    --app-version "1.5.0" \
    --app-image "$APP_BUNDLE" \
    --icon target/app-icon.icns \
    --dest target/dist

echo ""
echo -e "${GREEN}Build complete:${NC}"
echo "  App:       $APP_BUNDLE"
echo "  Installer: $DMG_FILE"
echo ""
echo "Run with: open \"$APP_BUNDLE\""
