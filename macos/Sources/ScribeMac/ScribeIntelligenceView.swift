import SwiftUI
import ScribeCore

private struct ScribeAIContext: Encodable {
    let title: String
    let path: String
    let content: String
}

/// Calls the authenticated Supabase Edge Function; provider credentials remain server-side.
struct ScribeIntelligenceView: View {
    @ObservedObject var store: ScribeStore
    private var items: [ScribeItem] { store.items }
    @AppStorage("scribe.supabase.url") private var supabaseURL = ""
    @AppStorage("scribe.supabase.anonKey") private var anonKey = ""
    @State private var question = ""
    @State private var answer = ""
    @State private var errorMessage = ""
    @State private var mode = "notes"
    @State private var busy = false
    @State private var conversation: [String] = []
    @State private var savedFlashcards = 0
    private let modes = ["notes", "explain", "exam", "quiz", "research", "summary", "flashcards", "mindmap", "socratic", "teach", "revise", "memorise", "practice", "test", "followup", "compare", "glossary", "formula_sheet", "study_plan", "lesson", "misconception", "mnemonics", "case_study"]

    private func notebookPath(for item: ScribeItem) -> String {
        var names = [item.title]
        var parentID = item.parentId
        var seen = Set<Int64>()
        while let id = parentID, seen.insert(id).inserted,
              let parent = items.first(where: { $0.id == id }) {
            names.append(parent.title)
            parentID = parent.parentId
        }
        return names.reversed().joined(separator: " / ")
    }

