import SwiftUI
import AppKit
import ScribeCore
import UniformTypeIdentifiers
import WebKit

struct ScribeMacRoot: View {
    @EnvironmentObject private var store: ScribeStore
    @AppStorage("scribe.dailyStudyGoalMinutes") private var dailyGoalMinutes = 60
    @AppStorage("scribe.recentItemIDs") private var recentItemIDs = ""
    @State private var showingImport = false
    @State private var showingExport = false
    @State private var showingPDFImport = false
    @State private var showingTrash = false
    @State private var integrityReport = ""
    @State private var showingIntegrityReport = false
    @State private var historyItem: ScribeItem?
    @State private var showingFlashcardComposer = false
    @State private var editingFlashcard: ScribeItem?
    @State private var showingDeckManager = false
    @State private var flashcardQuestion = ""
    @State private var flashcardAnswer = ""
    @State private var flashcardDeck = "General"
    @State private var flashcardDeckFilter = "All decks"
    @State private var searchKindFilter = "All"
    @State private var searchSavedOnly = false
    @State private var searchSort = "Recent"
    @State private var bulkSelectedIDs = Set<Int64>()
    @State private var trashSelectedIDs = Set<Int64>()
    @State private var bulkTagDraft = ""
    @State private var showingWordGoal = false
    @State private var wordGoalDraft = "500"
    @State private var draftTitle = ""
    @State private var draftBody = ""
    @State private var converterValue = "1"
    @State private var converterUnit = "mA"
    @State private var converterTarget = "A"
    @State private var revealed = false
    @State private var formulaQuery = ""
    @State private var topicSearchQuery = ""
    @State private var topicSearchIndex = 0
    @State private var showLatexPreview = false
    @State private var pdfAnnotationDraft = ""
    @State private var pdfAnnotationKind = "Highlight"
    @AppStorage("scribe.supabase.url") private var cloudURL = ScribeCloudConfiguration.supabaseURL
    @AppStorage("scribe.supabase.anonKey") private var cloudAnonKey = ScribeCloudConfiguration.supabaseAnonKey
    @State private var cloudEmail = ""
    @State private var cloudPassword = ""
    @State private var cloudStatus = ""
    @State private var cloudBusy = false
    @State private var editorSelection = NSRange(location: 0, length: 0)
    @FocusState private var searchFocused: Bool

    private let gold = Color(red: 0.84, green: 0.72, blue: 0.55)
    private let panel = Color(nsColor: .controlBackgroundColor)

