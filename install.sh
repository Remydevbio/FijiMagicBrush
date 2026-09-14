#!/bin/sh
set -eu
cd "$(dirname "$0")"
FIJI_DIR=${FIJI_DIR:-/home/rbonnav/Documents/Codex/Fiji}
test -d "$FIJI_DIR/plugins" || { echo "Fiji plugins directory not found" >&2; exit 1; }
test -f dist/Intensity_Selection_Tools.jar || { echo "Run ./build.sh first" >&2; exit 1; }
cp dist/Intensity_Selection_Tools.jar "$FIJI_DIR/plugins/Intensity_Selection_Tools.jar"
echo "Installed. Restart Fiji and select Plugins > Selection Tools > Install Brush and Smart Brush."
