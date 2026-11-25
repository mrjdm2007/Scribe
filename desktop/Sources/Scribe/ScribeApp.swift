import SwiftUI
import UniformTypeIdentifiers
import AppKit
import PDFKit

@main
struct ScribeApp: App {
    @StateObject private var library = ScribeLibrary()
    var body: some Scene {
        WindowGroup { ScribeRootView().environmentObject(library).frame(minWidth: 980, minHeight: 680) }
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("New Course") { _ = library.add(kind: "course") }.keyboardShortcut("n", modifiers: [.command, .shift])
            }
        }
    }
}

struct ScribeRootView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @State private var selectedID: UUID?
    @State private var section = "Library"
    @State private var query = ""
    @State private var showImporter = false
    @State private var showBackupExporter = false
    @State private var showBackupImporter = false
    @State private var backupDocument = ScribeBackupDocument(data: Data())

    private var selected: ScribeNode? { selectedID.flatMap(library.node) }
    private var displayed: [ScribeNode] {
        switch section {
        case "Bookmarks": return library.bookmarks
        case "Trash": return library.trash
        case "Search": return library.search(query)
        default: return library.courses
        }
    }

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                Picker("Section", selection: $section) {
                    Text("Library").tag("Library")
                    Text("Bookmarks").tag("Bookmarks")
                    Text("Trash").tag("Trash")
                    Text("Tools").tag("Tools")
                    Text("Study").tag("Study")
                }
                .pickerStyle(.segmented).padding(12)
                if section == "Search" || !query.isEmpty {
                    List(library.search(query), selection: $selectedID) { item in
                        NavigationLink(value: item.id) { Label(item.title, systemImage: symbol(item.kind)) }
                    }
                } else if section == "Library" {
                    List(selection: $selectedID) {
                        ForEach(library.courses) { item in
                            OutlineNodeRow(item: item, selectedID: $selectedID)
                        }
                    }
                    .listStyle(.sidebar)
                } else if section == "Bookmarks" {
                    List(library.bookmarks, selection: $selectedID) { item in
                        NavigationLink(value: item.id) { Label(item.title, systemImage: symbol(item.kind)) }
                    }
                } else if section == "Trash" {
                    List(library.trash, selection: $selectedID) { item in
                        NavigationLink(value: item.id) { Label(item.title, systemImage: "trash") }
                    }
                } else {
                    List {
                        Label("Engineering tools", systemImage: "function")
                        Label("Unit converter", systemImage: "arrow.left.arrow.right")
                        Label("Formula shelf", systemImage: "sum")
                        Label("Focus timer", systemImage: "timer")
                        Label("Flashcards & question bank", systemImage: "rectangle.stack")
                    }
                }
            }
            .navigationTitle("Scribe")
            .searchable(text: $query, prompt: "Search notes")
            .toolbar {
                ToolbarItemGroup {
                    Button { _ = library.add(kind: "course") } label: { Image(systemName: "plus") }.help("New course")
                    Menu {
                        Button("Export backup…") { exportBackup() }
                        Button("Import backup…") { showBackupImporter = true }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
        } detail: {
            if section == "Tools" {
                DesktopToolsView()
            } else if section == "Study" {
                DesktopStudyView()
            } else if let item = selected {
                NodeEditorView(item: item)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "books.vertical").font(.system(size: 38)).foregroundStyle(.secondary)
                    Text("Your notes, organised.").font(.title2.weight(.semibold))
                    Text("Choose a notebook item or create a course to begin.").foregroundStyle(.secondary)
                }
            }
        }
        .background(.regularMaterial)
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.item, .pdf, .image], allowsMultipleSelection: true) { result in
            guard let selected, selected.kind == "page", case .success(let urls) = result else { return }
            for url in urls { do { try library.importAttachment(from: url, parent: selected.id) } catch { library.lastError = error.localizedDescription } }
        }
        .fileImporter(isPresented: $showBackupImporter, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
            do { if case .success(let urls) = result, let url = urls.first { let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }; try library.restoreBackup(Data(contentsOf: url)) } }
            catch { library.lastError = "Import failed: \(error.localizedDescription)" }
        }
        .fileExporter(isPresented: $showBackupExporter, document: backupDocument, contentType: .json, defaultFilename: "Scribe-Backup", onCompletion: { result in
            if case .failure(let error) = result { library.lastError = "Export failed: \(error.localizedDescription)" }
        })
        .alert("Scribe", isPresented: Binding(get: { library.lastError != nil }, set: { if !$0 { library.lastError = nil } })) {
            Button("OK") { library.lastError = nil }
        } message: { Text(library.lastError ?? "") }
    }

    private func exportBackup() {
        do {
            let data = try library.backupData()
            backupDocument = ScribeBackupDocument(data: data)
            showBackupExporter = true
        } catch { library.lastError = error.localizedDescription }
    }
}