    var body: some View {
        NavigationSplitView {
            VStack(spacing: 0) {
                HStack { Text("SCRIBE").font(.system(size: 12, weight: .bold, design: .rounded)).tracking(2); Spacer(); Text("STUDY WORKSPACE").font(.system(size: 9, weight: .medium)).foregroundStyle(.secondary) }.padding()
                List(selection: $store.activeTool) {
                    ForEach(ScribeTool.allCases) { tool in Label(tool.rawValue, systemImage: icon(for: tool)).tag(tool) }
                }.listStyle(.sidebar)
                Divider()
                HStack {
                    Image(systemName: "magnifyingglass")
                    TextField("Search everything", text: $store.searchText).textFieldStyle(.plain).focused($searchFocused)
                    Button { searchFocused = true } label: { Image(systemName: "command") }
                        .buttonStyle(.plain).help("Focus search (⌘F)")
                        .keyboardShortcut("f", modifiers: .command)
                }.padding(12)
                Button { store.create(kind: "course", title: "Untitled course") } label: { Label("New course", systemImage: "plus") }
                    .buttonStyle(.borderedProminent).tint(gold).padding()
                    .keyboardShortcut("n", modifiers: .command)
            }
            .navigationSplitViewColumnWidth(min: 220, ideal: 250, max: 310)
        } detail: {
            Group {
                switch store.activeTool {
                case .library: library
                case .flashcards: flashcards
                case .focus: focus
                case .formulas: formulas
                case .converter: converter
                case .settings: backup
                case .academic: AcademicToolsView()
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity).background {
                ZStack {
                    Color(nsColor: .windowBackgroundColor)
                    RadialGradient(colors: [gold.opacity(0.075), .clear], center: .topLeading, startRadius: 20, endRadius: 900)
                    RadialGradient(colors: [Color.white.opacity(0.018), .clear], center: .bottomTrailing, startRadius: 0, endRadius: 720)
                }
            }
        }
        .alert("Set writing goal", isPresented: $showingWordGoal) {
            TextField("Target words (0 disables)", text: $wordGoalDraft)
            Button("Cancel", role: .cancel) { }
            Button("Save") {
                guard let item = store.selected else { return }
                let clean = item.tags.split(separator: ";").map(String.init).filter { !$0.hasPrefix("wordGoal:") }
                let goal = max(0, min(100000, Int(wordGoalDraft) ?? 0))
                store.update(item, tags: (clean + (goal > 0 ? ["wordGoal:\(goal)"] : [])).joined(separator: ";"))
            }
        } message: { Text("Track progress toward a word-count target for this topic.") }
        .fileImporter(isPresented: $showingImport, allowedContentTypes: [.json]) { result in if case .success(let url) = result { _ = url.startAccessingSecurityScopedResource(); defer { url.stopAccessingSecurityScopedResource() }; try? store.importBackup(from: url) } }
        .fileImporter(isPresented: $showingPDFImport, allowedContentTypes: [.pdf, .image, .plainText]) { result in
            guard case .success(let source) = result else { return }
            let access = source.startAccessingSecurityScopedResource()
            defer { if access { source.stopAccessingSecurityScopedResource() } }
            let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
                .appendingPathComponent("Scribe/attachments", isDirectory: true)
            try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            let destination = root.appendingPathComponent(UUID().uuidString + "-" + source.lastPathComponent)
            if (try? FileManager.default.copyItem(at: source, to: destination)) != nil {
                let ext = source.pathExtension.lowercased()
                let typeTag = ext == "pdf" ? "pdf" : (["png", "jpg", "jpeg", "heic", "webp", "gif"].contains(ext) ? "image" : "text")
                let body = typeTag == "text" ? ((try? String(contentsOf: destination, encoding: .utf8)) ?? "") : "\(typeTag.capitalized) attachment"
                store.create(kind: "block", title: source.lastPathComponent, parentId: store.selectedID,
                    body: body, tags: typeTag == "pdf" ? "type:pdf;ocr:pending" : "type:\(typeTag)",
                    uri: destination.path, switchToLibrary: false)
                if typeTag == "pdf", let imported = store.selected {
                    let importedID = imported.id
                    Task {
                        do {
                            let extracted = try await ScribePDFOCR.extractText(from: destination)
                            if let latest = store.items.first(where: { $0.id == importedID }) {
                                let hasText = !extracted.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                store.update(latest,
                                    body: hasText ? extracted : "PDF attachment · no searchable text could be extracted",
                                    tags: hasText ? "type:pdf;ocr:indexed" : "type:pdf;ocr:failed")
                            }
                        } catch {
                            if let latest = store.items.first(where: { $0.id == importedID }) {
                                store.update(latest, tags: "type:pdf;ocr:failed")
                            }
                        }
                    }
                }
            }
        }
        .sheet(isPresented: $showingFlashcardComposer) {
            VStack(alignment: .leading, spacing: 14) {
                Text("New flashcard").font(.title2.bold())
                TextField("Question", text: $flashcardQuestion).textFieldStyle(.roundedBorder)
                TextEditor(text: $flashcardAnswer).frame(minHeight: 120).overlay(RoundedRectangle(cornerRadius: 8).stroke(.secondary.opacity(0.3)))
                TextField("Deck / subject", text: $flashcardDeck).textFieldStyle(.roundedBorder)
                HStack { Spacer(); Button("Cancel") { showingFlashcardComposer = false }; Button("Save card") {
                    guard !flashcardQuestion.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                    let deck = flashcardDeck.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ";", with: " ")
                    store.create(kind: "flashcard", title: flashcardQuestion, body: flashcardAnswer, tags: "interval:1;deck:" + (deck.isEmpty ? "General" : deck))
                    store.activeTool = .flashcards
                    flashcardQuestion = ""; flashcardAnswer = ""; flashcardDeck = "General"; showingFlashcardComposer = false
                }.buttonStyle(.borderedProminent).tint(gold) }
            }.padding(22).frame(width: 420, height: 300)
        }
        .sheet(item: $editingFlashcard) { card in
            FlashcardEditSheet(card: card) { title, body, tags in store.update(card, title: title, body: body, tags: tags) }
        }
        .sheet(isPresented: $showingDeckManager) {
            let deckCards = store.items.filter { $0.kind == "flashcard" && $0.deletedAt == nil && flashcardDeckName($0) == flashcardDeckFilter }
            FlashcardDeckManagerSheet(
                deck: flashcardDeckFilter,
                cards: deckCards,
                availableDecks: Array(Set(store.items.filter { $0.kind == "flashcard" && $0.deletedAt == nil }.map(flashcardDeckName))).sorted(),
                onRename: { name, cards in
                    cards.forEach { card in
                        let retained = card.tags.split(separator: ";").map(String.init).filter { !$0.hasPrefix("deck:") && !$0.hasPrefix("subject:") }
                        store.update(card, tags: (retained + ["deck:" + name]).joined(separator: ";"))
                    }
                    flashcardDeckFilter = name
                },
                onMerge: { target, cards in
                    cards.forEach { card in
                        let retained = card.tags.split(separator: ";").map(String.init).filter { !$0.hasPrefix("deck:") && !$0.hasPrefix("subject:") }
                        store.update(card, tags: (retained + ["deck:" + target]).joined(separator: ";"))
                    }
                    flashcardDeckFilter = target
                },
                onDelete: { cards in
                    cards.forEach { store.remove($0) }
                    flashcardDeckFilter = "All decks"
                }
            )
        }
        .sheet(isPresented: $showingTrash) { trashSheet }
        .sheet(item: $historyItem) { item in historySheet(item) }
        .fileExporter(isPresented: $showingExport, document: BackupDocument(data: (try? store.backupData()) ?? Data("[]".utf8)), contentType: .json, defaultFilename: "Scribe-backup") { _ in }
        .onChange(of: store.selectedID) { value in
            guard let value, let item = store.items.first(where: { $0.id == value }),
                  ["course", "chapter", "topic", "page", "block"].contains(item.kind) else { return }
            var ids = recentItemIDs.split(separator: ",").compactMap { Int64($0) }
            ids.removeAll { $0 == value }
            recentItemIDs = ([value] + ids).prefix(6).map(String.init).joined(separator: ",")
        }
        .onChange(of: store.focusSearch) { value in
            if value { searchFocused = true; store.focusSearch = false }
        }
        .onChange(of: topicSearchQuery) { _ in topicSearchIndex = 0 }
        .alert("Library health check", isPresented: $showingIntegrityReport) {
            Button("Done", role: .cancel) { }
        } message: { Text(integrityReport) }
        .preferredColorScheme(.dark)
    }
    private func icon(for tool: ScribeTool) -> String {
        switch tool {
        case .library: return "books.vertical"
        case .flashcards: return "rectangle.stack"
        case .focus: return "timer"
        case .formulas: return "function"
        case .converter: return "arrow.left.arrow.right"
        case .settings: return "externaldrive"
        case .academic: return "graduationcap"
        }
    }
    private var library: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack { VStack(alignment: .leading) { Text("Your library").font(.largeTitle.bold()); Text("Course → Chapter → Topic notebook").foregroundStyle(.secondary) }; Spacer(); Button { showingPDFImport = true } label: { Label("Import attachment", systemImage: "paperclip") } }
            if store.searchText.isEmpty && store.selected == nil {
                let recentIDs = recentItemIDs.split(separator: ",").compactMap { Int64($0) }
                let recentItems = recentIDs.compactMap { id in store.items.first(where: { $0.id == id && $0.deletedAt == nil }) }
                if !recentItems.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("RECENTLY OPENED").font(.caption.bold()).foregroundStyle(.secondary)
                            Spacer()
                            Button("Clear") { recentItemIDs = "" }.font(.caption)
                                .buttonStyle(.plain).foregroundStyle(.secondary)
                        }
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(recentItems) { item in
                                    Button { store.selectedID = item.id } label: {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(item.title).lineLimit(1).font(.subheadline.weight(.medium))
                                            Text(item.kind.capitalized).font(.caption).foregroundStyle(.secondary)
                                        }.frame(width: 150, alignment: .leading).padding(12)
                                            .background(panel, in: RoundedRectangle(cornerRadius: 12))
                                    }.buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
            }
            HStack(spacing: 12) {
                if store.searchText.isEmpty {
                    hierarchy(parent: nil, title: "Courses")
                    if let selected = store.selected, ["course","chapter"].contains(selected.kind) { hierarchy(parent: selected.id, title: "Contents") }
                } else {
                    VStack(alignment: .leading) {
                        let filteredSearchItems = store.visibleItems.filter { item in
                            (searchKindFilter == "All" || item.kind.caseInsensitiveCompare(searchKindFilter) == .orderedSame) && (!searchSavedOnly || item.bookmarked)
                        }.sorted {
                            switch searchSort {
                            case "Title": return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
                            case "Oldest": return $0.updatedAt < $1.updatedAt
                            case "Created": return $0.createdAt > $1.createdAt
                            case "Longest": return $0.body.count > $1.body.count
                            case "Shortest": return $0.body.count < $1.body.count
                            default: return $0.updatedAt > $1.updatedAt
                            }
                        }
                        HStack(spacing: 8) {
                            Picker("Type", selection: $searchKindFilter) {
                                ForEach(["All", "course", "chapter", "topic", "page", "block"], id: \.self) { Text($0.capitalized).tag($0) }
                            }.pickerStyle(.menu).frame(width: 120)
                            Toggle("Saved only", isOn: $searchSavedOnly).toggleStyle(.checkbox)
                            Button("Reset") { searchKindFilter = "All"; searchSavedOnly = false; searchSort = "Recent" }
                            Spacer()
                            Button("Sort: \(searchSort)") {
                                let order = ["Recent", "Oldest", "Created", "Title", "Longest", "Shortest"]
                                let index = order.firstIndex(of: searchSort) ?? 0
                                searchSort = order[(index + 1) % order.count]
                            }
                        }
                        Text("SEARCH RESULTS · \(filteredSearchItems.count)").font(.caption.bold()).foregroundStyle(.secondary)
                        if !bulkSelectedIDs.isEmpty {
                            HStack(spacing: 8) {
                                Text("\(bulkSelectedIDs.count) selected").font(.caption.weight(.semibold))
                                Button("Select all") { bulkSelectedIDs = Set(filteredSearchItems.map(\.id)) }
                                Menu("Move to") {
                                    ForEach(store.items.filter { $0.deletedAt == nil && ["course", "chapter", "topic"].contains($0.kind) }) { target in
                                        Button(target.title) {
                                            store.bulkMove(filteredSearchItems.filter { bulkSelectedIDs.contains($0.id) }, to: target.id)
                                            bulkSelectedIDs.removeAll()
                                        }
                                    }
                                }
                                TextField("Add tag", text: $bulkTagDraft).textFieldStyle(.roundedBorder).frame(maxWidth: 150)
                                Button("Apply") {
                                    store.bulkTag(filteredSearchItems.filter { bulkSelectedIDs.contains($0.id) }, tag: bulkTagDraft)
                                    bulkTagDraft = ""
                                    bulkSelectedIDs.removeAll()
                                }.disabled(bulkTagDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                                Button("Archive", role: .destructive) {
                                    store.bulkArchive(filteredSearchItems.filter { bulkSelectedIDs.contains($0.id) })
                                    bulkSelectedIDs.removeAll()
                                }
                                Button("Clear") { bulkSelectedIDs.removeAll() }
                            }.font(.caption)
                        }
                        List(filteredSearchItems, selection: $store.selectedID) { item in
                            HStack(spacing: 8) {
                                Button {
                                    if bulkSelectedIDs.contains(item.id) { bulkSelectedIDs.remove(item.id) }
                                    else { bulkSelectedIDs.insert(item.id) }
                                } label: {
                                    Image(systemName: bulkSelectedIDs.contains(item.id) ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(bulkSelectedIDs.contains(item.id) ? gold : .secondary)
                                }.buttonStyle(.plain).help("Select for bulk actions")
                                VStack(alignment: .leading, spacing: 3) {
                                Text(item.title)
                                let bodyPreview = item.body.isEmpty ? "No content yet" : String(item.body.prefix(80))
                                Text("\(item.kind.capitalized) · \(bodyPreview)")
                                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                let wordCount = item.body.split(whereSeparator: { $0.isWhitespace }).count
                                let editedDate = Date(timeIntervalSince1970: Double(item.updatedAt) / 1000)
                                    .formatted(date: .abbreviated, time: .omitted)
                                Text("\(wordCount) words · Edited \(editedDate)")
                                    .font(.system(size: 10)).foregroundStyle(.tertiary)
                            }
                            }
                            .tag(item.id)
                            .contextMenu {
                                Button("Copy note") {
                                    NSPasteboard.general.clearContents()
                                    NSPasteboard.general.setString(item.title + (item.body.isEmpty ? "" : "\n\n" + item.body), forType: .string)
                                }
                                Button("Copy title") {
                                    NSPasteboard.general.clearContents()
                                    NSPasteboard.general.setString(item.title, forType: .string)
                                }
                            }
                        }.listStyle(.inset).frame(minWidth: 250)
                    }.padding(12).background(panel, in: RoundedRectangle(cornerRadius: 14))
                }
                editor
            }
        }.padding(22)
    }
    private func hierarchy(parent: Int64?, title: String) -> some View {
        VStack(alignment: .leading) {
            Text(title.uppercased()).font(.caption.bold()).foregroundStyle(.secondary)
            ScrollView {
                LazyVStack(spacing: 6) {
                    ForEach(store.children(of: parent)) { item in
                        Button { store.selectedID = item.id } label: { hierarchyRow(item) }
                            .buttonStyle(.plain)
                            .contextMenu {
                                Button(item.bookmarked ? "Remove bookmark" : "Bookmark") {
                                    store.update(item, bookmarked: !item.bookmarked)
                                }
                                Button("Duplicate with contents") { store.duplicateTree(item) }
                                if item.kind == "page" {
                                    Button("Split page into two") { store.splitPage(item) }
                                    Button("Merge with next page") { store.mergePageWithNext(item) }
                                }
                                Button("Move up") { store.move(item, direction: -1) }
                                Button("Move down") { store.move(item, direction: 1) }
                                Button("Delete", role: .destructive) { store.remove(item) }
                            }
                    }
                }
            }
            .frame(minHeight: 120)
            HStack {
                if parent == nil {
                    Button("New course") { store.create(kind: "course", title: "Untitled course") }
                } else if let parentKind = store.items.first(where: { $0.id == parent })?.kind, parentKind == "course" {
                    Button("New chapter") { store.create(kind: "chapter", title: "Untitled chapter", parentId: parent) }
                } else if let parentKind = store.items.first(where: { $0.id == parent })?.kind, parentKind == "chapter" {
                    Button("New topic") { store.create(kind: "topic", title: "Untitled topic", parentId: parent) }
                }
                Spacer()
            }
        }.padding(12).frame(minWidth: 200).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14)).overlay(RoundedRectangle(cornerRadius: 14).stroke(LinearGradient(colors: [.white.opacity(0.24), .white.opacity(0.035), gold.opacity(0.14)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1)).shadow(color: .black.opacity(0.22), radius: 12, x: 0, y: 6).shadow(color: gold.opacity(0.05), radius: 18, x: 0, y: 4)
    }
    private func hierarchyRow(_ item: ScribeItem) -> some View {
        HStack(spacing: 10) {
            Image(systemName: item.kind == "page" ? "doc.text" : "folder").foregroundStyle(gold)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title).lineLimit(1)
                Text(item.kind.capitalized).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 4)
            if item.bookmarked { Image(systemName: "bookmark.fill").foregroundStyle(gold) }
        }
        .padding(.horizontal, 9).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(store.selectedID == item.id ? gold.opacity(0.12) : Color.clear, in: RoundedRectangle(cornerRadius: 8))
    }

    private func appendTopicTemplate(_ template: String) {
        guard let item = store.selected else { return }
        let updated = item.body.isEmpty ? template : item.body.trimmingCharacters(in: .whitespacesAndNewlines) + "\n\n" + template
        store.update(item, body: updated)
    }

    private var editor: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let item = store.selected {
                Text(item.kind == "topic" ? "TOPIC NOTEBOOK" : item.kind.uppercased()).font(.caption.bold()).foregroundStyle(.secondary)
                TextField("Title", text: Binding(get: { store.selected?.title ?? "" }, set: { store.update(item,title:$0) })).font(.title2.bold()).textFieldStyle(.plain)
                if item.kind == "topic" {
                    let backlinkBlocks = store.items.filter { block in
                        block.tags.contains("type:crosslink") &&
                        Int64(block.tags.components(separatedBy: "target:").last?.components(separatedBy: CharacterSet(charactersIn: ";,")).first ?? "") == item.id
                    }
                    let backlinkSources = backlinkBlocks.compactMap { block in
                        store.items.first(where: { $0.id == block.parentId })
                    }.reduce(into: [ScribeItem]()) { result, source in
                        if !result.contains(where: { $0.id == source.id }) { result.append(source) }
                    }
                    if !backlinkSources.isEmpty {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("LINKED FROM").font(.caption.bold()).foregroundStyle(.secondary)
                            ForEach(backlinkSources) { source in
                                Button {
                                    store.selectedID = source.id
                                } label: {
                                    Label(source.title, systemImage: "arrowshape.turn.up.left")
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }.buttonStyle(.plain)
                            }
                        }.padding(10).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
                if item.kind == "topic" {
                    let outgoingBlocks = store.items.filter { $0.parentId == item.id && $0.tags.contains("type:crosslink") && $0.deletedAt == nil }
                    let outgoingIDs = outgoingBlocks.compactMap { block -> Int64? in
                        guard let raw = block.tags.components(separatedBy: "target:").last?.components(separatedBy: CharacterSet(charactersIn: ";,")).first else { return nil }
                        return Int64(raw)
                    }
                    let resolvedTargets = outgoingIDs.compactMap { targetID in store.items.first(where: { $0.id == targetID && $0.deletedAt == nil }) }.reduce(into: [ScribeItem]()) { result, target in
                        if !result.contains(where: { $0.id == target.id }) { result.append(target) }
                    }
                    let brokenCount = outgoingIDs.filter { targetID in !store.items.contains(where: { $0.id == targetID && $0.deletedAt == nil }) }.count
                    if !outgoingBlocks.isEmpty || !backlinkSources.isEmpty {
                        Text("\(outgoingBlocks.count) outgoing · \(backlinkBlocks.count) incoming links").font(.caption).foregroundStyle(.secondary)
                    }
                    if !resolvedTargets.isEmpty || brokenCount > 0 {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("LINKS IN THIS TOPIC").font(.caption.bold()).foregroundStyle(.secondary)
                            ForEach(resolvedTargets) { target in Button { store.selectedID = target.id } label: { Label(target.title, systemImage: "link").frame(maxWidth: .infinity, alignment: .leading) }.buttonStyle(.plain) }
                            if brokenCount > 0 { Label("\(brokenCount) broken topic link(s) · target may have been deleted", systemImage: "exclamationmark.triangle").font(.caption).foregroundStyle(.orange) }
                        }.padding(10).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
                let editorWordCount = item.body.split(whereSeparator: { $0.isWhitespace }).count
                HStack(spacing: 7) {
                    Text("\(editorWordCount) words")
                    Text("·")
                    Text("\(max(1, (editorWordCount + 199) / 200)) min read")
                    Text("·")
                    Text("\(item.body.count) characters")
                }.font(.caption).foregroundStyle(.secondary)
                if item.kind == "topic" {
                    HStack {
                        Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                        TextField("Find a word inside this topic…", text: $topicSearchQuery).textFieldStyle(.plain)
                        if !topicSearchQuery.isEmpty { Button { topicSearchQuery = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }.buttonStyle(.plain) }
                    }.padding(10).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 10))
                    let matches = item.body.components(separatedBy: .newlines).filter { $0.localizedCaseInsensitiveContains(topicSearchQuery) }
                    let matchingBlocks = store.children(of: item.id).filter { $0.title.localizedCaseInsensitiveContains(topicSearchQuery) || $0.body.localizedCaseInsensitiveContains(topicSearchQuery) }
                    if !topicSearchQuery.isEmpty {
                        let wordMatches = searchRanges(in: item.body, query: topicSearchQuery)
                        HStack {
                            Text("\(wordMatches.count) word matches · \(matchingBlocks.count) note elements").font(.caption).foregroundStyle(.secondary)
                            Spacer()
                            Button { topicSearchIndex = max(0, topicSearchIndex - 1) } label: { Image(systemName: "chevron.up") }.disabled(wordMatches.isEmpty)
                            Button { topicSearchIndex = min(max(0, wordMatches.count - 1), topicSearchIndex + 1) } label: { Image(systemName: "chevron.down") }.disabled(wordMatches.isEmpty)
                        }
                        ForEach(Array(matches.prefix(5).enumerated()), id: \.offset) { entry in
                            Text(entry.element).font(.caption).lineLimit(2).padding(.horizontal, 8)
                        }
                        ForEach(matchingBlocks.prefix(5)) { block in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(block.title).font(.caption.bold())
                                Text(block.body).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            }.padding(.horizontal, 8)
                        }
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            Button("B") { applyMarkup("**", "**") }.fontWeight(.bold)
                            Button("I") { applyMarkup("*", "*") }.italic()
                            Button("U") { applyMarkup("__", "__") }
                            Button("S") { applyMarkup("~~", "~~") }
                            Button("Heading") { applyMarkup("## ", "") }
                            Button("List") { applyMarkup("- ", "") }
                            Button("Task") { applyMarkup("- [ ] ", "") }
                            Button("1. List") { applyMarkup("1. ", "") }
                            Button("Code") { applyMarkup("```\n", "\n```") }
                            Button("Math") { applyMarkup("\\(", "\\)") }
                            Button("Display math") { applyMarkup("$\n", "\n$") }
                            Button("Quote") { applyMarkup("> ", "") }
                            Button("Link") { applyMarkup("[", "](https://)") }
                            Button("1. List") { applyMarkup("1. ", "") }
                            Button("Table") { applyMarkup("| ", " |  |") }
                            Button(showLatexPreview ? "Edit" : "Preview") { showLatexPreview.toggle() }
                            Menu("Templates") {
                                Button("Lecture notes") { appendTopicTemplate("# Lecture notes\n\n## Key concepts\n- \n\n## Examples\n- \n\n## Questions\n- [ ] ") }
                                Button("Textbook summary") { appendTopicTemplate("# Summary\n\n## Main idea\n\n## Important terms\n- \n\n## Takeaways\n- \n") }
                                Button("Problem solving") { appendTopicTemplate("# Problem\n\n## Given\n\n## Find\n\n## Approach\n1. \n\n## Solution\n\n## Final answer\n") }
                            }
                            Button("Word goal") { wordGoalDraft = "500"; showingWordGoal = true }
                        }.buttonStyle(.bordered).controlSize(.small)
                    }
                    Text("Notes use portable Markdown text, preserving line breaks and formatting markers across Android, Mac and window resizing.").font(.caption).foregroundStyle(.secondary)
                    ForEach(store.children(of: item.id)) { block in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(block.title).font(.headline)
                                Spacer()
                                Button { store.update(block, bookmarked: !block.bookmarked) } label: {
                                    Image(systemName: block.bookmarked ? "bookmark.fill" : "bookmark")
                                }
                                Button(role: .destructive) { store.remove(block) } label: {
                                    Image(systemName: "trash")
                                }
                            }
                            if block.tags.contains("type:pdf"), !block.uri.isEmpty {
                                ScribePDFPreview(url: URL(fileURLWithPath: block.uri)).frame(minHeight: 240)
                                Button("Extract PDF text") {
                                    Task {
                                        if let text = try? await ScribePDFOCR.extractText(from: URL(fileURLWithPath: block.uri)) {
                                            store.update(block, body: text)
                                        }
                                    }
                                }
                            } else if block.tags.contains("type:image"), let image = NSImage(contentsOfFile: block.uri) {
                                Image(nsImage: image).resizable().scaledToFit().frame(maxHeight: 240)
                            } else {
                                Text(block.body).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .padding(12)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                if item.tags.contains("type:pdf"), !item.uri.isEmpty {
                    ScribePDFWorkspace(url: URL(fileURLWithPath: item.uri))
                    Text("Select text to highlight or underline, or add a note directly in the PDF. Changes are saved in the PDF file.")
                        .font(.caption).foregroundStyle(.secondary)
                    HStack {
                        Button { Task { if let text = try? await ScribePDFOCR.extractText(from: URL(fileURLWithPath: item.uri)) { store.update(item, body: text) } } } label: { Label("Extract PDF text", systemImage: "text.viewfinder") }
                        Text("OCR text is saved in this attachment's note body.").font(.caption).foregroundStyle(.secondary)
                    }
                } else {
                    if item.kind == "topic" {
                        Group {
                            if showLatexPreview {
                                LatexPreviewView(source: item.body)
                            } else {
                                MarkdownEditor(
                                    text: Binding(get: { store.selected?.body ?? "" }, set: { store.update(item, body: $0) }),
                                    selection: $editorSelection,
                                    searchQuery: topicSearchQuery,
                                    matchIndex: topicSearchIndex
                                )
                            }
                        }
                        .frame(minHeight: 340)
                        .padding(8)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(LinearGradient(colors: [.white.opacity(0.26), .white.opacity(0.035), gold.opacity(0.16)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1))
                        .shadow(color: gold.opacity(0.07), radius: 18, x: 0, y: 6)
                    } else {
                        TextEditor(text: Binding(get: { store.selected?.body ?? "" }, set: { store.update(item,body:$0) }))
                            .font(.system(size: 15)).scrollContentBackground(.hidden).padding(8)
                            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(LinearGradient(colors: [.white.opacity(0.22), .white.opacity(0.035), gold.opacity(0.12)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1))
                            .shadow(color: gold.opacity(0.055), radius: 14, x: 0, y: 5)
                    }
                }
                HStack {
                    Button { store.update(item, bookmarked: !item.bookmarked) } label: {
                        Label(item.bookmarked ? "Bookmarked" : "Bookmark", systemImage: "bookmark")
                    }
                    Button { historyItem = item } label: { Label("History", systemImage: "clock.arrow.circlepath") }
                    Spacer()
                    Button(role: .destructive) { store.remove(item) } label: {
                        Label("Move to trash", systemImage: "trash")
                    }
                }
            } else { VStack(spacing: 10) { Image(systemName: "text.book.closed").font(.system(size: 34)); Text("Select a course, topic or page").font(.headline); Text("Your notes and study material will appear here.").foregroundStyle(.secondary) }.frame(maxWidth: .infinity, maxHeight: .infinity) }
        }.padding(14).frame(minWidth: 440, maxWidth:.infinity,maxHeight:.infinity).background(.ultraThinMaterial,in:RoundedRectangle(cornerRadius:14)).overlay(RoundedRectangle(cornerRadius:14).stroke(LinearGradient(colors:[.white.opacity(0.24),.white.opacity(0.035),gold.opacity(0.15)],startPoint:.topLeading,endPoint:.bottomTrailing),lineWidth:1)).shadow(color:.black.opacity(0.24),radius:16,x:0,y:8).shadow(color:gold.opacity(0.055),radius:20,x:0,y:4)
    }
    private func searchRanges(in text: String, query: String) -> [NSRange] {
        guard !query.isEmpty else { return [] }
        let source = text as NSString
        var cursor = 0
        var found: [NSRange] = []
        while cursor < source.length {
            let range = source.range(of: query, options: [.caseInsensitive],
                range: NSRange(location: cursor, length: source.length - cursor))
            if range.location == NSNotFound || range.length == 0 { break }
            found.append(range)
            cursor = range.location + range.length
        }
        return found
    }
    private func applyMarkup(_ prefix: String, _ suffix: String) {
        guard let item = store.selected else { return }
        let source = item.body as NSString
        let location = min(max(0, editorSelection.location), source.length)
        let length = min(max(0, editorSelection.length), source.length - location)
        let selected = source.substring(with: NSRange(location: location, length: length))
        let placeholder = selected.isEmpty ? "text" : selected
        let replacement = prefix + placeholder + suffix
        let updated = source.replacingCharacters(in: NSRange(location: location, length: length), with: replacement)
        store.update(item, body: updated)
        editorSelection = NSRange(location: location + (prefix as NSString).length, length: (placeholder as NSString).length)
    }
    private var flashcards: some View {
        VStack(alignment:.leading,spacing:16) {
            HStack { Text("Flashcards").font(.largeTitle.bold()); Spacer(); Button { showingFlashcardComposer = true } label: { Label("Add flashcard", systemImage: "plus") }.buttonStyle(.borderedProminent).tint(gold) }
            let cards = store.items.filter { $0.kind == "flashcard" && $0.deletedAt == nil }
            let decks = ["All decks"] + Array(Set(cards.map(flashcardDeckName))).sorted()
            let visibleCards = cards.filter { flashcardDeckFilter == "All decks" || flashcardDeckName($0) == flashcardDeckFilter }
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let dueCards = visibleCards.filter { card in
                let dueAt = card.tags.split(separator: ";").first(where: { $0.hasPrefix("dueAt:") }).flatMap { Int64($0.dropFirst("dueAt:".count)) } ?? 0
                return dueAt <= now
            }
            let upcomingCount = visibleCards.count - dueCards.count
            let todayStart = Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
            let reviewedToday = visibleCards.filter { card in
                let reviewedAt = card.tags.split(separator: ";").first(where: { $0.hasPrefix("lastReviewedAt:") })
                    .flatMap { Int64($0.dropFirst("lastReviewedAt:".count)) } ?? 0
                return reviewedAt >= todayStart
            }.count
            HStack {
                Text("\(dueCards.count) due now · \(upcomingCount) upcoming · \(reviewedToday) reviewed today").foregroundStyle(.secondary)
                Spacer()
                Picker("Deck", selection: $flashcardDeckFilter) {
                    ForEach(decks, id: \.self) { Text($0).tag($0) }
                }.frame(maxWidth: 180)
                Button("Manage") { showingDeckManager = true }.disabled(flashcardDeckFilter == "All decks")
            }
            if let card = dueCards.first {
                VStack(spacing:18) { Text(revealed ? card.body : card.title).font(.title2).multilineTextAlignment(.center).frame(maxWidth:.infinity,minHeight:180); Button(revealed ? "Hide answer" : "Reveal answer") { revealed.toggle() }; Button("Edit flashcard") { editingFlashcard = card }.buttonStyle(.bordered)
                    if revealed { HStack { reviewButton("Again",0,card); reviewButton("Hard",1,card); reviewButton("Good",2,card); reviewButton("Easy",3,card) } }
                }.padding(30).background(panel,in:RoundedRectangle(cornerRadius:20))
            } else if cards.isEmpty { VStack(spacing: 10) { Image(systemName: "rectangle.stack").font(.system(size: 34)); Text("No flashcards yet").font(.headline); Text("Create flashcards in your study notes and they will appear here.").foregroundStyle(.secondary) }.frame(maxWidth: .infinity, minHeight: 180) }
            else { VStack(spacing: 10) { Image(systemName: "checkmark.circle").font(.system(size: 34)).foregroundStyle(gold); Text("All caught up").font(.headline); Text("\(upcomingCount) cards scheduled for later review.").foregroundStyle(.secondary) }.frame(maxWidth: .infinity, minHeight: 180) }
            Spacer()
        }.padding(24)
    }
    private func flashcardDeckName(_ card: ScribeItem) -> String {
        let tags = card.tags.split(separator: ";")
        let tag = tags.first(where: { $0.hasPrefix("deck:") }) ?? tags.first(where: { $0.hasPrefix("subject:") })
        guard let tag else { return "General" }
        let value = String(tag.split(separator: ":", maxSplits: 1).last ?? "")
        return value.isEmpty ? "General" : value
    }
    private func reviewButton(_ title:String,_ rating:Int,_ card:ScribeItem)->some View { Button(title) { store.review(card,rating:rating); revealed=false }.buttonStyle(.bordered) }
    private var focus: some View {
        let weekStart = Calendar.current.dateInterval(of: .weekOfYear, for: Date())?.start ?? Calendar.current.startOfDay(for: Date())
        let weeklySessions = store.items.filter { $0.kind == "study_session" && $0.deletedAt == nil && Date(timeIntervalSince1970: Double($0.createdAt) / 1000) >= weekStart }
        let weeklyMinutes = weeklySessions.reduce(0) { total, item in
            let value = item.tags.range(of: #"minutes:\d+"#, options: .regularExpression)
                .map { Int(item.tags[$0].dropFirst(8)) ?? 0 } ?? 0
            return total + value
        }
        return VStack(spacing:20) {
            Text("Focus mode").font(.largeTitle.bold())
            TextField("What are you studying?",text:$store.sessionLabel).textFieldStyle(.roundedBorder).frame(maxWidth:360)
            Text(String(format:"%02d:%02d",store.remaining/60,store.remaining%60)).font(.system(size:76,weight:.light,design:.rounded)).monospacedDigit().foregroundStyle(gold)
            HStack { ForEach([15,25,45,60],id:\.self) { m in Button("\(m) min") { store.focusMinutes=m;store.remaining=m*60 }.disabled(store.isRunning) } }
            HStack { Button(store.isRunning ? "Pause":"Start") { store.startTimer() }.buttonStyle(.borderedProminent).tint(gold); Button("Reset") { store.resetTimer() } }
            Text("\(store.completedSessions) completed this visit").foregroundStyle(.secondary)
            let todayMinutes = ScribeStudyProgress.minutesStudied(on: Date(), sessions: store.items)
            let studyStreak = ScribeStudyProgress.currentStreakDays(sessions: store.items, today: Date())
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("TODAY'S STUDY GOAL").font(.caption.bold()).foregroundStyle(.secondary)
                        Text("\(todayMinutes) / 60 min").font(.title2.bold()).foregroundStyle(gold)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("🔥 \(studyStreak) day\(studyStreak == 1 ? "" : "s")").font(.headline).foregroundStyle(gold)
                        Text("study streak").font(.caption).foregroundStyle(.secondary)
                    }
                }
                HStack(spacing: 6) {
                    ForEach([30, 60, 90, 120], id: \.self) { minutes in
                        Button("\(minutes) min") { dailyGoalMinutes = minutes }
                            .buttonStyle(.bordered)
                            .tint(dailyGoalMinutes == minutes ? gold : .secondary)
                    }
                }
                ProgressView(value: Double(min(todayMinutes, dailyGoalMinutes)), total: Double(dailyGoalMinutes)).tint(gold)
                Text(todayMinutes >= dailyGoalMinutes ? "Daily goal completed" : "\(dailyGoalMinutes - todayMinutes) min left to reach today's goal")
                    .font(.caption).foregroundStyle(.secondary)
            }.padding(14).background(panel, in: RoundedRectangle(cornerRadius: 12))
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("THIS WEEK").font(.caption.bold()).foregroundStyle(.secondary)
                    Text("\(weeklyMinutes / 60)h \(weeklyMinutes % 60)m").font(.title2.bold()).foregroundStyle(gold)
                    Text("Study time").font(.caption).foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity, alignment: .leading).padding(14).background(panel, in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 4) {
                    Text("SESSIONS").font(.caption.bold()).foregroundStyle(.secondary)
                    Text("\(weeklySessions.count)").font(.title2.bold()).foregroundStyle(gold)
                    Text("Completed this week").font(.caption).foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity, alignment: .leading).padding(14).background(panel, in: RoundedRectangle(cornerRadius: 12))
            }
            VStack(alignment: .leading, spacing: 10) {
                Text("RECENT SESSIONS").font(.caption.bold()).foregroundStyle(.secondary)
                let recentSessions = store.items.filter { $0.kind == "study_session" && $0.deletedAt == nil }
                    .sorted { $0.createdAt > $1.createdAt }.prefix(5)
                if recentSessions.isEmpty {
                    Text("Your completed focus sessions will appear here.")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    ForEach(Array(recentSessions), id: \.id) { session in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(session.title).font(.subheadline.weight(.medium))
                                Text(Date(timeIntervalSince1970: Double(session.createdAt) / 1000), style: .date)
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            let minutes = session.tags.range(of: #"minutes:\d+"#, options: .regularExpression)
                                .map { Int(session.tags[$0].dropFirst(8)) ?? 0 } ?? 0
                            Text("\(minutes) min").font(.caption.weight(.semibold)).foregroundStyle(gold)
                        }.padding(10).background(panel, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }.frame(maxWidth: 560, alignment: .leading)
            Text("Completion notifications appear even when Scribe is not the active window, if notifications are allowed in macOS settings.")
                .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
            Spacer()
        }.padding(30)
    }
    private var formulas: some View {
        VStack(alignment:.leading,spacing:12) {
            Text("Engineering formula shelf").font(.largeTitle.bold())
            TextField("Search formulas",text:$formulaQuery).textFieldStyle(.roundedBorder)
            let formulas: [FormulaPreset] = [
                FormulaPreset(name:"Ohm’s law",expression:"V = IR"),
                FormulaPreset(name:"Power",expression:"P = VI = I²R"),
                FormulaPreset(name:"Capacitive reactance",expression:"Xc = 1 / 2πfC"),
                FormulaPreset(name:"Inductive reactance",expression:"XL = 2πfL"),
                FormulaPreset(name:"Resonance",expression:"f₀ = 1 / 2π√LC"),
                FormulaPreset(name:"RC time constant",expression:"τ = RC"),
                FormulaPreset(name:"Voltage divider",expression:"Vout = Vin × R₂ / (R₁ + R₂)"),
                FormulaPreset(name:"Non-inverting op-amp",expression:"Av = 1 + Rf/Rin")
            ]
            ForEach(formulas.filter { formulaQuery.isEmpty || $0.name.localizedCaseInsensitiveContains(formulaQuery) || $0.expression.localizedCaseInsensitiveContains(formulaQuery) }) { f in
                HStack { VStack(alignment:.leading) { Text(f.name).font(.headline); Text(f.expression).font(.title3.monospaced()).foregroundStyle(gold) }; Spacer(); Button("Save") { store.create(kind:"formula",title:f.name,body:f.expression,tags:"subject:engineering") } }
                    .padding(12).background(panel,in:RoundedRectangle(cornerRadius:12))
            }
            Spacer()
        }.padding(24)
    }
    private var converter: some View {
        VStack(alignment:.leading,spacing:14) {
            Text("Engineering unit converter").font(.largeTitle.bold())
            TextField("Value",text:$converterValue).textFieldStyle(.roundedBorder).frame(maxWidth:250)
            Picker("From",selection:$converterUnit) { ForEach(["mA","A","kA","mV","V","kV","Ω","kΩ","MΩ","nF","µF","mF","F"],id:\.self) { Text($0) } }.frame(maxWidth:260)
            Picker("To",selection:$converterTarget) { ForEach(["mA","A","kA","mV","V","kV","Ω","kΩ","MΩ","nF","µF","mF","F"],id:\.self) { Text($0) } }.frame(maxWidth:260)
            Text(convertedValue).font(.system(size:34,weight:.medium,design:.rounded)).foregroundStyle(gold)
            Text("Conversions are performed within matching electrical dimensions.").foregroundStyle(.secondary)
            Spacer()
        }.padding(24)
    }
    private var convertedValue: String {
        guard let v=Double(converterValue), let a=scale[converterUnit], let b=scale[converterTarget], a.group==b.group else { return "—" }
        return String(format:"%.8g %@",v*a.factor/b.factor,converterTarget)
    }
    private var scale: [String: (factor: Double, group: String)] {
        [
            "mA": (0.001, "I"), "A": (1, "I"), "kA": (1000, "I"),
            "mV": (0.001, "V"), "V": (1, "V"), "kV": (1000, "V"),
            "Ω": (1, "R"), "kΩ": (1000, "R"), "MΩ": (1e6, "R"),
            "nF": (1e-9, "C"), "µF": (1e-6, "C"), "mF": (1e-3, "C"), "F": (1, "C")
        ]
    }
    private var trashSheet: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Recently deleted").font(.title2.bold())
            let deleted = store.items.filter { $0.deletedAt != nil }.sorted { ($0.deletedAt ?? 0) > ($1.deletedAt ?? 0) }
            if deleted.isEmpty {
                VStack(spacing: 8) { Image(systemName: "trash").font(.largeTitle); Text("Trash is empty").font(.headline); Text("Deleted notes will appear here.").foregroundStyle(.secondary) }.frame(maxWidth: .infinity, minHeight: 160)
            } else {
                HStack {
                    Button(trashSelectedIDs.count == deleted.count ? "Clear selection" : "Select all") {
                        trashSelectedIDs = trashSelectedIDs.count == deleted.count ? [] : Set(deleted.map { $0.id })
                    }
                    Text(String(trashSelectedIDs.count) + " selected").font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    Button("Restore") {
                        deleted.filter { trashSelectedIDs.contains($0.id) }.forEach { store.restoreFromTrash($0) }
                        trashSelectedIDs = []
                    }.disabled(trashSelectedIDs.isEmpty)
                    Button(role: .destructive) {
                        deleted.filter { trashSelectedIDs.contains($0.id) }.forEach { store.deletePermanently($0) }
                        trashSelectedIDs = []
                    } label: { Text("Delete") }.disabled(trashSelectedIDs.isEmpty)
                }
                List(deleted) { item in
                    HStack {
                        Toggle(isOn: Binding(
                            get: { trashSelectedIDs.contains(item.id) },
                            set: { selected in if selected { trashSelectedIDs.insert(item.id) } else { trashSelectedIDs.remove(item.id) } }
                        )) { EmptyView() }.labelsHidden()
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title)
                            Text(item.kind.capitalized).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button("Restore") { store.restoreFromTrash(item); trashSelectedIDs.remove(item.id) }
                        Button(role: .destructive) { store.deletePermanently(item); trashSelectedIDs.remove(item.id) } label: { Image(systemName: "trash.slash") }
                    }
                }
            }
        }
        .padding(20).frame(minWidth: 520, minHeight: 360)
    }
    private func historySheet(_ item: ScribeItem) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Version history").font(.title2.bold())
            let entries = store.snapshots[item.id] ?? []
            if entries.isEmpty {
                VStack(spacing: 8) { Image(systemName: "clock.arrow.circlepath").font(.largeTitle); Text("No previous versions").font(.headline); Text("Previous note versions will appear here after edits.").foregroundStyle(.secondary) }.frame(maxWidth: .infinity, minHeight: 160)
            } else {
                List(Array(entries.enumerated()), id: \.offset) { pair in
                    let entry = pair.element
                    VStack(alignment: .leading, spacing: 6) {
                        Text(Date(timeIntervalSince1970: Double(entry.savedAt) / 1000), style: .date)
                            .font(.caption).foregroundStyle(.secondary)
                        Text(entry.body).lineLimit(3)
                        HStack { Spacer(); Button("Restore this version") {
                            store.restoreVersion(item, entry: entry)
                            historyItem = nil
                        } }
                    }.padding(.vertical, 4)
                }
            }
        }
        .padding(20).frame(minWidth: 520, minHeight: 360)
    }

    private var backup: some View {
        VStack(alignment:.leading,spacing:16) {
            Text("Backup & settings").font(.largeTitle.bold())
            Text("Scribe stores notes locally and can optionally sync your library through your own Supabase project.")
            Button { showingExport=true } label: { Label("Export Scribe JSON backup",systemImage:"square.and.arrow.up") }.buttonStyle(.borderedProminent).tint(gold)
            Button { showingImport=true } label: { Label("Restore Scribe JSON backup",systemImage:"square.and.arrow.down") }
            Button { showingTrash = true } label: { Label("Recently deleted", systemImage: "trash") }
            GroupBox("Supabase cloud sync") {
                VStack(alignment: .leading, spacing: 9) {
                    TextField("Supabase project URL", text: $cloudURL)
                        .textFieldStyle(.roundedBorder)
                    SecureField("Supabase anon / publishable key", text: $cloudAnonKey)
                        .textFieldStyle(.roundedBorder)
                    TextField("Account email", text: $cloudEmail)
                        .textFieldStyle(.roundedBorder)
                    SecureField("Account password", text: $cloudPassword)
                        .textFieldStyle(.roundedBorder)
                    HStack {
                        Button("Disable automatic sync") {
                            store.disableCloudSync()
                            cloudStatus = "Automatic sync disabled."
                        }
                        .buttonStyle(.bordered).disabled(cloudBusy)
                        Button {
                            cloudBusy = true
                            cloudStatus = "Connecting and syncing…"
                            Task {
                                do {
                                    cloudStatus = try await store.syncSupabase(url: cloudURL, anonKey: cloudAnonKey, email: cloudEmail, password: cloudPassword)
                                } catch {
                                    cloudStatus = "Sync failed: " + error.localizedDescription
                                }
                                cloudPassword = ""
                                cloudBusy = false
                            }
                        } label: {
                            if cloudBusy { ProgressView().controlSize(.small) } else { Label("Sync now", systemImage: "arrow.triangle.2.circlepath") }
                        }
                        .buttonStyle(.borderedProminent).tint(gold).disabled(cloudBusy)
                        Text(cloudStatus).font(.caption).foregroundStyle(.secondary)
                    }
                    Text("Run supabase/schema.sql in your project first. After sign-in, Scribe syncs every 6 hours while the app is open. The refresh session is stored in macOS Keychain; your password is not saved.")
                        .font(.caption).foregroundStyle(.secondary)
                }.padding(4)
            }
            Button {
                let active = store.items.filter { $0.deletedAt == nil }
                let missing = active.filter { !$0.uri.isEmpty && !FileManager.default.fileExists(atPath: $0.uri) }.count
                let keys = active.map { String($0.parentId ?? 0) + "|" + $0.kind + "|" + $0.title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
                let groups = Dictionary(grouping: keys, by: { $0 })
                let duplicates = groups.values.reduce(0) { $0 + max(0, $1.count - 1) }
                integrityReport = "Checked " + String(store.items.count) + " records.\n\nMissing attachments: " + String(missing) + "\nPossible duplicates: " + String(duplicates) + "\n\nNo data was changed."
                showingIntegrityReport = true
            } label: { Label("Check library integrity", systemImage: "checkmark.shield") }
            Text("The macOS app includes the topic-first notebook, Markdown editing, in-topic search, flashcards, focus mode, academic tools, PDF annotations/OCR, attachments, trash and version history. Cloud sync uses your own Supabase project.").foregroundStyle(.secondary)
            Spacer()
        }.padding(24)
    }
}

