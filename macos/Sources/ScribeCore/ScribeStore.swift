import Foundation
import SwiftUI
import Combine

public func nextReviewIntervalDays(_ currentDays: Int64, rating: Int) -> Int64 {
    let current = max(1, currentDays)
    let next: Int64
    switch rating {
    case 0: next = 1
    case 1: next = Int64(ceil(Double(current) * 1.2))
    case 2: next = current * 2
    case 3: next = current * 3
    default: next = current
    }
    return min(365, max(1, next))
}

public struct ScribeItem: Identifiable, Codable, Hashable {
    public var id: Int64
    public var parentId: Int64?
    public var kind: String
    public var title: String
    public var body: String
    public var uri: String
    public var tags: String
    public var bookmarked: Bool
    public var position: Int
    public var createdAt: Int64
    public var updatedAt: Int64
    public var deletedAt: Int64?
    public var interval: Int64 { Int64(tags.range(of: #"interval:[0-9]+"#, options: .regularExpression).map { String(tags[$0].dropFirst(9)) } ?? "1") ?? 1 }

    enum CodingKeys: String, CodingKey { case id, parentId, kind, title, body, uri, tags, bookmarked, position, createdAt, updatedAt, deletedAt }
    public init(id: Int64, parentId: Int64? = nil, kind: String, title: String, body: String = "", uri: String = "", tags: String = "", bookmarked: Bool = false, position: Int = 0, createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000), updatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000), deletedAt: Int64? = nil) {
        self.id=id; self.parentId=parentId; self.kind=kind; self.title=title; self.body=body; self.uri=uri; self.tags=tags; self.bookmarked=bookmarked; self.position=position; self.createdAt=createdAt; self.updatedAt=updatedAt; self.deletedAt=deletedAt
    }
}

public struct ScribeHistoryEntry: Codable, Hashable {
    public var title: String
    public var body: String
    public var savedAt: Int64
}
struct ScribeHistoryArchive: Codable {
    var itemId: Int64
    var entries: [ScribeHistoryEntry]
}
struct BackupRecord: Codable {
    public var id: Int64; var parentId: Int64?; var kind: String; var title: String; var body: String; var uri: String; var tags: String
    public var bookmarked: Bool; var position: Int; var createdAt: Int64; var updatedAt: Int64; var deletedAt: Int64?
    var history: [ScribeHistoryEntry]? = nil
    var attachmentBase64: String? = nil
}
enum CodableValue: Codable { case string(String), integer(Int64), bool(Bool), null
    init(from decoder: Decoder) throws { let c = try decoder.singleValueContainer(); if c.decodeNil() { self = .null } else if let v = try? c.decode(Bool.self) { self = .bool(v) } else if let v = try? c.decode(Int64.self) { self = .integer(v) } else { self = .string(try c.decode(String.self)) } }
    func encode(to encoder: Encoder) throws { var c = encoder.singleValueContainer(); switch self { case .string(let v): try c.encode(v); case .integer(let v): try c.encode(v); case .bool(let v): try c.encode(v); case .null: try c.encodeNil() } }
}

public enum ScribeTool: String, CaseIterable, Identifiable, Hashable { case library = "Library", flashcards = "Flashcards", focus = "Focus", formulas = "Formula shelf", converter = "Unit converter", settings = "Backup & settings", academic = "Academic tools"; public var id: String { rawValue } }

@MainActor public final class ScribeStore: ObservableObject {
    @Published public var items: [ScribeItem] = []
    @Published public var snapshots: [Int64: [ScribeHistoryEntry]] = [:]
    @Published public var selectedID: Int64?
    @Published public var activeTool: ScribeTool = .library
    @Published public var focusSearch = false
    @Published public var searchText = ""
    @Published public var focusMinutes = 25
    @Published public var remaining = 25 * 60
    @Published public var isRunning = false
    @Published public var sessionLabel = "Focused study"
    @Published public var completedSessions = 0
    private var timer: Timer?
    private var nextID: Int64 = 1
    private let fileURL: URL
    private let historyURL: URL

