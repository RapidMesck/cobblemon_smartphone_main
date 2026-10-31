#!/usr/bin/env bash
set -e

# Target screen(s) to capture: e.g. "all", "smartphone", "pokeinfo", "social", "settings", "gps", "rotom"
TARGET="${1:-all}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="${PROJECT_ROOT}/build/screenshots"

mkdir -p "$OUTPUT_DIR"

echo "======================================================="
echo " Cobblemon Smartphone GUI Screenshot Capture"
echo " Target:     ${TARGET}"
echo " Output Dir: ${OUTPUT_DIR}"
echo "======================================================="

cd "$PROJECT_ROOT"

# Ensure gradlew is executable
chmod +x ./gradlew

# Setup Xvfb if running headless
if command -v xvfb-run >/dev/null 2>&1; then
    echo "Running headless with xvfb-run..."
    xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew :fabric:runClient \
        -Dsmartphone.screenshot="${TARGET}" \
        -Dsmartphone.screenshot.dir="${OUTPUT_DIR}"
else
    echo "Running directly (xvfb-run not found)..."
    ./gradlew :fabric:runClient \
        -Dsmartphone.screenshot="${TARGET}" \
        -Dsmartphone.screenshot.dir="${OUTPUT_DIR}"
fi

echo ""
echo "======================================================="
echo " Capture finished! Generated images:"
echo "======================================================="
ls -lh "$OUTPUT_DIR"/*.png 2>/dev/null || echo "No screenshots found."
