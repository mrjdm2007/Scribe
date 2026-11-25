import SwiftUI
import UniformTypeIdentifiers

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
            } else if let item = selected {
                NodeEditorView(item: item)
            } else {
                ContentUnavailableView("Your notes, organised.", systemImage: "books.vertical",
                    description: Text("Choose a notebook item or create a course to begin."))
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
        .alert("Scribe", isPresented: Binding(get: { library.lastError != nil }, set: { if !$0 { library.lastError = nil } })) {
            Button("OK") { library.lastError = nil }
        } message: { Text(library.lastError ?? "") }
    }

    private func exportBackup() {
        do {
            let data = try library.backupData()
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("Scribe-Backup.json")
            try data.write(to: url, options: .atomic)
            NSWorkspace.shared.activateFileViewerSelecting([url])
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
                    Divider()
                    Button("Move to Trash", role: .destructive) { library.moveToTrash(item.id) }
                } label: { Image(systemName: "ellipsis.circle") }
            }
            TextField("Title", text: $item.title).font(.largeTitle.weight(.semibold)).textFieldStyle(.plain)
                .onChange(of: item.title) { _, _ in library.update(item) }
            if item.kind == "block" || item.kind == "formula" || item.kind == "question" || item.kind == "flashcard" || item.kind == "diagram" {
                TextEditor(text: $item.body).font(.body).scrollContentBackground(.hidden)
                    .padding(10).background(.quaternary.opacity(0.35), in: RoundedRectangle(cornerRadius: 14))
                    .onChange(of: item.body) { _, _ in library.update(item) }
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

struct DesktopToolsView: View {
    @State private var value = "1"
    @State private var from = "m"
    @State private var to = "cm"
    @State private var formula = "sin(x)"
    @State private var label = "New formula"
    private let units: [String: Double] = ["m":1,"cm":0.01,"mm":0.001,"km":1000,"V":1,"mV":0.001,"kV":1000,"A":1,"mA":0.001,"Hz":1,"kHz":1000,"MHz":1_000_000,"W":1,"mW":0.001,"kW":1000,"s":1,"ms":0.001,"min":60,"h":3600,"Ω":1,"kΩ":1000,"MΩ":1_000_000]
    var body: some View {
        Form {
            Section("Engineering unit converter") {
                TextField("Value", text: $value)
                Picker("From", selection: $from) { ForEach(units.keys.sorted(), id: \.self) { Text($0).tag($0) } }
                Picker("To", selection: $to) { ForEach(units.keys.sorted(), id: \.self) { Text($0).tag($0) } }
                if let n = Double(value), let a = units[from], let b = units[to] { Text("\(n * a / b, format: .number.precision(.significantDigits(1...8))) \(to)").font(.title2) }
            }
            Section("Formula shelf") {
                TextField("Formula name", text: $label)
                TextField("Equation / LaTeX source", text: $formula)
                Text("Formula source is saved as text; visual typesetting is not yet available.").font(.caption).foregroundStyle(.secondary)
            }
        }.formStyle(.grouped).padding(24).navigationTitle("Study tools")
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