    public init() {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let folder = root.appendingPathComponent("Scribe", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        fileURL = folder.appendingPathComponent("library.json")
        historyURL = folder.appendingPathComponent("history.json")
        if let data = try? Data(contentsOf: fileURL), let decoded = try? JSONDecoder().decode([ScribeItem].self, from: data) {
            items = decoded; nextID = (decoded.map(\.id).max() ?? 0) + 1
        }
        if let data = try? Data(contentsOf: historyURL), let archives = try? JSONDecoder().decode([ScribeHistoryArchive].self, from: data) {
            snapshots = Dictionary(uniqueKeysWithValues: archives.map { ($0.itemId, $0.entries) })
        }
    }
    public var selected: ScribeItem? { items.first { $0.id == selectedID } }
    public var visibleItems: [ScribeItem] {
        let active = items.filter { $0.deletedAt == nil }
        guard !searchText.isEmpty else { return active }
        return active.filter { $0.title.localizedCaseInsensitiveContains(searchText) || $0.body.localizedCaseInsensitiveContains(searchText) || $0.tags.localizedCaseInsensitiveContains(searchText) }
    }
    public func children(of parent: Int64?) -> [ScribeItem] { visibleItems.filter { $0.parentId == parent }.sorted { $0.position < $1.position } }
    public func create(kind: String, title: String, parentId: Int64? = nil, body: String = "", tags: String = "", uri: String = "", switchToLibrary: Bool = true) {
        let siblings = children(of: parentId)
        let item = ScribeItem(id: nextID, parentId: parentId, kind: kind, title: title, body: body, uri: uri, tags: tags, position: siblings.count)
        nextID += 1; items.append(item); selectedID = item.id; if switchToLibrary { activeTool = .library }; persist()
    }
    public func update(_ item: ScribeItem, title: String? = nil, body: String? = nil, tags: String? = nil, bookmarked: Bool? = nil) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let old = items[index]
        let nextBody = body ?? old.body
        if nextBody != old.body {
            var history = snapshots[old.id, default: []]
            history.insert(ScribeHistoryEntry(title: old.title, body: old.body, savedAt: now), at: 0)
            snapshots[old.id] = Array(history.prefix(30))
        }
        items[index].title = title ?? old.title
        items[index].body = nextBody
        items[index].tags = tags ?? old.tags
        items[index].bookmarked = bookmarked ?? old.bookmarked
        items[index].updatedAt = now
        persist()
    }
    public func duplicateTree(_ item: ScribeItem) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var rootCopyID: Int64?
        func clone(_ source: ScribeItem, parentId: Int64?, isRoot: Bool) {
            var copy = source
            copy.id = nextID
            nextID += 1
            copy.parentId = parentId
            copy.title = isRoot ? source.title + " copy" : source.title
            copy.position = children(of: parentId).count
            copy.createdAt = now
            copy.updatedAt = now
            copy.deletedAt = nil
            items.append(copy)
            if isRoot { rootCopyID = copy.id }
            let nested = items.filter { $0.parentId == source.id && $0.deletedAt == nil }
                .sorted { $0.position < $1.position }
            for child in nested { clone(child, parentId: copy.id, isRoot: false) }
        }
        clone(item, parentId: item.parentId, isRoot: true)
        selectedID = rootCopyID
        persist()
    }

    public func move(_ item: ScribeItem, direction: Int) {
        guard direction == -1 || direction == 1 else { return }
        let siblings = children(of: item.parentId)
        guard let index = siblings.firstIndex(where: { $0.id == item.id }) else { return }
        let target = index + direction
        guard siblings.indices.contains(target),
              let currentIndex = items.firstIndex(where: { $0.id == siblings[index].id }),
              let targetIndex = items.firstIndex(where: { $0.id == siblings[target].id }) else { return }
        let currentPosition = items[currentIndex].position
        items[currentIndex].position = items[targetIndex].position
        items[targetIndex].position = currentPosition
        persist()
    }

