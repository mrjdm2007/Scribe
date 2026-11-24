import AppKit
import Foundation

let canvas = NSSize(width: 1024, height: 1024)
let image = NSImage(size: canvas, flipped: false) { rect in
    let bg = NSBezierPath(roundedRect: rect.insetBy(dx: 28, dy: 28), xRadius: 220, yRadius: 220)
    NSColor(calibratedRed: 0.055, green: 0.065, blue: 0.082, alpha: 1).setFill()
    bg.fill()

    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(0.34)
    shadow.shadowBlurRadius = 30
    shadow.shadowOffset = NSSize(width: 0, height: -16)
    shadow.set()

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
    shadow.set()

    let spine = NSBezierPath(roundedRect: NSRect(x: 495, y: 255, width: 34, height: 430), xRadius: 12, yRadius: 12)
    NSColor(calibratedRed: 0.14, green: 0.16, blue: 0.19, alpha: 1).setFill()
    spine.fill()

    shadow.shadowColor = .clear
    shadow.set()
    let bookmark = NSBezierPath()
    bookmark.move(to: NSPoint(x: 690, y: 735))
    bookmark.line(to: NSPoint(x: 790, y: 735))
    bookmark.line(to: NSPoint(x: 790, y: 555))
    bookmark.line(to: NSPoint(x: 740, y: 590))
    bookmark.line(to: NSPoint(x: 690, y: 555))
    bookmark.close()
    NSColor(calibratedRed: 0.73, green: 0.59, blue: 0.38, alpha: 1).setFill()
    bookmark.fill()

    NSColor(calibratedRed: 0.58, green: 0.50, blue: 0.38, alpha: 0.88).setStroke()
    for y in [575.0, 500.0, 425.0] {
        let a = NSBezierPath()
        a.lineWidth = 10
        a.lineCapStyle = .round
        a.move(to: NSPoint(x: 245, y: y))
        a.line(to: NSPoint(x: 430, y: y + 20))
        a.stroke()
        let b = NSBezierPath()
        b.lineWidth = 10
        b.lineCapStyle = .round
        b.move(to: NSPoint(x: 590, y: y + 20))
        b.line(to: NSPoint(x: 775, y: y))
        b.stroke()
    }

    let glint = NSBezierPath()
    glint.lineWidth = 5
    glint.lineCapStyle = .round
    NSColor.white.withAlphaComponent(0.24).setStroke()
    glint.move(to: NSPoint(x: 150, y: 820))
    glint.curve(to: NSPoint(x: 820, y: 820), controlPoint1: NSPoint(x: 340, y: 900), controlPoint2: NSPoint(x: 680, y: 900))
    glint.stroke()
    return true
}

let output = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: output.deletingLastPathComponent(), withIntermediateDirectories: true)
guard let tiff = image.tiffRepresentation, let bitmap = NSBitmapImageRep(data: tiff),
      let png = bitmap.representation(using: .png, properties: [:]) else {
    fatalError("Could not render Scribe icon")
}
try png.write(to: output)