struct OutlineNodeRow: View {
    @EnvironmentObject private var library: ScribeLibrary
    let item: ScribeNode
    @Binding var selectedID: UUID?
    var body: some View {
        if library.children(of: item.id).isEmpty {
            NavigationLink(value: item.id) { Label(item.title, systemImage: symbol(item.kind)) }
        } else {
            DisclosureGroup {
                ForEach(library.children(of: item.id)) { child in
                    OutlineNodeRow(item: child, selectedID: $selectedID)
                }
            } label: {
                NavigationLink(value: item.id) { Label(item.title, systemImage: symbol(item.kind)) }
                    .contextMenu { Button("Duplicate") { _ = library.duplicate(item.id) }; Button("Move to Trash", role: .destructive) { library.moveToTrash(item.id) } }
            }
        }
    }
}

struct NodeEditorView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @State var item: ScribeNode
    @State private var showAttachmentPicker = false
    @State private var showHistory = false
    private func wrapBody(_ marker: String) {
        guard !item.body.isEmpty else { item.body = "(marker)text(marker)"; library.update(item); return }
        item.body = "(marker)(item.body)(marker)"
        library.update(item)
    }
    private func prefixBody(_ prefix: String) {
        item.body = item.body.split(separator: "\n", omittingEmptySubsequences: false)
            .map { prefix + $0 }.joined(separator: "\n")
        library.update(item)
    }

    private var childKind: String? {
        switch item.kind { case "course": return "chapter"; case "chapter": return "topic"; case "topic": return "page"; case "page": return "block"; default: return nil }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label(item.kind.capitalized, systemImage: symbol(item.kind)).foregroundStyle(.secondary)
                Spacer()
                Button { library.toggleBookmark(item.id); item.bookmarked.toggle() } label: { Image(systemName: item.bookmarked ? "bookmark.fill" : "bookmark") }
                Menu {
                    Button("Move up") { library.move(item.id, offset: -1) }
                    Button("Move down") { library.move(item.id, offset: 1) }
                    Button("Duplicate") { _ = library.duplicate(item.id) }
                    Button("Version history") { showHistory = true }
                    if item.kind == "page" {
                        Button("Split page at middle") { library.splitPage(item.id) }
                        Button("Merge with next page") { library.mergePageWithNext(item.id) }
                    }
                    Divider()
                    Button("Move to Trash", role: .destructive) { library.moveToTrash(item.id) }
                } label: { Image(systemName: "ellipsis.circle") }
            }
            TextField("Title", text: $item.title).font(.largeTitle.weight(.semibold)).textFieldStyle(.plain)
                .onChange(of: item.title) { _ in library.update(item) }
            if item.kind == "block" || item.kind == "formula" || item.kind == "question" || item.kind == "flashcard" || item.kind == "diagram" {
                if item.kind == "block" {
                    HStack(spacing: 8) {
                        Button("Bold") { wrapBody("**") }
                        Button("Italic") { wrapBody("*") }
                        Button("Strike") { wrapBody("~~") }
                        Button("Heading") { prefixBody("# ") }
                        Button("Bullet") { prefixBody("- ") }
                        Button("Quote") { prefixBody("> ") }
                        Button("Code") { wrapBody("`") }
                    }.buttonStyle(.bordered).controlSize(.small)
                }
                TextEditor(text: $item.body).font(.body).scrollContentBackground(.hidden)
                    .padding(10).frame(minHeight: 220)
                    .background(.quaternary.opacity(0.35), in: RoundedRectangle(cornerRadius: 14))
                    .onChange(of: item.body) { _ in library.update(item) }
                if item.kind == "block" && !item.body.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("FORMATTED PREVIEW").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        Text((try? AttributedString(markdown: item.body)) ?? AttributedString(item.body))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12).background(.quaternary.opacity(0.2), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
            } else {
                Text("Notebook contents").font(.headline).foregroundStyle(.secondary)
                List(library.children(of: item.id)) { child in
                    NavigationLink(value: child.id) { Label(child.title, systemImage: symbol(child.kind)) }
                }
                .frame(minHeight: 160)
            }
            HStack {
                if let childKind {
                    Button { _ = library.add(kind: childKind, parent: item.id) } label: { Label("Add \(childKind.capitalized)", systemImage: "plus") }.buttonStyle(.borderedProminent)
                }
                if item.kind == "page" {
                    Button { showAttachmentPicker = true } label: { Label("Attach file", systemImage: "paperclip") }
                }
                Spacer()
                Text("Updated \(item.updatedAt.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(.tertiary)
            }
            if !item.uri.isEmpty {
                HStack { Image(systemName: "paperclip"); Text(URL(fileURLWithPath: item.uri).lastPathComponent); Spacer(); Button("Reveal") { NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: item.uri)]) } }
                    .font(.caption)
                if URL(fileURLWithPath: item.uri).pathExtension.lowercased() == "pdf" {
                    PDFPreview(url: URL(fileURLWithPath: item.uri)).frame(minHeight: 420)
                    Button("Extract PDF text into this note") {
                        if let document = PDFDocument(url: URL(fileURLWithPath: item.uri)), let text = document.string {
                            item.body += (item.body.isEmpty ? "" : "\\n\\n") + text
                            library.update(item)
                        }
                    }
                } else if let image = NSImage(contentsOfFile: item.uri) {
                    Image(nsImage: image).resizable().scaledToFit().frame(maxHeight: 360)
                }
            }
            Spacer()
        }
        .padding(28)
        .fileImporter(isPresented: $showAttachmentPicker, allowedContentTypes: [.item, .pdf, .image], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result { for url in urls { do { try library.importAttachment(from: url, parent: item.id) } catch { library.lastError = error.localizedDescription } } }
        }
        .sheet(isPresented: $showHistory) {
            NavigationStack {
                List(library.history(for: item.id)) { snap in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(snap.title).font(.headline)
                        Text(snap.body).lineLimit(4).foregroundStyle(.secondary)
                        Text(snap.savedAt.formatted()).font(.caption).foregroundStyle(.tertiary)
                    }
                }.navigationTitle("Version history").toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showHistory = false } } }
            }.frame(minWidth: 480, minHeight: 360)
        }
    }
}

