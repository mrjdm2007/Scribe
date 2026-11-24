#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
swift build -c release
APP="$ROOT/build/Scribe.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp ".build/release/ScribeMac" "$APP/Contents/MacOS/Scribe"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleName</key><string>Scribe</string>
<key>CFBundleDisplayName</key><string>Scribe</string>
<key>CFBundleIdentifier</key><string>com.mrjdm.scribe.mac</string>
<key>CFBundleExecutable</key><string>Scribe</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>LSMinimumSystemVersion</key><string>13.0</string>
<key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST
cd "$ROOT/build"
ditto -c -k --sequesterRsrc --keepParent Scribe.app Scribe-macOS.zip
