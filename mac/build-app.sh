#!/bin/bash
# Builds AC Remote.app (menu bar only, no Dock icon) into mac/dist/.
set -euo pipefail
cd "$(dirname "$0")"

swift build -c release --product ACRemote
BIN="$(swift build -c release --show-bin-path)/ACRemote"

APP="dist/AC Remote.app"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
cp "$BIN" "$APP/Contents/MacOS/ACRemote"

cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>AC Remote</string>
    <key>CFBundleDisplayName</key><string>AC Remote</string>
    <key>CFBundleIdentifier</key><string>dev.adityadroid.acremote</string>
    <key>CFBundleExecutable</key><string>ACRemote</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>LSMinimumSystemVersion</key><string>14.0</string>
    <key>LSUIElement</key><true/>
    <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST

codesign --force --sign - "$APP"
echo "Built $APP"
