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
    let left = NSBezierPath()
    left.move(to: NSPoint(x: 170, y: 700))
    left.curve(to: NSPoint(x: 500, y: 650), controlPoint1: NSPoint(x: 270, y: 770), controlPoint2: NSPoint(x: 410, y: 755))
    left.line(to: NSPoint(x: 500, y: 265))
    left.curve(to: NSPoint(x: 170, y: 330), controlPoint1: NSPoint(x: 390, y: 310), controlPoint2: NSPoint(x: 275, y: 260))
    left.close()
    NSColor(calibratedRed: 0.95, green: 0.90, blue: 0.81, alpha: 1).setFill()
    left.fill()
    let right = NSBezierPath()
    right.move(to: NSPoint(x: 524, y: 650))
    right.curve(to: NSPoint(x: 854, y: 700), controlPoint1: NSPoint(x: 614, y: 755), controlPoint2: NSPoint(x: 754, y: 770))
    right.line(to: NSPoint(x: 854, y: 330))
    right.curve(to: NSPoint(x: 524, y: 265), controlPoint1: NSPoint(x: 749, y: 260), controlPoint2: NSPoint(x: 634, y: 310))
    right.close()
    NSColor(calibratedRed: 0.85, green: 0.78, blue: 0.65, alpha: 1).setFill()
    right.fill()
    let spine = NSBezierPath(roundedRect: NSRect(x: 495, y: 255, width: 34, height: 430), xRadius: 12, yRadius: 12)
    NSColor(calibratedRed: 0.14, green: 0.16, blue: 0.19, alpha: 1).setFill()
    spine.fill()
    let bookmark = NSBezierPath()
    bookmark.move(to: NSPoint(x: 690, y: 735)); bookmark.line(to: NSPoint(x: 790, y: 735))
    bookmark.line(to: NSPoint(x: 790, y: 555)); bookmark.line(to: NSPoint(x: 740, y: 590))
    bookmark.line(to: NSPoint(x: 690, y: 555)); bookmark.close()
    NSColor(calibratedRed: 0.73, green: 0.59, blue: 0.38, alpha: 1).setFill()
    bookmark.fill()
    NSColor(calibratedRed: 0.58, green: 0.50, blue: 0.38, alpha: 0.88).setStroke()
    for y in [575.0, 500.0, 425.0] {
        for pair in [(245.0, 430.0, y, y + 20), (590.0, 775.0, y + 20, y)] {
            let line = NSBezierPath(); line.lineWidth = 10; line.lineCapStyle = .round
            line.move(to: NSPoint(x: CGFloat(pair.0), y: CGFloat(pair.2))); line.line(to: NSPoint(x: CGFloat(pair.1), y: CGFloat(pair.3))); line.stroke()
        }
    }
    NSColor.white.withAlphaComponent(0.20).setStroke()
    let glint = NSBezierPath(); glint.lineWidth = 5; glint.lineCapStyle = .round
    glint.move(to: NSPoint(x: 150, y: 820))
    glint.curve(to: NSPoint(x: 820, y: 820), controlPoint1: NSPoint(x: 340, y: 900), controlPoint2: NSPoint(x: 680, y: 900))
    glint.stroke()
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