struct PDFPreview: NSViewRepresentable {
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

struct DesktopToolsView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @State private var tool = "Converter"
    @State private var value = "1"
    @State private var from = "m"
    @State private var to = "cm"
    @State private var formula = "sin(x)"
    @State private var label = "New formula"
    @State private var question = ""
    @State private var answer = ""
    @State private var cardFront = ""
    @State private var cardBack = ""
    @State private var tableRows = "3"
    @State private var tableColumns = "3"
    @State private var graphRange = 6.28
    private let units: [String: Double] = ["m":1,"cm":0.01,"mm":0.001,"km":1000,"V":1,"mV":0.001,"kV":1000,"A":1,"mA":0.001,"µA":0.000001,"Hz":1,"kHz":1000,"MHz":1_000_000,"GHz":1_000_000_000,"W":1,"mW":0.001,"kW":1000,"s":1,"ms":0.001,"min":60,"h":3600,"Ω":1,"kΩ":1000,"MΩ":1_000_000,"F":1,"µF":0.000001,"nF":0.000000001,"pF":0.000000000001,"J":1,"kJ":1000,"Wh":3600,"kWh":3600000,"m/s":1,"km/h":1.0/3.6,"C":1,"mC":0.001,"µC":0.000001,"H":1,"mH":0.001,"µH":0.000001]
    private let unitDimensions: [String: String] = [
        "m":"length","cm":"length","mm":"length","km":"length",
        "V":"voltage","mV":"voltage","kV":"voltage",
        "A":"current","mA":"current","µA":"current",
        "Hz":"frequency","kHz":"frequency","MHz":"frequency","GHz":"frequency",
        "W":"power","mW":"power","kW":"power",
        "s":"time","ms":"time","min":"time","h":"time",
        "Ω":"resistance","kΩ":"resistance","MΩ":"resistance",
        "F":"capacitance","µF":"capacitance","nF":"capacitance","pF":"capacitance",
        "J":"energy","kJ":"energy","Wh":"energy","kWh":"energy",
        "m/s":"speed","km/h":"speed",
        "C":"charge","mC":"charge","µC":"charge",
        "H":"inductance","mH":"inductance","µH":"inductance"
    ]
    private var graphValues: [CGFloat] {
        (0...160).map { i in
            let x = -graphRange + (2 * graphRange * Double(i) / 160)
            let y: Double
            switch formula.trimmingCharacters(in: .whitespaces).lowercased() {
            case "cos(x)": y = cos(x)
            case "x": y = x
            case "x^2": y = x*x
            case "1/x": y = abs(x) < 0.05 ? .nan : 1/x
            case "exp(x)": y = min(exp(x), 10)
            default: y = sin(x)
            }
            return y.isFinite ? CGFloat(y) : .nan
        }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Picker("Tool", selection: $tool) {
                Text("Converter").tag("Converter")
                Text("Formulas").tag("Formulas")
                Text("Graph").tag("Graph")
                Text("Questions").tag("Questions")
                Text("Flashcards").tag("Flashcards")
                Text("Tables").tag("Tables")
                Text("Diagrams").tag("Diagrams")
            }.pickerStyle(.segmented)
            Form {
                if tool == "Converter" {
                    Section("Engineering unit converter") {
                        TextField("Value", text: $value)
                        Picker("From", selection: $from) { ForEach(units.keys.sorted(), id: \.self) { Text($0).tag($0) } }
                        Picker("To", selection: $to) { ForEach(units.keys.sorted(), id: \.self) { Text($0).tag($0) } }
                        if let n = Double(value), let a = units[from], let b = units[to],
                           unitDimensions[from] == unitDimensions[to] {
                            Text("\(n * a / b, format: .number.precision(.significantDigits(1...8))) \(to)").font(.title2)
                        } else {
                            Text("Choose units from the same physical quantity.").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                } else if tool == "Formulas" {
                    Section("Formula shelf") {
                        TextField("Formula name", text: $label)
                        TextField("Equation / LaTeX source", text: $formula)
                        Button("Save formula") { _ = library.add(kind: "formula", title: label, body: formula, tags: "source:latex") }
                    }
                    Section("Saved formulas") {
                        ForEach(library.visibleNodes.filter { $0.kind == "formula" }) { item in
                            VStack(alignment: .leading) { Text(item.title); Text(item.body).foregroundStyle(.secondary) }
                        }
                    }
                } else if tool == "Graph" {
                    Section("Function plotter") {
                        TextField("Function: sin(x), cos(x), x, x^2, 1/x, exp(x)", text: $formula)
                        Slider(value: $graphRange, in: 1...30)
                        Text("Range: ±\(graphRange, specifier: "%.1f")")
                        GraphPreview(values: graphValues)
                        Button("Save graph formula") { _ = library.add(kind: "formula", title: "y = \(formula)", body: "y = \(formula)\nRange ±\(graphRange)", tags: "type:graph-formula") }
                    }
                } else if tool == "Questions" {
                    Section("Question bank entry") {
                        TextField("Question", text: $question, axis: .vertical)
                        TextField("Answer / explanation", text: $answer, axis: .vertical)
                        Button("Save question") { _ = library.add(kind: "question", title: question, body: answer, tags: "type:question"); question = ""; answer = "" }
                            .disabled(question.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    Section("Saved questions") {
                        ForEach(library.visibleNodes.filter { $0.kind == "question" }) { item in
                            VStack(alignment: .leading) { Text(item.title); Text(item.body).foregroundStyle(.secondary) }
                        }
                    }
                } else if tool == "Flashcards" {
                    Section("Create flashcard") {
                        TextField("Front", text: $cardFront)
                        TextField("Back", text: $cardBack)
                        Button("Save flashcard") { _ = library.add(kind: "flashcard", title: cardFront, body: cardBack, tags: "type:flashcard"); cardFront = ""; cardBack = "" }
                            .disabled(cardFront.isEmpty || cardBack.isEmpty)
                    }
                    Section("Your flashcards") {
                        ForEach(library.visibleNodes.filter { $0.kind == "flashcard" }) { item in
                            VStack(alignment: .leading) { Text(item.title); Text(item.body).foregroundStyle(.secondary) }
                        }
                    }
                } else if tool == "Tables" {
                    Section("Scientific table") {
                        TextField("Rows", text: $tableRows)
                        TextField("Columns", text: $tableColumns)
                        Button("Create table block") {
                            let rows = min(max(Int(tableRows) ?? 3, 1), 30)
                            let columns = min(max(Int(tableColumns) ?? 3, 1), 20)
                            let text = (0..<rows).map { _ in Array(repeating: "", count: columns).joined(separator: "\t") }.joined(separator: "\n")
                            _ = library.add(kind: "block", title: "Scientific table", body: text, tags: "type:table")
                        }
                    }
                } else {
                    Section("Diagram starters") {
                        Button("Create concept map outline") { _ = library.add(kind: "diagram", title: "Concept map", body: "Main idea\n  ├─ Concept A\n  └─ Concept B", tags: "type:concept-map") }
                        Button("Create circuit sketch notes") { _ = library.add(kind: "diagram", title: "Circuit sketch", body: "Components:\nConnections:\nReference values:", tags: "type:circuit-sketch") }
                    }
                }
            }.formStyle(.grouped)
        }.padding(24).navigationTitle("Study tools")
    }
}

struct GraphPreview: View {
    let values: [CGFloat]
    var body: some View {
        Canvas { context, size in
            let mid = size.height / 2
            var axes = Path()
            axes.move(to: CGPoint(x: 0, y: mid)); axes.addLine(to: CGPoint(x: size.width, y: mid))
            axes.move(to: CGPoint(x: size.width / 2, y: 0)); axes.addLine(to: CGPoint(x: size.width / 2, y: size.height))
            context.stroke(axes, with: .color(.secondary.opacity(0.5)), lineWidth: 1)
            var path = Path()
            var started = false
            for (i, value) in values.enumerated() {
                let x = size.width * CGFloat(i) / CGFloat(max(values.count - 1, 1))
                let y = mid - value * size.height / 12
                if value.isFinite && y >= 0 && y <= size.height {
                    if !started { path.move(to: CGPoint(x: x, y: y)); started = true }
                    else { path.addLine(to: CGPoint(x: x, y: y)) }
                } else { started = false }
            }
            context.stroke(path, with: .color(.accentColor), lineWidth: 2)
        }.frame(height: 220).background(.quaternary.opacity(0.25), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct ScribeBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws { FileWrapper(regularFileWithContents: data) }
}

func symbol(_ kind: String) -> String {
    switch kind {
    case "course": return "book.closed"; case "chapter": return "text.book.closed"; case "topic": return "folder"
    case "page": return "doc.text"; case "image": return "photo"; case "pdf": return "doc.richtext"
    case "flashcard": return "rectangle.on.rectangle"; case "question": return "questionmark.circle"
    case "formula": return "function"; case "diagram": return "point.3.connected.trianglepath.dotted"
    default: return "text.alignleft"
    }
}
