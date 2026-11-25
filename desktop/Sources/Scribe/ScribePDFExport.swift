import SwiftUI
import AppKit
import PDFKit
import UniformTypeIdentifiers

struct ScribePDFDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.pdf] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

func makeScribePDF(title: String, blocks: [ScribeNode]) throws -> Data {
    let linesPerPage = 40
    var lines: [(String, Bool)] = [(title, true), ("Exported from Scribe · " + Date.now.formatted(date: .abbreviated, time: .shortened), false), ("", false)]
    for block in blocks {
        lines.append((block.title, true))
        let content = block.body.isEmpty ? (block.uri.isEmpty ? "" : URL(fileURLWithPath: block.uri).lastPathComponent) : block.body
        for paragraph in content.components(separatedBy: .newlines) {
            if paragraph.isEmpty { lines.append(("", false)); continue }
            var remaining = paragraph[...]
            while !remaining.isEmpty {
                let cut = remaining.index(remaining.startIndex, offsetBy: min(88, remaining.count))
                lines.append((String(remaining[..<cut]), false))
                remaining = remaining[cut...]
            }
        }
        lines.append(("", false))
    }

    let document = PDFDocument()
    for start in stride(from: 0, to: max(lines.count, 1), by: linesPerPage) {
        let pageLines = Array(lines[start..<min(start + linesPerPage, lines.count)])
        let width = 1190, height = 1684
        guard let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height,
                                            bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
                                            isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0),
              let graphics = NSGraphicsContext(bitmapImageRep: bitmap) else {
            throw CocoaError(.fileWriteUnknown)
        }
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = graphics
        NSColor.white.setFill()
        NSBezierPath(rect: NSRect(x: 0, y: 0, width: 595, height: 842)).fill()
        var y: CGFloat = 790
        for (text, isHeading) in pageLines {
            let font = isHeading
                ? NSFont.systemFont(ofSize: text == title && start == 0 ? 22 : 14, weight: .semibold)
                : NSFont.systemFont(ofSize: 10)
            let color = isHeading ? NSColor.black : NSColor.darkGray
            (text as NSString).draw(at: NSPoint(x: 42, y: y), withAttributes: [.font: font, .foregroundColor: color])
            y -= isHeading ? 25 : 17
        }
        graphics.flushGraphics()
        NSGraphicsContext.restoreGraphicsState()
        let image = NSImage(size: NSSize(width: 595, height: 842))
        image.addRepresentation(bitmap)
        guard let page = PDFPage(image: image) else { throw CocoaError(.fileWriteUnknown) }
        document.insert(page, at: document.pageCount)
    }
    guard let data = document.dataRepresentation() else { throw CocoaError(.fileWriteUnknown) }
    return data
}
