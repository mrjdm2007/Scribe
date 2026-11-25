import SwiftUI

struct DesktopStudyView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @AppStorage("scribe.study.sessionHistory") private var sessionHistory = ""
    @State private var mode = "Flashcards"
    @State private var activeCardID: UUID?
    @State private var revealed = false
    @State private var activeQuestionID: UUID?
    @State private var questionRevealed = false
    @State private var selectedDuration = 25
    @State private var endDate: Date?
    @State private var completed = false

    private var dueCards: [ScribeNode] {
        library.visibleNodes.filter { node in
            guard node.kind == "flashcard" else { return false }
            let due = reviewValue("due", in: node.tags).flatMap(Double.init).map { Date(timeIntervalSince1970: $0) } ?? .distantPast
            return due <= .now
        }.sorted { $0.createdAt < $1.createdAt }
    }
    private var questions: [ScribeNode] { library.visibleNodes.filter { $0.kind == "question" }.sorted { $0.createdAt < $1.createdAt } }
    private var activeCard: ScribeNode? { activeCardID.flatMap(library.node) ?? dueCards.first }
    private var activeQuestion: ScribeNode? { activeQuestionID.flatMap(library.node) ?? questions.first }
    private var history: [String] { sessionHistory.split(separator: "\n").map(String.init).suffix(8).reversed() }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Picker("Study mode", selection: $mode) {
                Text("Flashcards").tag("Flashcards")
                Text("Questions").tag("Questions")
                Text("Focus").tag("Focus")
            }.pickerStyle(.segmented)
            if mode == "Flashcards" { flashcardPanel }
            else if mode == "Questions" { questionPanel }
            else { focusPanel }
        }
        .padding(24)
        .navigationTitle("Study")
        .onChange(of: mode) { _ in revealed = false; questionRevealed = false }
    }

    private var flashcardPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Label("Due for review", systemImage: "rectangle.stack")
                Spacer()
                Text("\(dueCards.count) cards").foregroundStyle(.secondary)
            }
            if let card = activeCard {
                VStack(alignment: .leading, spacing: 18) {
                    Text(card.title).font(.title2.weight(.semibold))
                    if revealed {
                        Divider()
                        Text(card.body).textSelection(.enabled)
                    } else {
                        Text("Think of the answer, then reveal it.")
                            .foregroundStyle(.secondary)
                    }
                    Button(revealed ? "Hide answer" : "Reveal answer") { revealed.toggle() }
                        .buttonStyle(.bordered)
                    if revealed {
                        Divider()
                        Text("How well did you remember?").font(.headline)
                        HStack {
                            gradeButton("Again", color: .red, days: 0.007)
                            gradeButton("Hard", color: .orange, days: 1)
                            gradeButton("Good", color: .blue, days: 3)
                            gradeButton("Easy", color: .green, days: 7)
                        }
                    }
                }
                .padding(22)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
            } else {
                Label("All caught up", systemImage: "checkmark.circle").font(.title2)
                    Text("There are no flashcards due right now. Create cards in Study tools.").foregroundStyle(.secondary)
            }
        }
    }

    private var questionPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("\(questions.count) saved questions").foregroundStyle(.secondary)
            if let question = activeQuestion {
                VStack(alignment: .leading, spacing: 16) {
                    Text(question.title).font(.title2.weight(.semibold))
                    if questionRevealed { Text(question.body).textSelection(.enabled) }
                    Button(questionRevealed ? "Hide answer" : "Reveal answer") { questionRevealed.toggle() }
                    HStack {
                        Button("Previous") { moveQuestion(-1) }.disabled(questions.first?.id == question.id)
                        Spacer()
                        Button("Next question") { moveQuestion(1) }.buttonStyle(.borderedProminent)
                    }
                }.padding(22).frame(maxWidth: .infinity, alignment: .leading)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
            } else {
                Label("No questions yet", systemImage: "questionmark.circle").font(.title2)
                    Text("Create questions in Study tools to practise them here.").foregroundStyle(.secondary)
            }
        }
    }

    private var focusPanel: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Focus session").font(.title2.weight(.semibold))
            if let endDate {
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    let remaining = max(0, Int(endDate.timeIntervalSince(timeline.date)))
                    VStack(alignment: .leading, spacing: 10) {
                        Text(String(format: "%02d:%02d", remaining / 60, remaining % 60))
                            .font(.system(size: 54, weight: .light, design: .rounded).monospacedDigit())
                        ProgressView(value: Double(selectedDuration * 60 - remaining), total: Double(selectedDuration * 60))
                    }
                    .onChange(of: remaining) { value in
                        if value == 0 && !completed {
                            completed = true
                            sessionHistory = (sessionHistory + "\n" + Date.now.formatted(date: .abbreviated, time: .shortened) + " · \(selectedDuration) min").trimmingCharacters(in: .whitespacesAndNewlines)
                        }
                    }
                }
                Button("End session") { self.endDate = nil; completed = false }.buttonStyle(.bordered)
            } else {
                Picker("Duration", selection: $selectedDuration) {
                    Text("15 min").tag(15); Text("25 min").tag(25)
                    Text("45 min").tag(45); Text("60 min").tag(60)
                }.pickerStyle(.segmented)
                Button("Start focus session") { completed = false; endDate = Date.now.addingTimeInterval(Double(selectedDuration * 60)) }
                    .buttonStyle(.borderedProminent)
            }
            Divider()
            Text("Recent completed sessions").font(.headline)
            if history.isEmpty { Text("No completed sessions yet.").foregroundStyle(.secondary) }
            else { ForEach(Array(history.enumerated()), id: \.offset) { _, entry in Text(entry).font(.subheadline).foregroundStyle(.secondary) } }
        }
    }

    private func gradeButton(_ title: String, color: Color, days: Double) -> some View {
        Button(title) {
            guard var card = activeCard else { return }
            var fields = card.tags.split(separator: ";").map(String.init).filter {
                !$0.hasPrefix("reviewDue=") && !$0.hasPrefix("reviewInterval=") && !$0.hasPrefix("reviewCount=")
            }
            let next = Date.now.addingTimeInterval(days * 86_400)
            fields.append("reviewDue=\(next.timeIntervalSince1970)")
            fields.append("reviewInterval=\(days)")
            let oldCount = reviewValue("count", in: card.tags).flatMap(Int.init) ?? 0
            fields.append("reviewCount=\(title == "Again" ? 0 : oldCount + 1)")
            if !fields.contains("type:flashcard") { fields.append("type:flashcard") }
            card.tags = fields.joined(separator: ";")
            library.update(card)
            activeCardID = nil
            revealed = false
        }
        .buttonStyle(.bordered)
        .tint(color)
    }

    private func moveQuestion(_ offset: Int) {
        guard let current = activeQuestion, let index = questions.firstIndex(where: { $0.id == current.id }) else { return }
        let next = min(max(index + offset, 0), questions.count - 1)
        activeQuestionID = questions[next].id
        questionRevealed = false
    }
}

private func reviewValue(_ key: String, in tags: String) -> String? {
    let prefix = "review\\(key.capitalized)="
    return tags.split(separator: ";").first(where: { $0.hasPrefix(prefix) })
        .map { String($0.dropFirst(prefix.count)) }
}
