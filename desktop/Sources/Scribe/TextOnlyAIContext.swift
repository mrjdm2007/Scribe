import Foundation

/// The only notebook text that may be supplied to an AI provider.
/// Attachment nodes and file URLs are excluded. This builder never reads file contents,
/// performs PDF extraction/OCR, or decodes images.
enum TextOnlyAIContext {
    private static let supportedKinds: Set<String> = [
        "course", "chapter", "topic", "page", "block", "formula", "question",
        "flashcard", "diagram", "study_question"
    ]
    private static let attachmentTypes: Set<String> = ["image", "pdf"]

    static func build(from nodes: [ScribeNode], maxCharacters: Int = 100_000) -> String {
        guard maxCharacters > 0 else { return "" }
        let visible = nodes.filter { node in
            node.deletedAt == nil && supportedKinds.contains(node.kind) &&
            node.uri.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isAttachment(node)
        }.sorted {
            $0.position == $1.position ? $0.createdAt < $1.createdAt : $0.position < $1.position
        }
        let byID = Dictionary(uniqueKeysWithValues: visible.map { ($0.id, $0) })
        func depth(of node: ScribeNode, visited: Set<UUID> = []) -> Int {
            guard let parentID = node.parentID, let parent = byID[parentID], !visited.contains(parentID) else { return 0 }
            return min(8, 1 + depth(of: parent, visited: visited.union([parentID])))
        }
        var sections: [String] = []
        var count = 0
        for node in visible {
            let title = node.title.trimmingCharacters(in: .whitespacesAndNewlines)
            let body = node.body.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !title.isEmpty || !body.isEmpty else { continue }
            let indent = String(repeating: "  ", count: depth(of: node))
            var section = "\(indent)[\(node.kind)] \(title)"
            if !body.isEmpty { section += "\n\(indent)  \(body)" }
            let extra = section.count + (sections.isEmpty ? 0 : 2)
            guard count + extra <= maxCharacters else { break }
            sections.append(section)
            count += extra
        }
        return sections.joined(separator: "\n\n")
    }

    private static func isAttachment(_ node: ScribeNode) -> Bool {
        guard node.kind == "block" else { return false }
        let fields = node.tags.split(whereSeparator: { $0 == ";" || $0 == "," })
        let type = fields.first { $0.trimmingCharacters(in: .whitespaces).hasPrefix("type:") }
            .map { String($0).trimmingCharacters(in: .whitespaces).dropFirst(5) }
        return type.map { attachmentTypes.contains(String($0)) } ?? false
    }
}