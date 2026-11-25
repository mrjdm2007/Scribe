import SwiftUI
import PDFKit
import Vision
import AppKit

struct PDFReadOnlyView: View {
    let url: URL
    @State private var document: PDFDocument?
    @State private var pageIndex = 0
    @State private var extractedText = ""
    @State private var searchText = ""
    @State private var searchMatches: [PDFSelection] = []
    @State private var searchIndex = 0
    @State private var isRecognizing = false
    @State private var errorMessage: String?

    init(url: URL) {
        self.url = url
        _document = State(initialValue: PDFDocument(url: url))
    }

    private var pageCount: Int { document?.pageCount ?? 0 }
    private var visibleText: String {
        guard !searchText.isEmpty else { return extractedText }
        let matches = extractedText.components(separatedBy: .newlines).filter { $0.localizedCaseInsensitiveContains(searchText) }
        return matches.isEmpty ? "No matching text found on this page." : matches.joined(separator: "\n")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("PDF · Read only", systemImage: "doc.richtext").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
                Text(pageCount > 0 ? "Page \(pageIndex + 1) of \(pageCount)" : "PDF unavailable")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if let document {
                PDFPageHost(document: document, pageIndex: $pageIndex)
                    .frame(minHeight: 460)
                    .background(.quaternary.opacity(0.2), in: RoundedRectangle(cornerRadius: 10))
                HStack {
                    Button("Previous") { pageIndex = max(0, pageIndex - 1) }.disabled(pageIndex == 0)
                    Button("Next") { pageIndex = min(pageCount - 1, pageIndex + 1) }.disabled(pageIndex >= pageCount - 1)
                    Spacer()
                    Button {
                        recognizeCurrentPage()
                    } label: {
                        if isRecognizing { ProgressView().controlSize(.small) }
                        else { Label("Extract text from page", systemImage: "text.viewfinder") }
                    }.disabled(isRecognizing || document.page(at: pageIndex) == nil)
                }
                HStack {
                    TextField("Search the entire PDF", text: $searchText)
                        .textFieldStyle(.roundedBorder)
                    Button("Find") { searchDocument() }
                        .disabled(searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if !searchMatches.isEmpty {
                        Text("\(searchIndex + 1) of \(searchMatches.count)")
                            .font(.caption).foregroundStyle(.secondary)
                        Button { moveSearchResult(-1) } label: { Image(systemName: "chevron.left") }
                            .disabled(searchIndex == 0)
                        Button { moveSearchResult(1) } label: { Image(systemName: "chevron.right") }
                            .disabled(searchIndex >= searchMatches.count - 1)
                    }
                }
                if let errorMessage { Text(errorMessage).font(.caption).foregroundStyle(.red) }
                if !extractedText.isEmpty {
                    Text("TEXT EXTRACTED FROM THIS PAGE").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    ScrollView {
                        Text(visibleText).frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled).padding(10)
                    }.frame(maxHeight: 180).background(.quaternary.opacity(0.2), in: RoundedRectangle(cornerRadius: 10))
                }
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "doc.questionmark").font(.largeTitle).foregroundStyle(.secondary)
                    Text("Unable to open PDF").font(.headline)
                }
            }
        }
        .onChange(of: pageIndex) { _ in extractedText = ""; errorMessage = nil }
    }

    private func searchDocument() {
        guard let document, !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        searchMatches = document.findString(searchText, withOptions: [.caseInsensitive])
        searchIndex = 0
        if let first = searchMatches.first, let page = first.pages.first {
            pageIndex = document.index(for: page)
        }
        if searchMatches.isEmpty { errorMessage = "No matches found in this PDF." }
        else { errorMessage = nil }
    }

    private func moveSearchResult(_ offset: Int) {
        let next = min(max(searchIndex + offset, 0), searchMatches.count - 1)
        searchIndex = next
        guard let page = searchMatches[next].pages.first, let document else { return }
        pageIndex = document.index(for: page)
    }

    private func recognizeCurrentPage() {
        guard let page = document?.page(at: pageIndex) else { return }
        if let nativeText = page.string, !nativeText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            extractedText = nativeText
            errorMessage = nil
            return
        }
        let image = page.thumbnail(of: CGSize(width: 1800, height: 1800), for: .mediaBox)
        var rect = CGRect(origin: .zero, size: image.size)
        guard let cgImage = image.cgImage(forProposedRect: &rect, context: nil, hints: nil) else {
            errorMessage = "This PDF page could not be rendered for text extraction."
            return
        }
        isRecognizing = true
        errorMessage = nil
        DispatchQueue.global(qos: .userInitiated).async {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            do {
                try VNImageRequestHandler(cgImage: cgImage).perform([request])
                let result = (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
                DispatchQueue.main.async {
                    extractedText = result.isEmpty ? "No text was detected on this page." : result
                    isRecognizing = false
                }
            } catch {
                DispatchQueue.main.async {
                    errorMessage = error.localizedDescription
                    isRecognizing = false
                }
            }
        }
    }
}

private struct PDFPageHost: NSViewRepresentable {
    let document: PDFDocument
    @Binding var pageIndex: Int

    func makeNSView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.document = document
        return view
    }

    func updateNSView(_ view: PDFView, context: Context) {
        if view.document !== document { view.document = document }
        if let page = document.page(at: pageIndex), view.currentPage !== page { view.go(to: page) }
    }
}