private struct FormulaPreset: Identifiable { let name: String; let expression: String; var id: String { name } }

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data:Data) { self.data=data }
    init(configuration:ReadConfiguration) throws { data=configuration.file.regularFileContents ?? Data("[]".utf8) }
    func fileWrapper(configuration:WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents:data) }
}


private struct FlashcardEditSheet: View {
    let card: ScribeItem
    let onSave: (String, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var question: String
    @State private var answer: String
    @State private var deck: String

    init(card: ScribeItem, onSave: @escaping (String, String, String) -> Void) {
        self.card = card
        self.onSave = onSave
        _question = State(initialValue: card.title)
        _answer = State(initialValue: card.body)
        let parts = card.tags.split(separator: ";")
        let tag = parts.first(where: { $0.hasPrefix("deck:") }) ?? parts.first(where: { $0.hasPrefix("subject:") })
        _deck = State(initialValue: tag.map { String($0.split(separator: ":", maxSplits: 1).last ?? "General") } ?? "General")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Edit flashcard").font(.title2.bold())
            TextField("Question / front", text: $question).textFieldStyle(.roundedBorder)
            TextEditor(text: $answer).frame(minHeight: 120)
            TextField("Deck / subject", text: $deck).textFieldStyle(.roundedBorder)
            HStack { Spacer(); Button("Cancel") { dismiss() }; Button("Save changes") {
                let safeDeck = deck.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ";", with: " ")
                let retained = card.tags.split(separator: ";").map(String.init).filter { !$0.hasPrefix("deck:") && !$0.hasPrefix("subject:") }
                onSave(question.trimmingCharacters(in: .whitespacesAndNewlines), answer, (retained + ["deck:" + (safeDeck.isEmpty ? "General" : safeDeck)]).joined(separator: ";"))
                dismiss()
            }.disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty).buttonStyle(.borderedProminent) }
        }.padding(22).frame(width: 440, height: 340)
    }
}

