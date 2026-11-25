import SwiftUI
import PDFKit
import Vision
import AppKit

struct ScribePDFWorkspace: View {
    let url: URL
    @State private var annotationText = ""
    @State private var command = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScribePDFPreview(url: url, annotationText: annotationText, annotationCommand: command)
                .frame(minHeight: 340)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
            HStack(spacing: 8) {
                Button {
                    command = "highlight|" + UUID().uuidString
                } label: { Label("Highlight selection", systemImage: "highlighter") }
                TextField("Note annotation text", text: $annotationText)
                    .textFieldStyle(.roundedBorder)
                Button {
                    guard !annotationText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                    command = "note|" + UUID().uuidString
                } label: { Label("Add note", systemImage: "note.text") }
            }
            Text("Select text in the PDF before highlighting. Annotations are written into the PDF file.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }
}

struct ScribePDFPreview: NSViewRepresentable {
    let url: URL
    var annotationText: String = ""
    var annotationCommand: String = ""

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeNSView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.document = PDFDocument(url: url)
        context.coordinator.url = url
        return view
    }

    func updateNSView(_ view: PDFView, context: Context) {
        if view.document?.documentURL != url {
            view.document = PDFDocument(url: url)
            context.coordinator.url = url
        }
        guard !annotationCommand.isEmpty,
              annotationCommand != context.coordinator.lastCommand else { return }
        context.coordinator.lastCommand = annotationCommand
        let command = annotationCommand.components(separatedBy: "|").first ?? ""
        switch command {
        case "highlight":
            context.coordinator.highlightSelection(in: view)
        case "note":
            context.coordinator.addNote(annotationText, in: view)
        default:
            break
        }
    }

    final class Coordinator {
        var url: URL?
        var lastCommand = ""

        func highlightSelection(in view: PDFView) {
            guard let document = view.document,
                  let selection = view.currentSelection else { return }
            let lineSelections = selection.selectionsByLine()
            guard !lineSelections.isEmpty else { return }
            for line in lineSelections {
                for page in line.pages {
                    let bounds = line.bounds(for: page).insetBy(dx: -1, dy: -1)
                    guard bounds.width > 0, bounds.height > 0 else { continue }
                    let annotation = PDFAnnotation(bounds: bounds, forType: .highlight, withProperties: nil)
                    annotation.color = NSColor.systemYellow.withAlphaComponent(0.48)
                    page.addAnnotation(annotation)
                }
            }
            if let destination = url { _ = document.write(to: destination) }
        }

        func addNote(_ text: String, in view: PDFView) {
            guard let document = view.document,
                  let page = view.currentPage ?? document.page(at: 0) else { return }
            let pageBounds = page.bounds(for: .mediaBox)
            let width = min(220, max(100, pageBounds.width - 40))
            let height: CGFloat = 72
            let bounds = CGRect(x: pageBounds.minX + 20, y: pageBounds.minY + 20, width: width, height: height)
            let annotation = PDFAnnotation(bounds: bounds, forType: .freeText, withProperties: nil)
            annotation.contents = text
            annotation.font = NSFont.systemFont(ofSize: 11)
            annotation.fontColor = .labelColor
            annotation.color = NSColor.systemYellow.withAlphaComponent(0.18)
            page.addAnnotation(annotation)
            document.write(to: url ?? document.documentURL ?? URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("Scribe-annotations.pdf"))
        }
    }
}

enum ScribePDFOCR {
    static func extractText(from url: URL) async throws -> String {
        try await Task.detached(priority: .userInitiated) {
            guard let document = PDFDocument(url: url) else {
                throw NSError(domain: "ScribePDF", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Unable to open PDF"])
            }
            var pages: [String] = []
            for index in 0..<document.pageCount {
                guard let page = document.page(at: index) else { continue }
                if let selectableText = page.string, !selectableText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    pages.append(selectableText)
                    continue
                }
                let thumbnail = page.thumbnail(of: CGSize(width: 1800, height: 2400), for: .mediaBox)
                guard let image = thumbnail.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
                    pages.append("")
                    continue
                }
                let request = VNRecognizeTextRequest()
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = true
                try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
                let lines = (request.results ?? []).sorted { $0.boundingBox.midY > $1.boundingBox.midY }
                    .compactMap { $0.topCandidates(1).first?.string }
                pages.append(lines.joined(separator: "\n"))
            }
            return pages.enumerated().map { "Page \($0.offset + 1)\n\($0.element)" }.joined(separator: "\n\n")
        }.value
    }
}
