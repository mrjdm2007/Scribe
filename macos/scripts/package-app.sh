#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
swift build -c release
APP="$ROOT/build/Scribe.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp ".build/release/ScribeMac" "$APP/Contents/MacOS/Scribe"
ICONSET="$ROOT/build/Scribe.iconset"
mkdir -p "$ICONSET"
swift scripts/generate-icon.swift "$ROOT/build/Scribe-1024.png"
for size in 16 32 128 256 512; do
  sips -z "$size" "$size" "$ROOT/build/Scribe-1024.png" --out "$ICONSET/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  sips -z "$double" "$double" "$ROOT/build/Scribe-1024.png" --out "$ICONSET/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns -o "$APP/Contents/Resources/Scribe.icns" "$ICONSET"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleName</key><string>Scribe</string>
<key>CFBundleDisplayName</key><string>Scribe</string>
<key>CFBundleIdentifier</key><string>com.mrjdm.scribe.mac</string>
<key>CFBundleExecutable</key><string>Scribe</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleIconFile</key><string>Scribe.icns</string>
<key>LSMinimumSystemVersion</key><string>13.0</string>
<key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST
cd "$ROOT/build"
ditto -c -k --sequesterRsrc --keepParent Scribe.app Scribe-macOS.zip