private struct FlashcardDeckManagerSheet: View {
    let deck: String
    let cards: [ScribeItem]
    let availableDecks: [String]
    let onRename: (String, [ScribeItem]) -> Void
    let onMerge: (String, [ScribeItem]) -> Void
    let onDelete: ([ScribeItem]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var newName: String
    @State private var target: String

    init(deck: String, cards: [ScribeItem], availableDecks: [String], onRename: @escaping (String, [ScribeItem]) -> Void, onMerge: @escaping (String, [ScribeItem]) -> Void, onDelete: @escaping ([ScribeItem]) -> Void) {
        self.deck = deck
        self.cards = cards
        self.availableDecks = availableDecks
        self.onRename = onRename
        self.onMerge = onMerge
        self.onDelete = onDelete
        _newName = State(initialValue: deck)
        _target = State(initialValue: availableDecks.first(where: { $0 != deck }) ?? "General")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Manage deck").font(.title2.bold())
            Text(deck + " · " + String(cards.count) + " cards").foregroundStyle(.secondary)
            TextField("New deck name", text: $newName).textFieldStyle(.roundedBorder)
            Button("Rename deck") {
                onRename(newName.trimmingCharacters(in: .whitespacesAndNewlines), cards)
                dismiss()
            }.disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || newName == deck)
            if !availableDecks.filter({ $0 != deck }).isEmpty {
                Picker("Merge into", selection: $target) {
                    ForEach(availableDecks.filter { $0 != deck }, id: \.self) { Text($0).tag($0) }
                }
                Button("Merge deck") { onMerge(target, cards); dismiss() }
            }
            Divider()
            Button("Delete deck and its cards", role: .destructive) { onDelete(cards); dismiss() }
            HStack { Spacer(); Button("Close") { dismiss() } }
        }.padding(22).frame(width: 420)
    }
}


