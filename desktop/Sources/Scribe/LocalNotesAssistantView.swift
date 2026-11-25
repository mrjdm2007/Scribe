import SwiftUI

/// API-key-free notebook retrieval: local search, explicit notebook scope, and traceable note citations.
/// This view never reads attachment bytes or OCR/PDF-extracted text.
struct LocalNotesAssistantView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @State private var question = ""
    @State private var selectedCourseIDs: Set<UUID> = []
    @State private var scopeInitialized = false

    private var words: [String] {
        question.lowercased().split { !$0.isLetter && !$0.isNumber }.map(String.init).filter { $0.count > 1 }
    }

    private var results: [(node: ScribeNode, score: Int, citation: String)] {
        guard !words.isEmpty else { return [] }
        let visible = library.visibleNodes.filter { node in
            guard node.uri.isEmpty else { return false }
            guard node.kind != "block" || !node.tags.contains("type:image") && !node.tags.contains("type:pdf") else { return false }
            return selectedCourseIDs.isEmpty || selectedCourseIDs.contains(rootCourseID(for: node))
        }
        return visible.compactMap { node in
            let title = node.title.lowercased()
            let body = node.body.lowercased()
            let tags = node.tags.lowercased()
            let score = words.reduce(0) { total, word in
                total + (title.contains(word) ? 5 : 0) + (body.contains(word) ? 2 : 0) + (tags.contains(word) ? 1 : 0)
            }
            guard score > 0 else { return nil }
            return (node, score, citationPath(for: node))
        }.sorted {
            $0.score == $1.score ? $0.node.updatedAt > $1.node.updatedAt : $0.score > $1.score
        }.prefix(20).map { $0 }
    }

    private func rootCourseID(for node: ScribeNode) -> UUID {
        var current = node
        var visited: Set<UUID> = []
        while let parentID = current.parentID, !visited.contains(parentID), let parent = library.node(parentID) {
            visited.insert(parentID)
            current = parent
        }
        return current.id
    }

    private func citationPath(for node: ScribeNode) -> String {
        var labels = [node.title.isEmpty ? node.kind.capitalized : node.title]
        var current = node
        var visited: Set<UUID> = [node.id]
        while let parentID = current.parentID, !visited.contains(parentID), let parent = library.node(parentID) {
            visited.insert(parentID)
            labels.append(parent.title.isEmpty ? parent.kind.capitalized : parent.title)
            current = parent
        }
        return labels.reversed().joined(separator: " › ")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Ask my notes").font(.largeTitle.weight(.semibold))
                    Text("Search your saved text locally, choose which notebooks to include, and trace every result to its source. No AI API key or internet connection required.")
                        .foregroundStyle(.secondary)
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("QUESTION OR TOPIC").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    TextField("e.g. Kirchhoff's voltage law, semiconductor doping…", text: $question)
                        .textFieldStyle(.roundedBorder)
                }
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("NOTEBOOK CONTEXT").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        Spacer()
                        Button(selectedCourseIDs.isEmpty ? "Clear selection" : "Use all notebooks") {
                            selectedCourseIDs.removeAll()
                        }.font(.caption)
                    }
                    Text("Choose specific courses, or leave all unselected to search everything.")
                        .font(.caption).foregroundStyle(.secondary)
                    if library.courses.isEmpty {
                        Text("Create a course to start building your notebook context.").foregroundStyle(.secondary)
                    } else {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 210), alignment: .leading)], alignment: .leading, spacing: 8) {
                            ForEach(library.courses) { course in
                                let chosen = selectedCourseIDs.contains(course.id)
                                Button {
                                    if chosen { selectedCourseIDs.remove(course.id) }
                                    else { selectedCourseIDs.insert(course.id) }
                                } label: {
                                    Label(course.title, systemImage: chosen ? "checkmark.circle.fill" : "circle")
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(9)
                                        .background(chosen ? Color.accentColor.opacity(0.12) : Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 9))
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                }
                Divider()
                HStack {
                    Text("NOTE SOURCES").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    Spacer()
                    Text("\(results.count) matches").font(.caption).foregroundStyle(.secondary)
                }
                if words.isEmpty {
                    ContentUnavailableView("Search your notes", systemImage: "text.magnifyingglass", description: Text("Enter a topic or question to find relevant saved text."))
                } else if results.isEmpty {
                    ContentUnavailableView("No matching notes", systemImage: "doc.text.magnifyingglass", description: Text("Try different keywords or expand the selected notebook context."))
                } else {
                    ForEach(results, id: \.node.id) { result in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(result.node.title.isEmpty ? result.node.kind.capitalized : result.node.title)
                                    .font(.headline)
                                Spacer()
                                Text("Relevance \(result.score)").font(.caption2).foregroundStyle(.secondary)
                            }
                            if !result.node.body.isEmpty {
                                Text(result.node.body).font(.body).lineLimit(5).textSelection(.enabled)
                            }
                            Label(result.citation, systemImage: "quote.opening")
                                .font(.caption).foregroundStyle(.tint).textSelection(.enabled)
                            Text("Source: \(result.node.kind.capitalized) · Updated \(result.node.updatedAt.formatted(date: .abbreviated, time: .shortened))")
                                .font(.caption2).foregroundStyle(.tertiary)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 13))
                    }
                }
                Text("This is local retrieval, not a generated AI answer. Photos, PDFs, attachment previews, and OCR text are excluded.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            .padding(24)
            .frame(maxWidth: 900, alignment: .leading)
        }
    }
}
