import SwiftUI

struct DesktopStudyView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @State private var mode = "Flashcards"
    @State private var activeCardID: UUID?
    @State private var revealed = false
    @State private var showCreateCard = false
    @State private var cardFront = ""
    @State private var cardBack = ""
    @State private var deck = "General"
    @State private var selectedDuration = 25
    @State private var sessionLabel = ""
    @State private var endDate: Date?
    @State private var completed = false
    @State private var reminderTime = Calendar.current.date(from: DateComponents(hour: 18, minute: 0)) ?? .now
    @State private var reminderStatus = "No daily reminder scheduled."

    private var cards: [ScribeNode] { library.visibleNodes.filter { $0.kind == "flashcard" }.sorted { $0.createdAt < $1.createdAt } }
    private var dueCards: [ScribeNode] {
        cards.filter { card in
            let due = flashTag("dueAt", in: card.tags).flatMap(Double.init).map { Date(timeIntervalSince1970: $0 / 1000) } ?? .distantPast
            return due <= .now
        }
    }
    private var activeCard: ScribeNode? { activeCardID.flatMap(library.node) ?? dueCards.first }
    private var sessions: [ScribeNode] { library.visibleNodes.filter { $0.kind == "study_session" }.sorted { $0.createdAt > $1.createdAt } }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Picker("Study mode", selection: $mode) {
                Text("Flashcards").tag("Flashcards")
                Text("Focus").tag("Focus")
            }.pickerStyle(.segmented)
            if mode == "Flashcards" { flashcardPanel } else { focusPanel }
        }
        .padding(24)
        .navigationTitle("Study")
        .sheet(isPresented: $showCreateCard) {
            NavigationStack {
                Form {
                    Section("New flashcard") {
                        TextField("Question / front", text: $cardFront, axis: .vertical)
                        TextField("Answer / back", text: $cardBack, axis: .vertical)
                        TextField("Deck / topic", text: $deck)
                    }
                }
                .navigationTitle("Create flashcard")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { showCreateCard = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") {
                            let body = cardFront.trimmingCharacters(in: .whitespacesAndNewlines) + "\n---ANSWER---\n" + cardBack.trimmingCharacters(in: .whitespacesAndNewlines)
                            _ = library.add(kind: "flashcard", title: cardFront.trimmingCharacters(in: .whitespacesAndNewlines), body: body,
                                            tags: "deck:\(deck);interval:0;dueAt:0;ease:2.5")
                            cardFront = ""; cardBack = ""; showCreateCard = false
                        }.disabled(cardFront.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || cardBack.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }.frame(minWidth: 440, minHeight: 300)
        }
    }

    private var flashcardPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Study smarter.").font(.largeTitle.weight(.semibold))
                    Text("A focused review queue that grows with your notes.").foregroundStyle(.secondary)
                }
                Spacer()
                Button { showCreateCard = true } label: { Label("Create flashcard", systemImage: "plus") }.buttonStyle(.borderedProminent)
            }
            HStack(spacing: 12) {
                metric("TOTAL CARDS", value: "\(cards.count)")
                metric("DUE NOW", value: "\(dueCards.count)")
                metric("MASTERED", value: "\(cards.filter { (Double(flashTag("interval", in: $0.tags) ?? "0") ?? 0) >= 21 }.count)")
            }
            Text("REVIEW QUEUE").font(.caption.weight(.semibold)).tracking(1.3).foregroundStyle(.secondary)
            if let card = activeCard {
                let pair = card.body.components(separatedBy: "\n---ANSWER---\n")
                let front = pair.first ?? card.title
                let back = pair.count > 1 ? pair[1] : card.body
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Text(card.tags.split(separator: ";").first(where: { $0.hasPrefix("deck:") }).map { String($0.dropFirst(5)) } ?? "Flashcard")
                            .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        Spacer()
                        Text("\(dueCards.count) due").font(.caption).foregroundStyle(.secondary)
                    }
                    Text(revealed ? "ANSWER" : "QUESTION").font(.caption.weight(.semibold)).tracking(1.5).foregroundStyle(.tint)
                    Text(revealed ? back : front).font(.title2).textSelection(.enabled)
                    if !revealed {
                        Button("Reveal answer") { revealed = true }.buttonStyle(.borderedProminent)
                    } else {
                        Divider()
                        Text("How well did you remember it?").font(.headline)
                        HStack {
                            reviewButton("Again", tint: .red) { schedule(card, rating: 0) }
                            reviewButton("Good", tint: .accentColor) { schedule(card, rating: 2) }
                            reviewButton("Easy", tint: .green) { schedule(card, rating: 3) }
                        }
                    }
                }
                .padding(22).frame(maxWidth: .infinity, alignment: .leading)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Label("You're all caught up", systemImage: "checkmark.circle").font(.title2.weight(.semibold))
                    Text(cards.isEmpty ? "Create cards for formulas, definitions, circuit concepts and key facts." : "Your next review is scheduled for later.")
                        .foregroundStyle(.secondary)
                }.padding(22).frame(maxWidth: .infinity, alignment: .leading)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
            }
            Text("ALL FLASHCARDS").font(.caption.weight(.semibold)).tracking(1.3).foregroundStyle(.secondary)
            ForEach(cards) { card in
                HStack {
                    VStack(alignment: .leading) {
                        Text(card.title).fontWeight(.medium)
                        Text("Interval: \(flashTag("interval", in: card.tags) ?? "0") day(s)").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button("Review") { activeCardID = card.id; revealed = false }.disabled(!dueCards.contains(where: { $0.id == card.id }))
                    Button(role: .destructive) { library.moveToTrash(card.id) } label: { Image(systemName: "trash") }
                }.padding(10).background(.quaternary.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private var focusPanel: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Make time for deep work.").font(.largeTitle.weight(.semibold))
            TextField("Session / subject", text: $sessionLabel).textFieldStyle(.roundedBorder).frame(maxWidth: 420)
            if let endDate {
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    let remaining = max(0, Int(endDate.timeIntervalSince(timeline.date)))
                    VStack(alignment: .leading, spacing: 10) {
                        Text(String(format: "%02d:%02d", remaining / 60, remaining % 60))
                            .font(.system(size: 58, weight: .light, design: .rounded).monospacedDigit())
                        ProgressView(value: Double(selectedDuration * 60 - remaining), total: Double(selectedDuration * 60))
                    }
                    .onChange(of: remaining) { value in
                        if value == 0 && !completed {
                            completed = true
                            _ = library.add(kind: "study_session", title: sessionLabel.isEmpty ? "Focused study" : sessionLabel,
                                            body: "Completed a \(selectedDuration) minute focus session.",
                                            tags: "minutes:\(selectedDuration);completedAt:\(Date.now.timeIntervalSince1970)")
                        }
                    }
                }
                Button("End session") { self.endDate = nil; completed = false }.buttonStyle(.bordered)
            } else {
                Picker("Duration", selection: $selectedDuration) {
                    Text("15 min").tag(15); Text("25 min").tag(25); Text("45 min").tag(45); Text("60 min").tag(60)
                }.pickerStyle(.segmented).frame(maxWidth: 420)
                Button("Start focus") { completed = false; endDate = Date.now.addingTimeInterval(Double(selectedDuration * 60)) }
                    .buttonStyle(.borderedProminent)
            }
            HStack(spacing: 12) {
                metric("COMPLETED SESSIONS", value: "\(sessions.count)")
                metric("TOTAL FOCUS", value: "\(sessions.reduce(0) { $0 + (Int(flashTag("minutes", in: $1.tags) ?? "0") ?? 0) }) min")
            }
            Text("RECENT SESSIONS").font(.caption.weight(.semibold)).tracking(1.3).foregroundStyle(.secondary)
            ForEach(sessions.prefix(10)) { item in
                HStack {
                    VStack(alignment: .leading) { Text(item.title); Text(item.body).font(.caption).foregroundStyle(.secondary) }
                    Spacer()
                    Text(item.createdAt.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(.secondary)
                }.padding(10).background(.quaternary.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
            }
            Divider()
            Text("Daily study reminder").font(.headline)
            DatePicker("Reminder time", selection: $reminderTime, displayedComponents: .hourAndMinute)
            HStack {
                Button("Schedule daily reminder") { scheduleReminder() }.buttonStyle(.borderedProminent)
                Button("Cancel reminder") {
                    UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["scribe.daily-study"])
                    reminderStatus = "Daily reminder cancelled."
                }.buttonStyle(.bordered)
            }
            Text(reminderStatus).font(.caption).foregroundStyle(.secondary)
        }
    }

    private func metric(_ title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.caption2.weight(.semibold)).tracking(1).foregroundStyle(.secondary)
            Text(value).font(.title2.weight(.semibold)).foregroundStyle(.tint)
        }.frame(maxWidth: .infinity, alignment: .leading).padding(14)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 13))
    }

    private func reviewButton(_ title: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(title, action: action).buttonStyle(.bordered).tint(tint).frame(maxWidth: .infinity)
    }

    private func schedule(_ card: ScribeNode, rating: Int) {
        let oldInterval = Double(flashTag("interval", in: card.tags) ?? "0") ?? 0
        let oldEase = Double(flashTag("ease", in: card.tags) ?? "2.5") ?? 2.5
        let interval: Double
        let easeDelta: Double
        switch rating {
        case 0: interval = 0.006; easeDelta = -0.2
        case 2: interval = oldInterval < 1 ? 1 : oldInterval * oldEase; easeDelta = 0
        default: interval = oldInterval < 1 ? 4 : oldInterval * oldEase * 1.3; easeDelta = 0.15
        }
        let newEase = min(max(oldEase + easeDelta, 1.3), 3.0)
        let dueAt = Date.now.timeIntervalSince1970 * 1000 + interval * 86_400_000
        var fields = card.tags.split(separator: ";").map(String.init).filter {
            !$0.hasPrefix("dueAt:") && !$0.hasPrefix("interval:") && !$0.hasPrefix("ease:")
        }
        fields.append("dueAt:\(Int64(dueAt))")
        fields.append("interval:\(interval)")
        fields.append("ease:\(newEase)")
        var updated = card; updated.tags = fields.joined(separator: ";")
        library.update(updated)
        activeCardID = nil; revealed = false
    }

    private func scheduleReminder() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { granted, error in
            guard granted else {
                DispatchQueue.main.async { reminderStatus = error?.localizedDescription ?? "Notification permission was not granted." }
                return
            }
            var components = Calendar.current.dateComponents([.hour, .minute], from: reminderTime)
            components.second = 0
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            let content = UNMutableNotificationContent()
            content.title = "Time for a study session"
            content.body = "Open Scribe and continue where you left off."
            content.sound = .default
            let request = UNNotificationRequest(identifier: "scribe.daily-study", content: content, trigger: trigger)
            UNUserNotificationCenter.current().add(request) { error in
                DispatchQueue.main.async { reminderStatus = error?.localizedDescription ?? "Daily reminder scheduled." }
            }
        }
    }
}

private func flashTag(_ key: String, in tags: String) -> String? {
    let prefix = key + ":"
    return tags.split(separator: ";").first(where: { $0.hasPrefix(prefix) }).map { String($0.dropFirst(prefix.count)) }
}
