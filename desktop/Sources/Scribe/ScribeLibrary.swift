import Foundation
import SwiftUI
import UniformTypeIdentifiers

struct ScribeNode: Codable, Identifiable, Hashable {
    var id: UUID
    var parentID: UUID?
    var kind: String
    var title: String
    var body: String
    var uri: String
    var tags: String
    var bookmarked: Bool
    var position: Int
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    init(id: UUID = UUID(), parentID: UUID? = nil, kind: String, title: String,
         body: String = "", uri: String = "", tags: String = "", bookmarked: Bool = false,
         position: Int = 0, createdAt: Date = .now, updatedAt: Date = .now, deletedAt: Date? = nil) {
        self.id = id; self.parentID = parentID; self.kind = kind; self.title = title
        self.body = body; self.uri = uri; self.tags = tags; self.bookmarked = bookmarked
        self.position = position; self.createdAt = createdAt; self.updatedAt = updatedAt
        self.deletedAt = deletedAt
    }
}

struct ScribeSnapshot: Codable, Identifiable {
    var id = UUID()
    var itemID: UUID
    var title: String
    var body: String
    var savedAt: Date = .now
}

struct ScribeAttachmentBackup: Codable {
    var nodeID: UUID
    var filename: String
    var mimeType: String
    var dataBase64: String
}

struct ScribeBackup: Codable {
    var schemaVersion = 1
    var exportedAt = Date.now
    var items: [ScribeNode]
    var snapshots: [ScribeSnapshot]
    var attachments: [ScribeAttachmentBackup] = []
}