    private var relevantNotes: [ScribeAIContext] {
        let terms = Set(question.lowercased().split { !$0.isLetter && !$0.isNumber }.filter { $0.count > 2 })
        return items.filter { $0.deletedAt == nil && !$0.body.isEmpty }
            .map { item -> (ScribeItem, Int) in
                let score = terms.reduce(0) { total, term in
                    total + (item.title.localizedCaseInsensitiveContains(String(term)) ? 3 : 0)
                        + (item.body.localizedCaseInsensitiveContains(String(term)) ? 1 : 0)
                }
                return (item, score)
            }
            .filter { $0.1 > 0 }
            .sorted { $0.1 > $1.1 }
            .prefix(24)
            .map { ScribeAIContext(title: $0.0.title, path: notebookPath(for: $0.0), content: String($0.0.body.prefix(12000))) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("SCRIBE INTELLIGENCE").font(.caption.bold()).tracking(1.5).foregroundStyle(.secondary)
                    Text("Your study assistant").font(.title2.bold())
                }
                Spacer()
                Button("New chat") { conversation = []; question = ""; answer = ""; errorMessage = ""; savedFlashcards = 0 }
                    .buttonStyle(.borderless).disabled(busy)
                if busy { ProgressView() }
            }
            Picker("Mode", selection: $mode) {
                ForEach(modes, id: \.self) { Text($0.capitalized).tag($0) }
            }.pickerStyle(.menu)
            TextEditor(text: $question).frame(minHeight: 76, maxHeight: 110)
                .padding(7).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                .overlay(alignment: .topLeading) {
                    if question.isEmpty { Text("Ask your notes, compare concepts, build formula sheets, plan revision, practise or research…").foregroundStyle(.tertiary).padding(15).allowsHitTesting(false) }
                }
            HStack {
                Text("\(relevantNotes.count) matching note sources").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button("Ask Scribe") { Task { await ask() } }
                    .buttonStyle(.borderedProminent).tint(Color(red: 0.84, green: 0.72, blue: 0.55))
                    .disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy)
            }
            if !errorMessage.isEmpty { Text(errorMessage).foregroundStyle(.red).font(.callout) }
            if mode == "flashcards" && !answer.isEmpty {
                HStack {
                    Button(savedFlashcards > 0 ? "Saved \(savedFlashcards) flashcards" : "Save generated flashcards to library") {
                        savedFlashcards = saveGeneratedFlashcards()
                    }.disabled(savedFlashcards > 0)
                    if savedFlashcards == 0 { Text("Cards are stored in your Scribe flashcard library.").font(.caption).foregroundStyle(.secondary) }
                }
            }
            ScrollView {
                Text(answer.isEmpty ? "Scribe can search your notes, explain difficult concepts, prepare exam questions, quiz you and research topics. Answers distinguish notebook evidence from external research." : answer)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .font(.system(size: 15)).lineSpacing(5).textSelection(.enabled)
                    .foregroundStyle(answer.isEmpty ? .secondary : .primary)
            }.frame(maxHeight: .infinity)
        }.padding(20).frame(minWidth: 520, minHeight: 500)
    }

    private func saveGeneratedFlashcards() -> Int {
        guard let start = answer.firstIndex(of: "{"),
              let end = answer.lastIndex(of: "}"), start < end,
              let data = String(answer[start...end]).data(using: .utf8),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let cards = object["flashcards"] as? [[String: Any]] else { return 0 }
        var saved = 0
        for card in cards {
            let front = (card["front"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let back = (card["back"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !front.isEmpty, !back.isEmpty else { continue }
            let source = (card["sourceTitle"] as? String ?? "").replacingOccurrences(of: ";", with: ",")
            let tags = source.isEmpty ? "deck:AI generated" : "deck:AI generated;source_title:" + String(source.prefix(100))
            store.create(kind: "flashcard", title: front, body: back, tags: tags, switchToLibrary: false)
            saved += 1
        }
        return saved
    }

    @MainActor private func ask() async {
        let base = supabaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard base.hasPrefix("https://"), !anonKey.isEmpty else {
            errorMessage = "Add your Supabase project URL and anon key in Scribe Settings first."
            return
        }
        let studentMessage = question
        let priorTurns = conversation.suffix(8).joined(separator: "\n\n")
        let conversationPrompt = (priorTurns.isEmpty ? "" : priorTurns + "\n\n") + "STUDENT: " + studentMessage
        busy = true; errorMessage = ""; answer = ""
        defer { busy = false }
        do {
            guard let session = ScribeCloudSessionVault.load(),
                  session.url.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == base,
                  session.anonKey == anonKey else {
                errorMessage = "Sign in to Scribe Cloud and sync once before using Scribe Intelligence."
                return
            }
            let refreshURL = URL(string: base + "/auth/v1/token?grant_type=refresh_token")!
            var refreshRequest = URLRequest(url: refreshURL)
            refreshRequest.httpMethod = "POST"
            refreshRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
            refreshRequest.setValue(anonKey, forHTTPHeaderField: "apikey")
            refreshRequest.httpBody = try JSONSerialization.data(withJSONObject: ["refresh_token": session.refreshToken])
            let (refreshData, refreshResponse) = try await URLSession.shared.data(for: refreshRequest)
            let refreshStatus = (refreshResponse as? HTTPURLResponse)?.statusCode ?? 0
            let refreshed = (try? JSONSerialization.jsonObject(with: refreshData)) as? [String: Any] ?? [:]
            guard (200..<300).contains(refreshStatus), let accessToken = refreshed["access_token"] as? String else {
                throw NSError(domain: "ScribeAI", code: refreshStatus, userInfo: [NSLocalizedDescriptionKey: "Your Scribe Cloud session expired. Sign in again in Settings."])
            }
            if let nextRefresh = refreshed["refresh_token"] as? String {
                try ScribeCloudSessionVault.save(ScribeSavedCloudSession(url: base, anonKey: anonKey, email: session.email, refreshToken: nextRefresh))
            }
            let url = URL(string: base + "/functions/v1/study-assistant")!
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(anonKey, forHTTPHeaderField: "apikey")
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            let payload: [String: Any] = [
                "question": conversationPrompt, "mode": mode,
                "note_context": try JSONSerialization.jsonObject(with: JSONEncoder().encode(relevantNotes))
            ]
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
            let (data, response) = try await URLSession.shared.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
            guard (200..<300).contains(status) else {
                throw NSError(domain: "ScribeAI", code: status, userInfo: [NSLocalizedDescriptionKey: object["error"] as? String ?? "Study assistant request failed (HTTP \(status))."])
            }
            answer = object["answer"] as? String ?? "The assistant returned an empty response."
            conversation.append("Student: " + studentMessage)
            conversation.append("Scribe: " + answer)
            if conversation.count > 16 { conversation.removeFirst(conversation.count - 16) }
            question = ""
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
