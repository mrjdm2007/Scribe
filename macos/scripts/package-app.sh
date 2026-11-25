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
cat > "$ROOT/build/generate-icon.swift" <<'SWIFT'
import AppKit
import Foundation
let image = NSImage(size: NSSize(width: 1024, height: 1024), flipped: false) { rect in
    NSColor(calibratedRed: 0.055, green: 0.065, blue: 0.082, alpha: 1).setFill()
    NSBezierPath(roundedRect: rect.insetBy(dx: 28, dy: 28), xRadius: 220, yRadius: 220).fill()
    let shadow = NSBezierPath(roundedRect: NSRect(x: 224, y: 158, width: 590, height: 710), xRadius: 76, yRadius: 76)
    NSColor.black.withAlphaComponent(0.28).setFill(); shadow.fill()
    let cover = NSBezierPath(roundedRect: NSRect(x: 210, y: 176, width: 590, height: 650), xRadius: 62, yRadius: 62)
    NSColor(calibratedRed: 0.95, green: 0.92, blue: 0.85, alpha: 1).setFill(); cover.fill()
    let rightPage = NSBezierPath(roundedRect: NSRect(x: 520, y: 190, width: 266, height: 622), xRadius: 48, yRadius: 48)
    NSColor(calibratedRed: 0.86, green: 0.80, blue: 0.68, alpha: 1).setFill(); rightPage.fill()
    let spine = NSBezierPath(roundedRect: NSRect(x: 492, y: 176, width: 42, height: 650), xRadius: 18, yRadius: 18)
    NSColor(calibratedRed: 0.13, green: 0.15, blue: 0.18, alpha: 1).setFill(); spine.fill()
    let bookmark = NSBezierPath()
    bookmark.move(to: NSPoint(x: 680, y: 826)); bookmark.line(to: NSPoint(x: 770, y: 826))
    bookmark.line(to: NSPoint(x: 770, y: 640)); bookmark.line(to: NSPoint(x: 725, y: 674))
    bookmark.line(to: NSPoint(x: 680, y: 640)); bookmark.close()
    NSColor(calibratedRed: 0.73, green: 0.59, blue: 0.38, alpha: 1).setFill(); bookmark.fill()
    NSColor(calibratedRed: 0.62, green: 0.56, blue: 0.45, alpha: 0.85).setStroke()
    for (x, y, width) in [(292.0, 650.0, 148.0), (292.0, 550.0, 148.0), (292.0, 450.0, 148.0),
                          (580.0, 550.0, 150.0), (580.0, 450.0, 150.0), (580.0, 350.0, 150.0)] {
        let line = NSBezierPath(); line.lineWidth = 13; line.lineCapStyle = .round
        line.move(to: NSPoint(x: x, y: y)); line.line(to: NSPoint(x: x + width, y: y)); line.stroke()
    }
    NSColor.white.withAlphaComponent(0.22).setStroke()
    let reflection = NSBezierPath(); reflection.lineWidth = 7; reflection.lineCapStyle = .round
    reflection.move(to: NSPoint(x: 270, y: 780))
    reflection.curve(to: NSPoint(x: 745, y: 780), controlPoint1: NSPoint(x: 390, y: 850), controlPoint2: NSPoint(x: 620, y: 850))
    reflection.stroke()
    return true
}
let output = URL(fileURLWithPath: CommandLine.arguments[1])
guard let tiff = image.tiffRepresentation, let bitmap = NSBitmapImageRep(data: tiff),
      let png = bitmap.representation(using: .png, properties: [:]) else { fatalError("Icon render failed") }
try png.write(to: output)
SWIFT
swift "$ROOT/build/generate-icon.swift" "$ROOT/build/Scribe-1024.png"
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