private struct LatexPreviewView: NSViewRepresentable {
    let source: String
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeNSView(context: Context) -> WKWebView {
        let view = WKWebView(frame: .zero)
        view.setValue(false, forKey: "drawsBackground")
        let html = latexPreviewHTML(source)
        context.coordinator.lastHTML = html
        view.loadHTMLString(html, baseURL: URL(string: "https://scribe.local/"))
        return view
    }
    func updateNSView(_ view: WKWebView, context: Context) {
        let html = latexPreviewHTML(source)
        guard context.coordinator.lastHTML != html else { return }
        context.coordinator.lastHTML = html
        view.loadHTMLString(html, baseURL: URL(string: "https://scribe.local/"))
    }
    final class Coordinator { var lastHTML = "" }
}

private func latexPreviewHTML(_ source: String) -> String {
    let safe = source
        .replacingOccurrences(of: "&", with: "&amp;")
        .replacingOccurrences(of: "<", with: "&lt;")
        .replacingOccurrences(of: ">", with: "&gt;")
    return """
    <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
    :root{color-scheme:light dark}body{margin:0;padding:18px;background:transparent;color:CanvasText;font:15px/1.65 -apple-system,BlinkMacSystemFont,sans-serif}
    #content{white-space:pre-wrap;overflow-wrap:anywhere}.MathJax{color:inherit!important}
    mjx-container[display="true"]{max-width:100%;overflow-x:auto;overflow-y:hidden;padding:8px 0}
    #status{font-size:11px;opacity:.65;margin-top:14px}
    </style>
    <script>window.MathJax={tex:{inlineMath:[['\\\\(','\\\\)'],['$','$']],displayMath:[['\\\\[','\\\\]'],['$$','$$']],processEscapes:true,packages:{'[+]':['ams']}},options:{skipHtmlTags:['script','noscript','style','textarea','pre','code']}};</script>
    <script defer src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js" onerror="document.getElementById('status').textContent='Math rendering needs an internet connection. Your LaTeX source is unchanged.'"></script>
    </head><body><div id="content">\(safe)</div><div id="status">Rendering mathematics…</div>
    <script>window.addEventListener('load',async()=>{const node=document.getElementById('content');if(window.markdownit){const raw=node.textContent;node.innerHTML=window.markdownit({html:false,linkify:false,typographer:true}).render(raw);}else{document.getElementById('status').textContent='Markdown renderer unavailable; showing source text.';}try{if(window.MathJax&&MathJax.startup){await MathJax.startup.promise;await MathJax.typesetPromise([node]);document.getElementById('status').textContent='Markdown and LaTeX preview';}}catch(e){document.getElementById('status').textContent='Some equations could not be rendered.';}});</script>
    </body></html>
    """
}
