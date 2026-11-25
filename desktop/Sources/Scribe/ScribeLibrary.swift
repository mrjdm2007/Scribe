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
            let lastSnapshot = snapshots.filter { $0.itemID == item.id }.map(\\.savedAt).max()
            if lastSnapshot == nil || Date().timeIntervalSince(lastSnapshot!) >= 60 {
                snapshots.append(ScribeSnapshot(itemID: item.id, title: nodes[index].title, body: nodes[index].body))
            }
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
        let type = UTType(filenameExtension: ext)
        let kind: String
        if type?.conforms(to: .image) == true { kind = "image" }
        else if type?.conforms(to: .pdf) == true { kind = "pdf" }
        else { throw NSError(domain: "ScribeAttachments", code: 415, userInfo: [NSLocalizedDescriptionKey: "Scribe accepts photos and PDF files only."]) }
        let target = folder.appendingPathComponent(UUID().uuidString).appendingPathExtension(ext)
        try FileManager.default.copyItem(at: source, to: target)
        _ = add(kind: "block", parent: parent, title: source.deletingPathExtension().lastPathComponent,
                body: "Attached \(kind)", uri: target.path, tags: "type:\(kind)")
    }

    func backupData() throws -> Data {
        // Export the same JSON-array format used by Android so a backup can move between platforms.
        let portableIDs = Dictionary(uniqueKeysWithValues: nodes.enumerated().map { ($0.element.id, Int64($0.offset + 1)) })
        let rows: [[String: Any]] = try nodes.map { item in
            var row: [String: Any] = [
                "id": portableIDs[item.id] ?? 0,
                "kind": item.kind, "title": item.title, "body": item.body,
                "uri": item.uri, "tags": portableTags(item.tags, ids: portableIDs),
                "bookmarked": item.bookmarked, "position": item.position,
                "createdAt": Int64(item.createdAt.timeIntervalSince1970 * 1000),
                "updatedAt": Int64(item.updatedAt.timeIntervalSince1970 * 1000)
            ]
            if let parentID = item.parentID, let numericParent = portableIDs[parentID] {
                row["parentId"] = NSNumber(value: numericParent)
            } else { row["parentId"] = NSNull() }
            if let deletedAt = item.deletedAt {
                row["deletedAt"] = NSNumber(value: Int64(deletedAt.timeIntervalSince1970 * 1000))
            } else { row["deletedAt"] = NSNull() }
            if !item.uri.isEmpty {
                let fileURL = URL(fileURLWithPath: item.uri)
                let type = UTType(filenameExtension: fileURL.pathExtension)
                if type?.conforms(to: .image) == true || type?.conforms(to: .pdf) == true,
                   let bytes = try? Data(contentsOf: fileURL) {
                    row["attachmentBase64"] = bytes.base64EncodedString()
                }
            }
            row["history"] = snapshots.filter { $0.itemID == item.id }.map {
                ["title": $0.title, "body": $0.body, "savedAt": Int64($0.savedAt.timeIntervalSince1970 * 1000)] as [String: Any]
            }
            return row
        }
        return try JSONSerialization.data(withJSONObject: rows, options: [.prettyPrinted, .sortedKeys])
    }

    func restoreBackup(_ data: Data) throws {
        if let rows = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            try restoreAndroidBackup(rows)
            return
        }
        let backup = try JSONDecoder.scribe.decode(ScribeBackup.self, from: data)
        guard backup.schemaVersion == 1 else { throw CocoaError(.fileReadCorruptFile) }
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

    private func restoreAndroidBackup(_ rows: [[String: Any]]) throws {
        let numericIDs = rows.compactMap { ($0["id"] as? NSNumber)?.int64Value }
        guard numericIDs.count == rows.count, Set(numericIDs).count == numericIDs.count else {
            throw CocoaError(.fileReadCorruptFile)
        }
        let idMap = Dictionary(uniqueKeysWithValues: numericIDs.map { ($0, UUID()) })
        guard rows.allSatisfy({ row in
            if let parent = row["parentId"] as? NSNumber { return idMap[parent.int64Value] != nil }
            return true
        }) else { throw CocoaError(.fileReadCorruptFile) }

        let attachmentFolder = root.appendingPathComponent("attachments", isDirectory: true)
        try FileManager.default.createDirectory(at: attachmentFolder, withIntermediateDirectories: true)
        var restored: [ScribeNode] = []
        var restoredSnapshots: [ScribeSnapshot] = []
        for row in rows {
            let oldID = (row["id"] as? NSNumber)?.int64Value ?? 0
            guard let id = idMap[oldID] else { throw CocoaError(.fileReadCorruptFile) }
            let parentNumber = (row["parentId"] as? NSNumber)?.int64Value
            let created = (row["createdAt"] as? NSNumber)?.doubleValue ?? Date.now.timeIntervalSince1970 * 1000
            let updated = (row["updatedAt"] as? NSNumber)?.doubleValue ?? created
            let deleted = (row["deletedAt"] as? NSNumber)?.doubleValue
            let originalURI = row["uri"] as? String ?? ""
            var uri = ""
            if let encoded = row["attachmentBase64"] as? String, let bytes = Data(base64Encoded: encoded) {
                let name = URL(fileURLWithPath: originalURI).lastPathComponent
                let ext = URL(fileURLWithPath: name).pathExtension
                let type = UTType(filenameExtension: ext)
                if type?.conforms(to: .image) == true || type?.conforms(to: .pdf) == true {
                    let target = attachmentFolder.appendingPathComponent("\(id.uuidString)-\(name.isEmpty ? "attachment" : name)")
                    try bytes.write(to: target, options: .atomic)
                    uri = target.path
                }
            }
            var tags = row["tags"] as? String ?? ""
            if let targetToken = tags.split(separator: ";").first(where: { $0.hasPrefix("target:") }),
               let targetNumber = Int64(targetToken.dropFirst(7)), let targetUUID = idMap[targetNumber] {
                tags = tags.replacingOccurrences(of: "target:\(targetNumber)", with: "target:\(targetUUID.uuidString)")
            }
            var body = row["body"] as? String ?? ""
            if body.hasPrefix("topic-ref:"), let targetNumber = Int64(body.dropFirst(10)), let targetUUID = idMap[targetNumber] {
                body = "topic-ref:\(targetUUID.uuidString)"
            }
            restored.append(ScribeNode(
                id: id, parentID: parentNumber.flatMap { idMap[$0] },
                kind: row["kind"] as? String ?? "block", title: row["title"] as? String ?? "Untitled",
                body: body, uri: uri, tags: tags, bookmarked: row["bookmarked"] as? Bool ?? false,
                position: row["position"] as? Int ?? 0,
                createdAt: Date(timeIntervalSince1970: created / 1000),
                updatedAt: Date(timeIntervalSince1970: updated / 1000),
                deletedAt: deleted.map { Date(timeIntervalSince1970: $0 / 1000) }
            ))
            if let history = row["history"] as? [[String: Any]] {
                for entry in history {
                    let saved = (entry["savedAt"] as? NSNumber)?.doubleValue ?? created
                    restoredSnapshots.append(ScribeSnapshot(itemID: id, title: entry["title"] as? String ?? "",
                        body: entry["body"] as? String ?? "", savedAt: Date(timeIntervalSince1970: saved / 1000)))
                }
            }
        }
        nodes = restored; snapshots = restoredSnapshots; save()
    }

    private func portableTags(_ tags: String, ids: [UUID: Int64]) -> String {
        guard let token = tags.split(separator: ";").first(where: { $0.hasPrefix("target:") }),
              let uuid = UUID(uuidString: String(token.dropFirst(7))), let numeric = ids[uuid] else { return tags }
        return tags.replacingOccurrences(of: "target:\(uuid.uuidString)", with: "target:\(numeric)")
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
