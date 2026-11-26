import Foundation
import SwiftUI
import Combine
import UserNotifications

public func independentCopyTags(_ tags: String) -> String {
    tags.split(separator: ";").map(String.init)
        .filter { !$0.isEmpty && !$0.hasPrefix("cloud_id:") }
        .joined(separator: ";")
}

public func belongsToTrashBatch(_ candidateDeletedAt: Int64?, rootDeletedAt: Int64?) -> Bool {
    guard let candidateDeletedAt, let rootDeletedAt else { return false }
    return abs(Double(candidateDeletedAt) - Double(rootDeletedAt)) <= 5_000
}

public func shouldCreateSnapshot(previousBody: String, nextBody: String, latestSnapshotAt: Int64, now: Int64) -> Bool {
    previousBody != nextBody && (latestSnapshotAt <= 0 || now - latestSnapshotAt >= 60_000)
}

public func appendingTag(_ existingTags: String, tag rawTag: String) -> String {
    let clean = rawTag.replacingOccurrences(of: ";", with: " ").components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }.joined(separator: " ")
    guard !clean.isEmpty else { return existingTags }
    let tags = existingTags.split(separator: ";").map(String.init).filter { !$0.isEmpty }
    return tags.contains(clean) ? tags.joined(separator: ";") : (tags + [clean]).joined(separator: ";")
}

func backupValidationError(_ records: [BackupRecord]) -> String? {
    let ids = records.map(\.id)
    if ids.contains(where: { $0 <= 0 }) { return "Backup contains an invalid item ID." }
    if Set(ids).count != ids.count { return "Backup contains duplicate item IDs." }
    let byID = Dictionary(uniqueKeysWithValues: records.map { ($0.id, $0) })
    for record in records {
        if let parent = record.parentId, byID[parent] == nil {
            return "Backup contains an item with a missing parent."
        }
        var visited = Set<Int64>()
        var cursor: Int64? = record.id
        while let current = cursor {
            if !visited.insert(current).inserted { return "Backup contains a circular notebook hierarchy." }
            cursor = byID[current]?.parentId
        }
    }
    return nil
}

