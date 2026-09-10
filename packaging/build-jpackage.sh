#!/usr/bin/env bash
# Builds the shaded jSerialComm-bundled jar with Maven and packages it with jpackage.
#
# jpackage cannot cross-package for other operating systems; run this script on Linux
# or macOS to produce the installer for that OS. Run build-jpackage.ps1 on Windows for
# that target. The jar built by the maven-shade-plugin already bundles jSerialComm's
# native libraries for all platforms, so no per-arch handling is needed here.
#
# Usage:
#   ./packaging/build-jpackage.sh            # creates a portable app image
#   ./packaging/build-jpackage.sh rpm         # Linux only
#   ./packaging/build-jpackage.sh pkg         # macOS only
#   ./packaging/build-jpackage.sh app-image   # any OS

set -euo pipefail

APP_NAME="TouchDRO Desktop"
VENDOR_NAME="DRO Desktop Project"
REQUESTED_TYPE="${1:-}"

if ! command -v jpackage >/dev/null 2>&1; then
    echo "jpackage not found on PATH. Install a JDK 17+ that includes jpackage and add its bin directory to PATH." >&2
    exit 1
fi
if ! command -v jlink >/dev/null 2>&1; then
    echo "jlink not found on PATH. Install a JDK 17+ that includes jlink and add its bin directory to PATH." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

OS_NAME="$(uname -s)"
case "$OS_NAME" in
    Linux*|Darwin*) DEFAULT_TYPE="app-image" ;;
    *) echo "Unsupported OS for this script: $OS_NAME" >&2; exit 1 ;;
esac
TYPE="${REQUESTED_TYPE:-$DEFAULT_TYPE}"

echo "==> Building shaded jar with Maven..."
mvn -q -Dmaven.test.skip=true clean package

ARTIFACT_ID="$(sed -n 's:.*<artifactId>\(.*\)</artifactId>.*:\1:p' pom.xml | head -1)"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"
JAR_NAME="${ARTIFACT_ID}-${VERSION}-allplatforms.jar"
JAR_PATH="target/${JAR_NAME}"

if [ ! -f "$JAR_PATH" ]; then
    echo "Jar not found at $JAR_PATH" >&2
    exit 1
fi

OUT_DIR="target/jpackage"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

RUNTIME_DIR="target/runtime"
rm -rf "$RUNTIME_DIR"
jlink \
    --add-modules java.desktop,java.prefs,jdk.unsupported \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --output "$RUNTIME_DIR"

# jpackage requires a purely numeric app-version, strip pre-release suffixes like "-beta".
APP_VERSION="${VERSION%%-*}"

ICON_ARGS=()
if [ "$OS_NAME" = "Darwin" ] && [ -f "assets/touchdro.icns" ]; then
    ICON_ARGS=(--icon "assets/touchdro.icns")
elif [ "$OS_NAME" = "Linux" ] && [ -f "assets/touchdro.png" ]; then
    ICON_ARGS=(--icon "assets/touchdro.png")
else
    echo "Warning: no platform icon found in assets/, packaging without a custom icon." >&2
fi

echo "==> Running jpackage ($TYPE) for $OS_NAME..."
jpackage \
    --type "$TYPE" \
    --input target \
    --dest "$OUT_DIR" \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "$VENDOR_NAME" \
    --main-jar "$JAR_NAME" \
    --main-class com.drodesktop.Main \
    --runtime-image "$RUNTIME_DIR" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    "${ICON_ARGS[@]}"

echo "==> Done. Output in $OUT_DIR"