@MainActor
final class ScribeLibrary: ObservableObject {
    @Published private(set) var nodes: [ScribeNode] = []
    @Published private(set) var snapshots: [ScribeSnapshot] = []
    @Published var lastError: String?
    private let root: URL
    private let fileURL: URL

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        root = support.appendingPathComponent("Scribe", isDirectory: true)
        fileURL = root.appendingPathComponent("library.json")
        do { try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true); load() }
        catch { lastError = error.localizedDescription }
    }

    var courses: [ScribeNode] { children(of: nil) }
    var visibleNodes: [ScribeNode] { nodes.filter { $0.deletedAt == nil } }
    var bookmarks: [ScribeNode] { visibleNodes.filter(\.bookmarked).sorted { $0.updatedAt > $1.updatedAt } }
    var trash: [ScribeNode] { nodes.filter { $0.deletedAt != nil }.sorted { ($0.deletedAt ?? .distantPast) > ($1.deletedAt ?? .distantPast) } }

    func children(of parent: UUID?) -> [ScribeNode] {
        visibleNodes.filter { $0.parentID == parent }.sorted {
            $0.position == $1.position ? $0.createdAt < $1.createdAt : $0.position < $1.position
        }
    }
    func node(_ id: UUID) -> ScribeNode? { nodes.first { $0.id == id } }
    func search(_ query: String) -> [ScribeNode] {
        guard !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }
        return visibleNodes.filter {
            $0.title.localizedCaseInsensitiveContains(query) ||
            $0.body.localizedCaseInsensitiveContains(query) ||
            $0.tags.localizedCaseInsensitiveContains(query)
        }.sorted { $0.updatedAt > $1.updatedAt }
    }

    func add(kind: String, parent: UUID? = nil, title: String? = nil, body: String = "", uri: String = "", tags: String = "") -> UUID {
        let defaults = ["course":"Untitled course","chapter":"Untitled chapter","topic":"Untitled topic","page":"Untitled page","block":"New block","formula":"New formula","question":"New question","flashcard":"New flashcard","diagram":"New diagram"]
        let item = ScribeNode(parentID: parent, kind: kind, title: title ?? defaults[kind, default: "Untitled"], body: body,
                              uri: uri, tags: tags, position: children(of: parent).count)
        nodes.append(item); save(); return item.id
    }

    func update(_ item: ScribeNode) {
        guard let index = nodes.firstIndex(where: { $0.id == item.id }) else { return }
        var revised = item; revised.updatedAt = .now
        if nodes[index].body != revised.body || nodes[index].title != revised.title {
            snapshots.append(ScribeSnapshot(itemID: item.id, title: nodes[index].title, body: nodes[index].body))
        }
        nodes[index] = revised; save()
    }

    func toggleBookmark(_ id: UUID) {
        guard var item = node(id) else { return }; item.bookmarked.toggle(); update(item)
    }

    func move(_ id: UUID, offset: Int) {
        guard let item = node(id) else { return }
        var siblings = children(of: item.parentID)
        guard let index = siblings.firstIndex(where: { $0.id == id }) else { return }
        let target = min(max(index + offset, 0), siblings.count - 1)
        guard target != index else { return }
        siblings.swapAt(index, target)
        for (position, sibling) in siblings.enumerated() {
            if let i = nodes.firstIndex(where: { $0.id == sibling.id }) { nodes[i].position = position; nodes[i].updatedAt = .now }
        }
        save()
    }

    func duplicate(_ id: UUID) -> UUID? {
        guard let source = node(id), source.deletedAt == nil else { return nil }
        func clone(_ original: ScribeNode, parent: UUID?, rootCopy: Bool) -> UUID {
            let newID = UUID()
            var copy = original
            copy.id = newID; copy.parentID = parent
            if rootCopy { copy.title += " copy" }
            copy.position = children(of: parent).count
            copy.createdAt = .now; copy.updatedAt = .now; copy.deletedAt = nil
            nodes.append(copy)
            let nested = children(of: original.id)
            for child in nested { _ = clone(child, parent: newID, rootCopy: false) }
            return newID
        }
        let newID = clone(source, parent: source.parentID, rootCopy: true); save(); return newID
    }

    func splitPage(_ id: UUID) {
        guard let page = node(id), page.kind == "page" else { return }
        let blocks = children(of: id)
        guard blocks.count >= 2 else { return }
        let secondPageID = UUID()
        let secondPage = ScribeNode(id: secondPageID, parentID: page.parentID, kind: "page",
                                    title: page.title + " · Part 2", position: children(of: page.parentID).count)
        nodes.append(secondPage)
        for (offset, block) in blocks.dropFirst((blocks.count + 1) / 2).enumerated() {
            if let index = nodes.firstIndex(where: { $0.id == block.id }) {
                nodes[index].parentID = secondPageID
                nodes[index].position = offset
                nodes[index].updatedAt = .now
            }
        }
        save()
    }

    func mergePageWithNext(_ id: UUID) {
        guard let page = node(id), page.kind == "page" else { return }
        let siblings = children(of: page.parentID).filter { $0.kind == "page" }
        guard let index = siblings.firstIndex(where: { $0.id == id }), siblings.indices.contains(index + 1) else { return }
        let next = siblings[index + 1]
        var position = children(of: id).count
        for block in children(of: next.id) {
            if let blockIndex = nodes.firstIndex(where: { $0.id == block.id }) {
                nodes[blockIndex].parentID = id
                nodes[blockIndex].position = position
                nodes[blockIndex].updatedAt = .now
                position += 1
            }
        }
        if let nextIndex = nodes.firstIndex(where: { $0.id == next.id }) { nodes[nextIndex].deletedAt = .now }
        save()
    }

    func moveToTrash(_ id: UUID) {
        let now = Date()
        func mark(_ target: UUID) {
            guard let index = nodes.firstIndex(where: { $0.id == target }) else { return }
            nodes[index].deletedAt = now; nodes[index].updatedAt = now
            let childIDs = nodes.filter { $0.parentID == target && $0.deletedAt == nil }.map(\.id)
            childIDs.forEach(mark)
        }
        mark(id); save()
    }

    func restore(_ id: UUID) {
        func revive(_ target: UUID) {
            guard let index = nodes.firstIndex(where: { $0.id == target }) else { return }
            nodes[index].deletedAt = nil; nodes[index].updatedAt = .now
            let childIDs = nodes.filter { $0.parentID == target && $0.deletedAt != nil }.map(\.id)
            childIDs.forEach(revive)
        }
        revive(id); save()
    }

    func deleteForever(_ id: UUID) {
        let descendants = nodes.filter { $0.parentID == id }.map(\.id)
        descendants.forEach(deleteForever)
        nodes.removeAll { $0.id == id }
        snapshots.removeAll { $0.itemID == id }
        save()
    }

    func importAttachment(from source: URL, parent: UUID) throws {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        let folder = root.appendingPathComponent("attachments", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let ext = source.pathExtension
        let target = folder.appendingPathComponent(UUID().uuidString).appendingPathExtension(ext)
        try FileManager.default.copyItem(at: source, to: target)
        let type = UTType(filenameExtension: ext)
        let kind = type?.conforms(to: .image) == true ? "image" : (type?.conforms(to: .pdf) == true ? "pdf" : "file")
        _ = add(kind: "block", parent: parent, title: source.deletingPathExtension().lastPathComponent,
                body: "Attached \(kind)", uri: target.path, tags: "type:\(kind)")
    }

    func backupData() throws -> Data {
        let attachments: [ScribeAttachmentBackup] = nodes.compactMap { item in
            guard !item.uri.isEmpty, let bytes = try? Data(contentsOf: URL(fileURLWithPath: item.uri)) else { return nil }
            return ScribeAttachmentBackup(nodeID: item.id, filename: URL(fileURLWithPath: item.uri).lastPathComponent,
                                          mimeType: "application/octet-stream", dataBase64: bytes.base64EncodedString())
        }
        return try JSONEncoder.scribe.encode(ScribeBackup(items: nodes, snapshots: snapshots, attachments: attachments))
    }
    func restoreBackup(_ data: Data) throws {
        let backup = try JSONDecoder.scribe.decode(ScribeBackup.self, from: data)
        guard backup.schemaVersion == 1 else { throw CocoaError(.fileReadCorruptFile) }
        // Validate references and attachment payloads before mutating the live library.
        let ids = Set(backup.items.map(\.id))
        guard backup.items.allSatisfy({ $0.parentID == nil || ids.contains($0.parentID!) }),
              backup.attachments.allSatisfy({ ids.contains($0.nodeID) && Data(base64Encoded: $0.dataBase64) != nil }) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        let attachmentFolder = root.appendingPathComponent("attachments", isDirectory: true)
        try FileManager.default.createDirectory(at: attachmentFolder, withIntermediateDirectories: true)
        var restoredItems = backup.items
        for attachment in backup.attachments {
            guard let bytes = Data(base64Encoded: attachment.dataBase64),
                  let index = restoredItems.firstIndex(where: { $0.id == attachment.nodeID }) else { continue }
            let safeName = URL(fileURLWithPath: attachment.filename).lastPathComponent
            let target = attachmentFolder.appendingPathComponent("\(attachment.nodeID.uuidString)-\(safeName)")
            try bytes.write(to: target, options: .atomic)
            restoredItems[index].uri = target.path
        }
        nodes = restoredItems; snapshots = backup.snapshots; save()
    }

    func history(for id: UUID) -> [ScribeSnapshot] { snapshots.filter { $0.itemID == id }.sorted { $0.savedAt > $1.savedAt } }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL) else { return }
        do {
            let backup = try JSONDecoder.scribe.decode(ScribeBackup.self, from: data)
            nodes = backup.items; snapshots = backup.snapshots
        } catch { lastError = "Your library could not be read: \(error.localizedDescription)" }
    }
    private func save() {
        do { try JSONEncoder.scribe.encode(ScribeBackup(items: nodes, snapshots: snapshots)).write(to: fileURL, options: .atomic); lastError = nil }
        catch { lastError = "Save failed: \(error.localizedDescription)" }
    }
}

extension JSONEncoder {
    static var scribe: JSONEncoder { let e = JSONEncoder(); e.outputFormatting = [.prettyPrinted, .sortedKeys]; e.dateEncodingStrategy = .iso8601; return e }
}
extension JSONDecoder {
    static var scribe: JSONDecoder { let d = JSONDecoder(); d.dateDecodingStrategy = .iso8601; return d }
}