public func nextReviewIntervalDays(_ currentDays: Int64, rating: Int) -> Int64 {
    let current = min(365, max(1, currentDays))
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

public func flashcardReviewTags(_ tags: String, rating: Int, nowMillis: Int64) -> String {
    let parsedValue = tags.split(separator: ";").first(where: { $0.hasPrefix("interval:") }).flatMap { Double($0.dropFirst("interval:".count)) } ?? 1
    let oldValue = parsedValue.isFinite ? min(365, max(1, parsedValue)) : 1
    let days = nextReviewIntervalDays(Int64(ceil(oldValue)), rating: rating)
    let offset = days * 86_400_000
    let dueAt = nowMillis > Int64.max - offset ? Int64.max : nowMillis + offset
    let preserved = tags.split(separator: ";").map(String.init).filter { !$0.isEmpty && !$0.hasPrefix("interval:") && !$0.hasPrefix("dueAt:") && !$0.hasPrefix("ease:") && !$0.hasPrefix("lastReviewedAt:") }
    return (preserved + ["interval:\(days)", "dueAt:\(dueAt)", "lastReviewedAt:\(nowMillis)"]).joined(separator: ";")
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

public enum ScribeTool: String, CaseIterable, Identifiable, Hashable { case library = "Library", flashcards = "Flashcards", focus = "Focus", formulas = "Formula shelf", converter = "Unit converter", settings = "Backup & settings", academic = "Academic tools", workspace = "Study workspace"; public var id: String { rawValue } }

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
    private let deletionQueueURL: URL
    private var pendingCloudDeletions: [String: Int64] = [:]
    private var cloudSyncTimer: Timer?
    private var firebaseSyncTimer: Timer?

    public init() {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let folder = root.appendingPathComponent("Scribe", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        fileURL = folder.appendingPathComponent("library.json")
        historyURL = folder.appendingPathComponent("history.json")
        deletionQueueURL = folder.appendingPathComponent("cloud-deletions.json")
        if let data = try? Data(contentsOf: deletionQueueURL), let queued = try? JSONDecoder().decode([String: Int64].self, from: data) { pendingCloudDeletions = queued }
        if let data = try? Data(contentsOf: fileURL), let decoded = try? JSONDecoder().decode([ScribeItem].self, from: data) {
            items = decoded; nextID = (decoded.map(\.id).max() ?? 0) + 1
        }
        if let data = try? Data(contentsOf: historyURL), let archives = try? JSONDecoder().decode([ScribeHistoryArchive].self, from: data) {
            snapshots = Dictionary(uniqueKeysWithValues: archives.map { ($0.itemId, $0.entries) })
        }
        if ScribeCloudSessionVault.load() != nil {
            scheduleCloudSync()
            Task { @MainActor [weak self] in
                _ = try? await self?.syncSavedCloudSession()
            }
        }
        if ScribeFirebaseSessionVault.load() != nil {
            scheduleFirebaseSync()
            Task { @MainActor [weak self] in _ = try? await self?.syncSavedFirebase() }
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
        let old = items[index]
        let now = max(Int64(Date().timeIntervalSince1970 * 1000), old.updatedAt + 1)
        let nextBody = body ?? old.body
        let latestSnapshotAt = snapshots[old.id]?.first?.savedAt ?? 0
        if shouldCreateSnapshot(previousBody: old.body, nextBody: nextBody, latestSnapshotAt: latestSnapshotAt, now: now) {
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
            copy.tags = independentCopyTags(source.tags)
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

    public func splitPage(_ page: ScribeItem) {
        guard page.kind == "page" else { return }
        let blocks = children(of: page.id)
        guard blocks.count >= 2 else { return }
        let siblings = children(of: page.parentId)
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let newPage = ScribeItem(
            id: nextID, parentId: page.parentId, kind: "page",
            title: page.title + " · Part 2", position: siblings.count,
            createdAt: now, updatedAt: now
        )
        nextID += 1
        items.append(newPage)
        for (index, block) in blocks.dropFirst((blocks.count + 1) / 2).enumerated() {
            guard let itemIndex = items.firstIndex(where: { $0.id == block.id }) else { continue }
            items[itemIndex].parentId = newPage.id
            items[itemIndex].position = index
            items[itemIndex].updatedAt = now
        }
        selectedID = newPage.id
        persist()
    }

    public func mergePageWithNext(_ page: ScribeItem) {
        guard page.kind == "page" else { return }
        let siblings = children(of: page.parentId)
        guard let index = siblings.firstIndex(where: { $0.id == page.id }),
              siblings.indices.contains(index + 1) else { return }
        let next = siblings[index + 1]
        guard next.kind == "page" else { return }
        let existing = children(of: page.id)
        let incoming = children(of: next.id)
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        for (offset, block) in incoming.enumerated() {
            guard let itemIndex = items.firstIndex(where: { $0.id == block.id }) else { continue }
            items[itemIndex].parentId = page.id
            items[itemIndex].position = existing.count + offset
            items[itemIndex].updatedAt = now
        }
        if let nextIndex = items.firstIndex(where: { $0.id == next.id }) {
            items[nextIndex].deletedAt = now
            items[nextIndex].updatedAt = now
        }
        selectedID = page.id
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
        guard let rootDeletedAt = items.first(where: { $0.id == item.id })?.deletedAt ?? item.deletedAt else { return }
        func restore(_ id: Int64) {
            guard let index = items.firstIndex(where: { $0.id == id }),
                  belongsToTrashBatch(items[index].deletedAt, rootDeletedAt: rootDeletedAt) else { return }
            items[index].deletedAt = nil
            let childIDs = items.filter {
                $0.parentId == id && belongsToTrashBatch($0.deletedAt, rootDeletedAt: rootDeletedAt)
            }.map(\.id)
            childIDs.forEach(restore)
        }
        restore(item.id)
        persist()
    }
    public func deletePermanently(_ item: ScribeItem) {
        func remove(_ id: Int64) {
            let childIDs = items.filter { $0.parentId == id }.map(\.id)
            childIDs.forEach(remove)
            if let item = items.first(where: { $0.id == id }),
               let cloud = item.tags.split(separator: ";").first(where: { $0.hasPrefix("cloud_id:") }).map({ String($0.dropFirst(9)) }) {
                pendingCloudDeletions[cloud] = Int64(Date().timeIntervalSince1970 * 1000)
            }
            items.removeAll { $0.id == id }
            snapshots.removeValue(forKey: id)
        }
        remove(item.id)
        persist()
    }
    public func bulkMove(_ selected: [ScribeItem], to targetParentID: Int64?) {
        let movingIDs = Set(selected.map(\.id))
        if var cursor = targetParentID {
            var visited = Set<Int64>()
            while visited.insert(cursor).inserted {
                if movingIDs.contains(cursor) { return }
                guard let parent = items.first(where: { $0.id == cursor })?.parentId else { break }
                cursor = parent
            }
        }
        var position = children(of: targetParentID).count
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        for item in selected where item.deletedAt == nil {
            guard let index = items.firstIndex(where: { $0.id == item.id }) else { continue }
            items[index].parentId = targetParentID
            items[index].position = position
            items[index].updatedAt = now
            position += 1
        }
        persist()
    }
    public func bulkArchive(_ selected: [ScribeItem]) {
        for item in selected where item.deletedAt == nil { remove(item) }
    }
    public func bulkRestore(_ selected: [ScribeItem]) {
        for item in selected where item.deletedAt != nil { restoreFromTrash(item) }
    }
    public func bulkTag(_ selected: [ScribeItem], tag: String) {
        let clean = tag.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ";", with: " ")
        guard !clean.isEmpty else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        for item in selected {
            guard let index = items.firstIndex(where: { $0.id == item.id }) else { continue }
            let nextTags = appendingTag(items[index].tags, tag: clean)
            guard nextTags != items[index].tags else { continue }
            items[index].tags = nextTags
            items[index].updatedAt = max(now, items[index].updatedAt + 1)
        }
        persist()
    }
    public func bulkDeletePermanently(_ selected: [ScribeItem]) {
        for item in selected { deletePermanently(item) }
    }

    public func restoreVersion(_ item: ScribeItem, entry: ScribeHistoryEntry) {
        update(item, title: entry.title, body: entry.body)
    }

    public func review(_ item: ScribeItem, rating: Int) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        update(item, tags: flashcardReviewTags(item.tags, rating: rating, nowMillis: now))
    }

    public func syncSupabase(url: String, anonKey: String, email: String, password: String = "", refreshToken: String? = nil) async throws -> String {
        guard url.hasPrefix("https://"), let base = URL(string: url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))) else {
            throw NSError(domain: "ScribeCloud", code: 1, userInfo: [NSLocalizedDescriptionKey: "Enter a valid HTTPS Supabase project URL."])
        }
        guard !anonKey.isEmpty, (!email.isEmpty && !password.isEmpty) || !(refreshToken ?? "").isEmpty else {
            throw NSError(domain: "ScribeCloud", code: 2, userInfo: [NSLocalizedDescriptionKey: "Enter the anon key and sign in with your account."])
        }
        func request(_ path: String, method: String, token: String? = nil, prefer: String? = nil, body: Data? = nil, contentType: String = "application/json", upsert: Bool = false) async throws -> Data {
            let endpoint = URL(string: path, relativeTo: base.appendingPathComponent(""))!
            var req = URLRequest(url: endpoint)
            req.httpMethod = method
            req.timeoutInterval = 30
            req.setValue(anonKey, forHTTPHeaderField: "apikey")
            req.setValue(contentType, forHTTPHeaderField: "Content-Type")
            if upsert { req.setValue("true", forHTTPHeaderField: "x-upsert") }
            if let token { req.setValue("Bearer " + token, forHTTPHeaderField: "Authorization") }
            if let prefer { req.setValue(prefer, forHTTPHeaderField: "Prefer") }
            req.httpBody = body
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                let status = (response as? HTTPURLResponse)?.statusCode ?? -1
                let message = String(data: data, encoding: .utf8) ?? "Unknown server response"
                throw NSError(domain: "ScribeCloud", code: status, userInfo: [NSLocalizedDescriptionKey: "Supabase request failed (\(status)): \(message)"])
            }
            return data
        }
        let usingRefreshToken = !(refreshToken ?? "").isEmpty
        let authBody = try JSONSerialization.data(withJSONObject: usingRefreshToken
            ? ["refresh_token": refreshToken!] : ["email": email, "password": password])
        let grant = usingRefreshToken ? "refresh_token" : "password"
        let authData = try await request("auth/v1/token?grant_type=\(grant)", method: "POST", body: authBody)
        let auth = try JSONSerialization.jsonObject(with: authData) as? [String: Any] ?? [:]
        guard let token = auth["access_token"] as? String,
              let newRefreshToken = auth["refresh_token"] as? String,
              let user = auth["user"] as? [String: Any],
              let userID = user["id"] as? String else {
            throw NSError(domain: "ScribeCloud", code: 3, userInfo: [NSLocalizedDescriptionKey: "Supabase did not return a valid session."])
        }
        try ScribeCloudSessionVault.save(ScribeSavedCloudSession(
            url: url.trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            anonKey: anonKey, email: email, refreshToken: newRefreshToken
        ))
        scheduleCloudSync()

        for (cloud, deletedAt) in Array(pendingCloudDeletions) {
            let body = try JSONSerialization.data(withJSONObject: ["p_cloud_id": cloud, "p_deleted_at": deletedAt])
            _ = try await request("rest/v1/rpc/scribe_delete_item", method: "POST", token: token, body: body)
            pendingCloudDeletions.removeValue(forKey: cloud)
        }
        persist()
        func cloudID(_ tags: String) -> String? {
            tags.split(separator: ";").first(where: { $0.hasPrefix("cloud_id:") }).map { String($0.dropFirst(9)) }
        }
        func tagged(_ tags: String, _ id: String) -> String {
            (tags.split(separator: ";").map(String.init).filter { !$0.isEmpty && !$0.hasPrefix("cloud_id:") } + ["cloud_id:" + id]).joined(separator: ";")
        }
        for index in items.indices {
            if cloudID(items[index].tags) == nil {
                items[index].tags = tagged(items[index].tags, UUID().uuidString.lowercased())
            }
        }
        persist()

        func makePayload(_ item: ScribeItem, cloudIDs: [Int64: String]) async throws -> [String: Any] {
            let id = cloudIDs[item.id] ?? cloudID(item.tags) ?? UUID().uuidString.lowercased()
            var payload: [String: Any] = [
                "cloudId": id, "kind": item.kind, "title": item.title, "body": item.body,
                "tags": tagged(item.tags, id), "bookmarked": item.bookmarked,
                "position": item.position, "createdAt": item.createdAt, "updatedAt": item.updatedAt
            ]
            payload["history"] = (snapshots[item.id] ?? []).map { ["title": $0.title, "body": $0.body, "savedAt": $0.savedAt] as [String: Any] }
            if let parent = item.parentId, let parentCloud = cloudIDs[parent] { payload["parentCloudId"] = parentCloud }
            else { payload["parentCloudId"] = NSNull() }
            payload["deletedAt"] = item.deletedAt.map { $0 as Any } ?? NSNull()
            if !item.uri.isEmpty, let data = try? Data(contentsOf: URL(fileURLWithPath: item.uri)) {
                let fileName = URL(fileURLWithPath: item.uri).lastPathComponent
                payload["attachmentName"] = fileName
                if data.count <= 8 * 1024 * 1024 {
                    payload["attachmentBase64"] = data.base64EncodedString()
                } else {
                    let safeName = fileName.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? "attachment"
                    let objectPath = "\(userID)/\(id)/\(safeName)"
                    _ = try await request("storage/v1/object/scribe-attachments/\(objectPath)",
                        method: "POST", token: token, body: data,
                        contentType: "application/octet-stream", upsert: true)
                    payload["attachmentPath"] = objectPath
                }
            }
            return payload
        }
        func upload(_ records: [ScribeItem], cloudIDs: [Int64: String]) async throws {
            for item in records {
                guard let cloud = cloudIDs[item.id] else { continue }
                let body = try JSONSerialization.data(withJSONObject: [
                    "p_cloud_id": cloud,
                    "p_updated_at": item.updatedAt,
                    "p_payload": try await makePayload(item, cloudIDs: cloudIDs)
                ])
                _ = try await request("rest/v1/rpc/scribe_upsert_item",
                    method: "POST", token: token, body: body)
            }
        }
        var cloudIDs = Dictionary(uniqueKeysWithValues: items.compactMap { item in cloudID(item.tags).map { (item.id, $0) } })
        let remoteData = try await request("rest/v1/scribe_cloud_items?select=cloud_id,payload,updated_at",
            method: "GET", token: token)
        let remote = try JSONSerialization.jsonObject(with: remoteData) as? [[String: Any]] ?? []
        let tombstoneData = try await request("rest/v1/scribe_cloud_tombstones?select=cloud_id", method: "GET", token: token)
        let tombstones = try JSONSerialization.jsonObject(with: tombstoneData) as? [[String: Any]] ?? []
        let allBeforeTombstones = items
        for row in tombstones {
            guard let cloud = row["cloud_id"] as? String,
                  let match = allBeforeTombstones.first(where: { cloudID($0.tags) == cloud }) else { continue }
            func removeCloudTree(_ id: Int64) {
                let children = items.filter { $0.parentId == id }.map(\.id)
                children.forEach(removeCloudTree)
                items.removeAll { $0.id == id }
                snapshots.removeValue(forKey: id)
            }
            removeCloudTree(match.id)
        }
        var localByCloud = Dictionary(uniqueKeysWithValues: items.compactMap { item in cloudID(item.tags).map { ($0, item.id) } })
        var pending = remote
        func mergeHistory(_ data: [String: Any], itemID: Int64) {
            let remoteEntries = (data["history"] as? [[String: Any]] ?? []).compactMap { entry -> ScribeHistoryEntry? in
                guard let title = entry["title"] as? String,
                      let body = entry["body"] as? String,
                      let savedAt = (entry["savedAt"] as? NSNumber)?.int64Value else { return nil }
                return ScribeHistoryEntry(title: title, body: body, savedAt: savedAt)
            }
            var byDate: [Int64: ScribeHistoryEntry] = [:]
            for entry in snapshots[itemID] ?? [] { byDate[entry.savedAt] = entry }
            remoteEntries.forEach { byDate[$0.savedAt] = $0 }
            snapshots[itemID] = byDate.values.sorted { $0.savedAt > $1.savedAt }.prefix(30).map { $0 }
        }
        var inserted = 0
        var changed = 0
        func restoreAttachment(_ data: [String: Any], cloud: String) async throws -> String {
            let bytes: Data
            if let objectPath = data["attachmentPath"] as? String, !objectPath.isEmpty {
                bytes = try await request("storage/v1/object/authenticated/scribe-attachments/\(objectPath)", method: "GET", token: token)
            } else if let encoded = data["attachmentBase64"] as? String, let decoded = Data(base64Encoded: encoded) {
                bytes = decoded
            } else { return "" }
            let folder = fileURL.deletingLastPathComponent().appendingPathComponent("attachments", isDirectory: true)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let destination = folder.appendingPathComponent("cloud_" + cloud)
            try bytes.write(to: destination, options: .atomic)
            return destination.path
        }

        var madeProgress = true
        while !pending.isEmpty && madeProgress {
            madeProgress = false
            var remaining: [[String: Any]] = []
            for row in pending {
                guard let cloud = row["cloud_id"] as? String, let data = row["payload"] as? [String: Any] else { continue }
                let parentCloud = data["parentCloudId"] as? String
                if let parentCloud, localByCloud[parentCloud] == nil { remaining.append(row); continue }
                let parentID = parentCloud.flatMap { localByCloud[$0] }
                let remoteUpdated = (data["updatedAt"] as? NSNumber)?.int64Value ?? (row["updated_at"] as? NSNumber)?.int64Value ?? 0
                if let localID = localByCloud[cloud], let index = items.firstIndex(where: { $0.id == localID }) {
                    if remoteUpdated > items[index].updatedAt {
                        var uri = items[index].uri
                        if uri.isEmpty || !FileManager.default.fileExists(atPath: uri) {
                            uri = try await restoreAttachment(data, cloud: cloud)
                        }
                        items[index] = ScribeItem(id: localID, parentId: parentID,
                            kind: data["kind"] as? String ?? items[index].kind,
                            title: data["title"] as? String ?? items[index].title,
                            body: data["body"] as? String ?? items[index].body, uri: uri,
                            tags: tagged(data["tags"] as? String ?? items[index].tags, cloud),
                            bookmarked: data["bookmarked"] as? Bool ?? items[index].bookmarked,
                            position: data["position"] as? Int ?? items[index].position,
                            createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? items[index].createdAt,
                            updatedAt: remoteUpdated,
                            deletedAt: (data["deletedAt"] as? NSNumber)?.int64Value)
                        changed += 1
                    }
                } else {
                    let uri = try await restoreAttachment(data, cloud: cloud)
                    let id = nextID
                    nextID += 1
                    let item = ScribeItem(id: id, parentId: parentID, kind: data["kind"] as? String ?? "block",
                        title: data["title"] as? String ?? "Synced note", body: data["body"] as? String ?? "",
                        uri: uri, tags: tagged(data["tags"] as? String ?? "", cloud),
                        bookmarked: data["bookmarked"] as? Bool ?? false,
                        position: data["position"] as? Int ?? 0,
                        createdAt: (data["createdAt"] as? NSNumber)?.int64Value ?? remoteUpdated,
                        updatedAt: remoteUpdated,
                        deletedAt: (data["deletedAt"] as? NSNumber)?.int64Value)
                    items.append(item)
                    localByCloud[cloud] = id
                    inserted += 1
                }
                if let mergedID = localByCloud[cloud] { mergeHistory(data, itemID: mergedID) }
                madeProgress = true
            }
            pending = remaining
        }
        if !pending.isEmpty {
            throw NSError(domain: "ScribeCloud", code: 4, userInfo: [NSLocalizedDescriptionKey: "Sync stopped safely: \(pending.count) cloud record(s) have missing parent records. No incomplete sync was reported as successful."])
        }
        persist()
        cloudIDs = Dictionary(uniqueKeysWithValues: items.compactMap { item in cloudID(item.tags).map { (item.id, $0) } })
        try await upload(items, cloudIDs: cloudIDs)
        return "Synced \(inserted) new and \(changed) updated records, including attachments."
    }

    public func syncSavedCloudSession() async throws -> String {
        guard let session = ScribeCloudSessionVault.load() else {
            throw NSError(domain: "ScribeCloud", code: 5, userInfo: [NSLocalizedDescriptionKey: "No saved cloud session. Sign in once to enable automatic sync."])
        }
        return try await syncSupabase(
            url: session.url, anonKey: session.anonKey,
            email: session.email, refreshToken: session.refreshToken
        )
    }


    public func syncFirebase(apiKey: String, projectId: String, email: String, password: String,
                             createAccount: Bool) async throws -> String {
        let session = try await ScribeFirebaseClient.authenticate(
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            projectId: projectId.trimmingCharacters(in: .whitespacesAndNewlines),
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password, createAccount: createAccount)
        try ScribeFirebaseSessionVault.save(session)
        scheduleFirebaseSync()
        let (idToken, refreshedSession) = try await ScribeFirebaseClient.refreshed(session)
        try ScribeFirebaseSessionVault.save(refreshedSession)
        return try await syncFirebaseLibrary(projectId: projectId, idToken: idToken)
    }

    public func syncSavedFirebase() async throws -> String {
        guard let session = ScribeFirebaseSessionVault.load() else {
            throw NSError(domain: "ScribeFirebase", code: 5,
                userInfo: [NSLocalizedDescriptionKey: "No Firebase account is connected."])
        }
        let (idToken, refreshedSession) = try await ScribeFirebaseClient.refreshed(session)
        try ScribeFirebaseSessionVault.save(refreshedSession)
        return try await syncFirebaseLibrary(projectId: session.projectId, idToken: idToken)
    }

    public func disableFirebaseSync() {
        firebaseSyncTimer?.invalidate()
        firebaseSyncTimer = nil
        ScribeFirebaseSessionVault.clear()
    }

    private func scheduleFirebaseSync() {
        firebaseSyncTimer?.invalidate()
        firebaseSyncTimer = Timer.scheduledTimer(withTimeInterval: 6 * 60 * 60, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in _ = try? await self?.syncSavedFirebase() }
        }
    }

    private func syncFirebaseLibrary(projectId: String, idToken: String) async throws -> String {
        func firebaseID(_ tags: String) -> String? {
            tags.split(separator: ";").first(where: { $0.hasPrefix("firebase_id:") }).map { String($0.dropFirst("firebase_id:".count)) }
        }
        func tagged(_ tags: String, _ id: String) -> String {
            (tags.split(separator: ";").map(String.init).filter { !$0.isEmpty && !$0.hasPrefix("firebase_id:") } + ["firebase_id:" + id]).joined(separator: ";")
        }
        func restoreFirebaseAttachment(_ payload: [String: Any], id: String, currentURI: String) -> String {
            if !currentURI.isEmpty && FileManager.default.fileExists(atPath: currentURI) { return currentURI }
            guard let encoded = payload["attachmentBase64"] as? String,
                  let data = Data(base64Encoded: encoded) else { return currentURI }
            let folder = fileURL.deletingLastPathComponent().appendingPathComponent("attachments", isDirectory: true)
            do {
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                let rawName = payload["attachmentName"] as? String ?? "attachment"
                let safeName = URL(fileURLWithPath: rawName).lastPathComponent.replacingOccurrences(of: "/", with: "_")
                let destination = folder.appendingPathComponent("firebase_\(id)_\(safeName)")
                try data.write(to: destination, options: .atomic)
                return destination.path
            } catch { return currentURI }
        }

        for index in items.indices {
            if firebaseID(items[index].tags) == nil {
                items[index].tags = tagged(items[index].tags, UUID().uuidString.lowercased())
            }
        }
        persist()
        var pending: [[String: Any]] = []
        var cursor: String?
        repeat {
            let page = try await ScribeFirebaseClient.requestLibrary(
                projectId: projectId, idToken: idToken, method: "GET", cursor: cursor)
            pending.append(contentsOf: page["records"] as? [[String: Any]] ?? [])
            cursor = page["nextCursor"] as? String
        } while cursor != nil
        var localByFirebase = Dictionary(uniqueKeysWithValues: items.compactMap { item in
            firebaseID(item.tags).map { ($0, item.id) }
        })
        var inserted = 0
        var changed = 0
        var madeProgress = true
        while !pending.isEmpty && madeProgress {
            madeProgress = false
            var remaining: [[String: Any]] = []
            for row in pending {
                guard let id = row["id"] as? String, let payload = row["payload"] as? [String: Any] else { continue }
                let parentFirebase = payload["parentFirebaseId"] as? String
                if let parentFirebase, localByFirebase[parentFirebase] == nil {
                    remaining.append(row); continue
                }
                let parentID = parentFirebase.flatMap { localByFirebase[$0] }
                let remoteUpdated = (payload["updatedAt"] as? NSNumber)?.int64Value ?? (row["updatedAt"] as? NSNumber)?.int64Value ?? 0
                if let localID = localByFirebase[id], let index = items.firstIndex(where: { $0.id == localID }) {
                    if remoteUpdated > items[index].updatedAt {
                        items[index] = ScribeItem(id: localID, parentId: parentID,
                            kind: payload["kind"] as? String ?? items[index].kind,
                            title: payload["title"] as? String ?? items[index].title,
                            body: payload["body"] as? String ?? items[index].body,
                            uri: restoreFirebaseAttachment(payload, id: id, currentURI: items[index].uri),
                            tags: tagged(payload["tags"] as? String ?? items[index].tags, id),
                            bookmarked: payload["bookmarked"] as? Bool ?? items[index].bookmarked,
                            position: payload["position"] as? Int ?? items[index].position,
                            createdAt: (payload["createdAt"] as? NSNumber)?.int64Value ?? items[index].createdAt,
                            updatedAt: remoteUpdated,
                            deletedAt: (payload["deletedAt"] as? NSNumber)?.int64Value)
                        changed += 1
                    }
                } else {
                    let localID = (items.map(\.id).max() ?? 0) + 1
                    let item = ScribeItem(id: localID, parentId: parentID,
                        kind: payload["kind"] as? String ?? "block",
                        title: payload["title"] as? String ?? "Synced note",
                        body: payload["body"] as? String ?? "",
                        uri: restoreFirebaseAttachment(payload, id: id, currentURI: ""),
                        tags: tagged(payload["tags"] as? String ?? "", id),
                        bookmarked: payload["bookmarked"] as? Bool ?? false,
                        position: payload["position"] as? Int ?? 0,
                        createdAt: (payload["createdAt"] as? NSNumber)?.int64Value ?? remoteUpdated,
                        updatedAt: remoteUpdated,
                        deletedAt: (payload["deletedAt"] as? NSNumber)?.int64Value)
                    items.append(item)
                    localByFirebase[id] = localID
                    inserted += 1
                }
                if let localID = localByFirebase[id] {
                    let remoteHistory = (payload["history"] as? [[String: Any]] ?? []).compactMap { entry -> ScribeHistoryEntry? in
                        guard let title = entry["title"] as? String, let body = entry["body"] as? String,
                              let savedAt = (entry["savedAt"] as? NSNumber)?.int64Value else { return nil }
                        return ScribeHistoryEntry(title: title, body: body, savedAt: savedAt)
                    }
                    var byDate = Dictionary(uniqueKeysWithValues: (snapshots[localID] ?? []).map { ($0.savedAt, $0) })
                    remoteHistory.forEach { byDate[$0.savedAt] = $0 }
                    snapshots[localID] = byDate.values.sorted { $0.savedAt > $1.savedAt }.prefix(30).map { $0 }
                }
                madeProgress = true
            }
            pending = remaining
        }
        if !pending.isEmpty {
            throw NSError(domain: "ScribeFirebase", code: 6,
                userInfo: [NSLocalizedDescriptionKey: "Firebase sync paused because one or more parent notebook records are missing."])
        }
        persist()

        var records: [[String: Any]] = []
        let ids = Dictionary(uniqueKeysWithValues: items.compactMap { item in firebaseID(item.tags).map { (item.id, $0) } })
        for item in items {
            guard let id = ids[item.id] else { continue }
            let parentFirebase = item.parentId.flatMap { ids[$0] }
            let history = (snapshots[item.id] ?? []).map { ["title": $0.title, "body": $0.body, "savedAt": $0.savedAt] as [String: Any] }
            var payload: [String: Any] = [
                "kind": item.kind, "title": item.title, "body": item.body,
                "tags": tagged(item.tags, id), "bookmarked": item.bookmarked,
                "position": item.position, "createdAt": item.createdAt,
                "updatedAt": item.updatedAt, "deletedAt": item.deletedAt as Any? ?? NSNull(),
                "parentFirebaseId": parentFirebase as Any? ?? NSNull(), "history": history
            ]
            if !item.uri.isEmpty, let data = try? Data(contentsOf: URL(fileURLWithPath: item.uri)), data.count <= 400_000 {
                payload["attachmentName"] = URL(fileURLWithPath: item.uri).lastPathComponent
                payload["attachmentBase64"] = data.base64EncodedString()
            }
            records.append(["id": id, "updatedAt": item.updatedAt, "payload": payload])
        }
        var uploaded = 0
        for start in stride(from: 0, to: records.count, by: 100) {
            let batch = Array(records[start..<min(start + 100, records.count)])
            let result = try await ScribeFirebaseClient.requestLibrary(projectId: projectId, idToken: idToken,
                method: "POST", body: ["records": batch])
            uploaded += result["saved"] as? Int ?? 0
        }
        return "Firebase sync complete: \(inserted) added, \(changed) updated, \(uploaded) uploaded."
    }

    public func disableCloudSync() {
        cloudSyncTimer?.invalidate()
        cloudSyncTimer = nil
        ScribeCloudSessionVault.clear()
    }

    private func scheduleCloudSync() {
        cloudSyncTimer?.invalidate()
        cloudSyncTimer = Timer.scheduledTimer(withTimeInterval: 6 * 60 * 60, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                _ = try? await self.syncSavedCloudSession()
            }
        }
    }

    public func persist() {
        if let data = try? JSONEncoder().encode(items) { try? data.write(to: fileURL, options: .atomic) }
        if let data = try? JSONEncoder().encode(pendingCloudDeletions) { try? data.write(to: deletionQueueURL, options: .atomic) }
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
        if let validationError = backupValidationError(records) {
            throw NSError(domain: "ScribeBackup", code: 1, userInfo: [NSLocalizedDescriptionKey: validationError])
        }
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
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        isRunning = true
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if self.remaining > 0 { self.remaining -= 1 } else {
                    self.isRunning = false; self.timer?.invalidate(); self.completedSessions += 1
                    let session = ScribeItem(id: self.nextID, kind: "study_session", title: self.sessionLabel,
                        body: "Completed a \(self.focusMinutes)-minute focus session.",
                        tags: "minutes:\(self.focusMinutes)")
                    self.nextID += 1
                    self.items.append(session)
                    self.persist()
                    let content = UNMutableNotificationContent()
                    content.title = "Focus session complete"
                    content.body = "\(self.sessionLabel) · \(self.focusMinutes) minutes completed."
                    content.sound = .default
                    let request = UNNotificationRequest(
                        identifier: "scribe-focus-\(session.id)", content: content, trigger: nil)
                    Task { try? await UNUserNotificationCenter.current().add(request) }
                }
            }
        }
    }
    public func resetTimer() { timer?.invalidate(); isRunning = false; remaining = focusMinutes * 60 }
    deinit { timer?.invalidate() }
}
