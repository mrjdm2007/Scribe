import Foundation
import SwiftUI

struct ScribeNode: Codable, Identifiable, Hashable {
    var id: UUID
    var parentID: UUID?
    var kind: String
    var title: String
    var body: String
    var position: Int
    var createdAt: Date
    var updatedAt: Date

    init(id: UUID = UUID(), parentID: UUID? = nil, kind: String, title: String,
         body: String = "", position: Int = 0, createdAt: Date = .now, updatedAt: Date = .now) {
        self.id = id; self.parentID = parentID; self.kind = kind; self.title = title
        self.body = body; self.position = position; self.createdAt = createdAt; self.updatedAt = updatedAt
    }
}

@MainActor
final class ScribeLibrary: ObservableObject {
    @Published private(set) var nodes: [ScribeNode] = []
    private let fileURL: URL

    var visibleCourses: [ScribeNode] {
        nodes.filter { $0.kind == "course" }.sorted { $0.position < $1.position }
    }

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let folder = support.appendingPathComponent("Scribe", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        fileURL = folder.appendingPathComponent("library.json")
        load()
    }

    func addCourse() {
        let node = ScribeNode(kind: "course", title: "Untitled course", position: visibleCourses.count)
        nodes.append(node)
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([ScribeNode].self, from: data) else { return }
        nodes = decoded
    }

    private func save() {
        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            try encoder.encode(nodes).write(to: fileURL, options: .atomic)
        } catch {
            assertionFailure("Scribe could not save its local library: \(error)")
        }
    }
}
