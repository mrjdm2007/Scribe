import SwiftUI
import PDFKit
import Vision
import AppKit

struct ScribePDFPreview: NSViewRepresentable {
    let url: URL
    func makeNSView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.document = PDFDocument(url: url)
        return view
    }
    func updateNSView(_ view: PDFView, context: Context) {
        if view.document?.documentURL != url { view.document = PDFDocument(url: url) }
    }
}

enum ScribePDFOCR {
    static func extractText(from url: URL) async throws -> String {
        try await Task.detached(priority: .userInitiated) {
            guard let document = PDFDocument(url: url) else { throw NSError(domain: "ScribePDF", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unable to open PDF"]) }
            var pages: [String] = []
            for index in 0..<document.pageCount {
                guard let page = document.page(at: index) else { continue }
                let thumbnail = page.thumbnail(of: CGSize(width: 1800, height: 2400), for: .mediaBox)
                guard let image = thumbnail.cgImage(forProposedRect: nil, context: nil, hints: nil) else { continue }
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