    public func remove(_ item: ScribeItem) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        func mark(_ id: Int64) { for child in items where child.parentId == id && child.deletedAt == nil { mark(child.id) }; if let i = items.firstIndex(where: { $0.id == id }) { items[i].deletedAt = now } }
        mark(item.id); persist()
    }
    public func restoreFromTrash(_ item: ScribeItem) {
        func restore(_ id: Int64) {
            if let index = items.firstIndex(where: { $0.id == id }) {
                items[index].deletedAt = nil
            }
            let childIDs = items.filter { $0.parentId == id }.map(\.id)
            childIDs.forEach(restore)
        }
        restore(item.id)
        persist()
    }
    public func deletePermanently(_ item: ScribeItem) {
        func remove(_ id: Int64) {
            let childIDs = items.filter { $0.parentId == id }.map(\.id)
            childIDs.forEach(remove)
            items.removeAll { $0.id == id }
            snapshots.removeValue(forKey: id)
        }
        remove(item.id)
        persist()
    }
    public func restoreVersion(_ item: ScribeItem, entry: ScribeHistoryEntry) {
        update(item, title: entry.title, body: entry.body)
    }

    public func review(_ item: ScribeItem, rating: Int) {
        let next = nextReviewIntervalDays(item.interval, rating: rating)
        let tags = item.tags.replacingOccurrences(of: #"interval:[0-9]+"#, with: "interval:\(next)", options: .regularExpression)
        update(item, tags: tags.contains("interval:") ? tags : tags + ";interval:\(next)")
    }
    public func persist() {
        if let data = try? JSONEncoder().encode(items) { try? data.write(to: fileURL, options: .atomic) }
        let archives = snapshots.map { ScribeHistoryArchive(itemId: $0.key, entries: $0.value) }
        if let data = try? JSONEncoder().encode(archives) { try? data.write(to: historyURL, options: .atomic) }
    }
    public func backupData() throws -> Data {
        let records = items.map { item -> BackupRecord in
            var record = BackupRecord(id: item.id, parentId: item.parentId, kind: item.kind, title: item.title, body: item.body, uri: item.uri, tags: item.tags, bookmarked: item.bookmarked, position: item.position, createdAt: item.createdAt, updatedAt: item.updatedAt, deletedAt: item.deletedAt, history: snapshots[item.id] ?? [])
            if !item.uri.isEmpty, let attachment = try? Data(contentsOf: URL(fileURLWithPath: item.uri)) {
                record.attachmentBase64 = attachment.base64EncodedString()
            }
            return record
        }
        return try JSONEncoder().encode(records)
    }
    public func exportBackup(to url: URL) throws {
        try backupData().write(to: url, options: .atomic)
    }
    public func importBackup(from url: URL) throws {
        let records = try JSONDecoder().decode([BackupRecord].self, from: Data(contentsOf: url))
        let attachments = fileURL.deletingLastPathComponent().appendingPathComponent("attachments", isDirectory: true)
        try FileManager.default.createDirectory(at: attachments, withIntermediateDirectories: true)
        snapshots = Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0.history ?? []) })
        items = records.map { record in
            var restoredURI = record.uri
            if let encoded = record.attachmentBase64, let bytes = Data(base64Encoded: encoded) {
                let destination = attachments.appendingPathComponent(UUID().uuidString)
                if (try? bytes.write(to: destination, options: .atomic)) != nil { restoredURI = destination.path }
            }
            return ScribeItem(id: record.id, parentId: record.parentId, kind: record.kind, title: record.title, body: record.body, uri: restoredURI, tags: record.tags, bookmarked: record.bookmarked, position: record.position, createdAt: record.createdAt, updatedAt: record.updatedAt, deletedAt: record.deletedAt)
        }
        nextID = (items.map(\.id).max() ?? 0) + 1; persist()
    }
    public func startTimer() {
        guard !isRunning else { isRunning = false; timer?.invalidate(); return }
        isRunning = true
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if self.remaining > 0 { self.remaining -= 1 } else {
                    self.isRunning = false; self.timer?.invalidate(); self.completedSessions += 1
                    let session = ScribeItem(id: self.nextID, kind: "study_session", title: self.sessionLabel, body: "Completed a \(self.focusMinutes)-minute focus session.")
                    self.nextID += 1
                    self.items.append(session)
                    self.persist()
                }
            }
        }
    }
    public func resetTimer() { timer?.invalidate(); isRunning = false; remaining = focusMinutes * 60 }
    deinit { timer?.invalidate() }
}
