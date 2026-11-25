import SwiftUI
import ScribeCore

private struct ScribeAIContext: Encodable {
    let title: String
    let path: String
    let content: String
}

/// Calls the authenticated Supabase Edge Function; provider credentials remain server-side.
struct ScribeIntelligenceView: View {
    let items: [ScribeItem]
    @AppStorage("scribe.supabase.url") private var supabaseURL = ""
    @AppStorage("scribe.supabase.anonKey") private var anonKey = ""
    @State private var question = ""
    @State private var answer = ""
    @State private var errorMessage = ""
    @State private var mode = "notes"
    @State private var busy = false
    private let modes = ["notes", "explain", "exam", "quiz", "research", "summary"]

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
            .map { ScribeAIContext(title: $0.0.title, path: $0.0.kind, content: String($0.0.body.prefix(12000))) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("SCRIBE INTELLIGENCE").font(.caption.bold()).tracking(1.5).foregroundStyle(.secondary)
                    Text("Your study assistant").font(.title2.bold())
                }
                Spacer()
                if busy { ProgressView() }
            }
            Picker("Mode", selection: $mode) {
                ForEach(modes, id: \.self) { Text($0.capitalized).tag($0) }
            }.pickerStyle(.segmented)
            TextEditor(text: $question).frame(minHeight: 76, maxHeight: 110)
                .padding(7).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                .overlay(alignment: .topLeading) {
                    if question.isEmpty { Text("Ask across your notebooks, request exam questions, start a quiz or research a topic…").foregroundStyle(.tertiary).padding(15).allowsHitTesting(false) }
                }
            HStack {
                Text("\(relevantNotes.count) matching note sources").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button("Ask Scribe") { Task { await ask() } }
                    .buttonStyle(.borderedProminent).tint(Color(red: 0.84, green: 0.72, blue: 0.55))
                    .disabled(question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy)
            }
            if !errorMessage.isEmpty { Text(errorMessage).foregroundStyle(.red).font(.callout) }
            ScrollView {
                Text(answer.isEmpty ? "Scribe can search your notes, explain difficult concepts, prepare exam questions, quiz you and research topics. Answers distinguish notebook evidence from external research." : answer)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .font(.system(size: 15)).lineSpacing(5).textSelection(.enabled)
                    .foregroundStyle(answer.isEmpty ? .secondary : .primary)
            }.frame(maxHeight: .infinity)
        }.padding(20).frame(minWidth: 520, minHeight: 500)
    }

    @MainActor private func ask() async {
        let base = supabaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard base.hasPrefix("https://"), !anonKey.isEmpty else {
            errorMessage = "Add your Supabase project URL and anon key in Scribe Settings first."
            return
        }
        busy = true; errorMessage = ""; answer = ""
        defer { busy = false }
        do {
            let url = URL(string: base + "/functions/v1/study-assistant")!
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(anonKey, forHTTPHeaderField: "apikey")
            request.setValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
            let payload: [String: Any] = [
                "question": question, "mode": mode,
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
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
