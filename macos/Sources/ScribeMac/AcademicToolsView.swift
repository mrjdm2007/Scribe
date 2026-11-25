import SwiftUI
import UserNotifications
import ScribeCore

// macOS academic tools use the same portable question encoding as Android.
struct AcademicToolsView: View {
    @EnvironmentObject private var store: ScribeStore
    @State private var section = "Graph"
    @State private var graphExpression = "sin(x)"
    @State private var graphRange = "6.28"
    @State private var equationTitle = ""
    @State private var equationText = ""
    @State private var circuitTitle = "Circuit design"
    @State private var circuitComponents = "V1 5V\nR1 1kΩ\nLED1 red LED"
    @State private var circuitNotes = ""
    @State private var tableTitle = "Scientific table"
    @State private var tableCells = Array(repeating: Array(repeating: "", count: 4), count: 4)
    @State private var question = ""
    @State private var topic = ""
    @State private var options = ["", "", "", ""]
    @State private var correctOption = "A"
    @State private var explanation = ""
    @State private var practiceAnswered = false
    @State private var practiceScore = 0
    @State private var practiceIndex = 0
    @State private var revealAnswer = false
    @State private var diagramTitle = "Concept map"
    @State private var diagramOutline = ""
    @State private var reminderTitle = ""
    @State private var reminderMessage = ""
    @State private var reminderDate = Date().addingTimeInterval(3600)
    @State private var reminderStatus = ""

    private let sections = ["Graph", "Equations", "Circuit", "Tables", "Questions", "Practice", "Reminders", "Diagrams"]
    private var questions: [ScribeItem] { store.items.filter { $0.kind == "study_question" && $0.deletedAt == nil && $0.body.contains("---OPTIONS---") }.sorted { $0.updatedAt > $1.updatedAt } }

    private func parsedQuestion(_ item: ScribeItem) -> (prompt: String, options: [String], correct: String, explanation: String) {
        let optionsMarker = "\n---OPTIONS---\n"
        let answerMarker = "\n---CORRECT---\n"
        let explanationMarker = "\n---EXPLANATION---\n"
        let optionPart = item.body.components(separatedBy: optionsMarker).dropFirst().joined(separator: optionsMarker)
        let answerPart = optionPart.components(separatedBy: answerMarker)
        let optionLines = (answerPart.first ?? "").components(separatedBy: .newlines).filter { !$0.isEmpty }
        let tail = answerPart.count > 1 ? answerPart[1] : "A"
        let answerAndExplanation = tail.components(separatedBy: explanationMarker)
        return (item.body.components(separatedBy: optionsMarker).first ?? item.body, optionLines, answerAndExplanation.first ?? "A", answerAndExplanation.count > 1 ? answerAndExplanation[1] : "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Academic tools").font(.largeTitle.bold())
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(sections, id: \.self) { value in
                        Button(value) { section = value }
                            .buttonStyle(.borderedProminent)
                            .tint(section == value ? Color.accentColor : Color.secondary.opacity(0.22))
                    }
                }
            }
            Group {
                switch section {
                case "Graph": graph
                case "Equations": equations
                case "Circuit": circuit
                case "Tables": tables
                case "Questions": questionsEditor
                case "Practice": practice
                case "Reminders": reminders
                default: diagrams
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .padding(24)
    }

    private var graph: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Function graph plotter").font(.title2.bold())
            TextField("Function · sin(x), cos(x), tan(x), x, x^2, 1/x, exp(x)", text: $graphExpression)
                .textFieldStyle(.roundedBorder).frame(maxWidth: 520)
            TextField("X range · 1 to 30", text: $graphRange)
                .textFieldStyle(.roundedBorder).frame(maxWidth: 220)
            Text("y = " + graphExpression).font(.system(.title3, design: .monospaced))
            Canvas { context, size in
                let midX = size.width / 2, midY = size.height / 2
                var grid = Path()
                for index in 0...10 {
                    let x = size.width * CGFloat(index) / 10
                    let y = size.height * CGFloat(index) / 10
                    grid.move(to: CGPoint(x: x, y: 0)); grid.addLine(to: CGPoint(x: x, y: size.height))
                    grid.move(to: CGPoint(x: 0, y: y)); grid.addLine(to: CGPoint(x: size.width, y: y))
                }
                context.stroke(grid, with: .color(.secondary.opacity(0.15)), lineWidth: 0.7)
                var axes = Path()
                axes.move(to: CGPoint(x: 0, y: midY)); axes.addLine(to: CGPoint(x: size.width, y: midY))
                axes.move(to: CGPoint(x: midX, y: 0)); axes.addLine(to: CGPoint(x: midX, y: size.height))
                context.stroke(axes, with: .color(.secondary.opacity(0.8)), lineWidth: 1)
                let limit = min(max(Double(graphRange) ?? 6.28, 1), 30)
                var plot = Path()
                var started = false
                for step in 0...600 {
                    let x = -limit + 2 * limit * Double(step) / 600
                    let px = CGFloat(step) / 600 * size.width
                    if let y = graphValue(x), y.isFinite, abs(y) <= 6 {
                        let point = CGPoint(x: px, y: midY - CGFloat(y) * size.height / 12)
                        if started { plot.addLine(to: point) } else { plot.move(to: point); started = true }
                    } else { started = false }
                }
                context.stroke(plot, with: .color(.orange), lineWidth: 2.4)
            }
            .frame(height: 280)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
            Button("Save graph to library") {
                store.create(kind: "block", title: "Graph · y = " + graphExpression,
                    body: "y = " + graphExpression + "\nRange: ±" + graphRange + " (radians for trigonometric functions)",
                    tags: "type:graph", switchToLibrary: false)
            }.buttonStyle(.borderedProminent)
            Text("Supports x, pi, e, + − × ÷, powers, parentheses, sin, cos, tan, sqrt, abs, ln, log and exp. Trigonometric functions use radians.")
                .font(.caption).foregroundStyle(.secondary)
            Spacer()
        }
    }

    private func graphValue(_ x: Double) -> Double? {
        guard let value = ScribeMathExpression.evaluate(graphExpression, x: x),
              abs(value) <= 6 else { return nil }
        return value
    }

    private var equations: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Equation composer").font(.title2.bold())
            TextField("Equation name", text: $equationTitle).textFieldStyle(.roundedBorder)
            TextEditor(text: $equationText).frame(minHeight: 140).padding(6)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
            Button("Save equation") {
                guard !equationText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                store.create(kind: "formula", title: equationTitle.isEmpty ? "Equation" : equationTitle,
                             body: equationText, tags: "subject:engineering", switchToLibrary: false)
                equationTitle = ""; equationText = ""
            }.buttonStyle(.borderedProminent)
            Text("Equations are stored as portable text so their source is preserved in Android backups.").font(.caption).foregroundStyle(.secondary)
            Spacer()
        }
    }

    private var circuit: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Circuit workbench").font(.title2.bold())
            TextField("Circuit title", text: $circuitTitle).textFieldStyle(.roundedBorder)
            Text("COMPONENTS / CONNECTIONS").font(.caption.bold()).foregroundStyle(.secondary)
            TextEditor(text: $circuitComponents).frame(minHeight: 150).padding(6)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
            TextField("Design notes", text: $circuitNotes, axis: .vertical).lineLimit(2...5).textFieldStyle(.roundedBorder)
            Button("Save circuit") {
                store.create(kind: "block", title: circuitTitle,
                             body: circuitComponents + "\n\n" + circuitNotes,
                             tags: "type:circuit;subject:electronics", switchToLibrary: false)
            }.buttonStyle(.borderedProminent)
            Text("Use one component per line and describe node connections explicitly.").font(.caption).foregroundStyle(.secondary)
            Spacer()
        }
    }

    private var tables: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Scientific table").font(.title2.bold())
            TextField("Table title", text: $tableTitle).textFieldStyle(.roundedBorder).frame(maxWidth: 360)
            HStack {
                Button("Add row") { if tableCells.count < 30 { tableCells.append(Array(repeating: "", count: tableCells.first?.count ?? 4)) } }
                Button("Add column") { if (tableCells.first?.count ?? 0) < 12 { for index in tableCells.indices { tableCells[index].append("") } } }
                Button("Remove row") { if tableCells.count > 1 { tableCells.removeLast() } }
                Button("Remove column") { if (tableCells.first?.count ?? 0) > 1 { for index in tableCells.indices { tableCells[index].removeLast() } } }
            }.buttonStyle(.bordered)
            ScrollView([.horizontal, .vertical]) {
                Grid(horizontalSpacing: 8, verticalSpacing: 8) {
                    ForEach(tableCells.indices, id: \.self) { row in
                        GridRow {
                            ForEach(tableCells[row].indices, id: \.self) { column in
                                TextField(row == 0 ? "Header" : "—", text: cellBinding(row, column))
                                    .textFieldStyle(.roundedBorder).frame(minWidth: 105)
                            }
                        }
                    }
                }.padding(4)
            }.frame(maxHeight: 360)
            Button("Save table") {
                let text = tableCells.map { $0.joined(separator: "\t") }.joined(separator: "\n")
                store.create(kind: "block", title: tableTitle, body: text, tags: "type:table;format:tsv", switchToLibrary: false)
            }.buttonStyle(.borderedProminent)
            Spacer()
        }
    }

    private func cellBinding(_ row: Int, _ column: Int) -> Binding<String> {
        Binding(get: { tableCells[row][column] }, set: { tableCells[row][column] = $0 })
    }

    private var questionsEditor: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Question bank").font(.title2.bold())
            TextField("Question", text: $question, axis: .vertical).lineLimit(2...4).textFieldStyle(.roundedBorder)
            TextField("Topic / subject", text: $topic).textFieldStyle(.roundedBorder)
            ForEach(options.indices, id: \.self) { index in
                TextField("Option \(index + 1)", text: Binding(
                    get: { options[index] },
                    set: { options[index] = $0 }
                )).textFieldStyle(.roundedBorder)
            }
            HStack {
                Text("Correct option")
                ForEach(["A", "B", "C", "D"], id: \.self) { value in
                    Button(value) { correctOption = value }.buttonStyle(.borderedProminent)
                        .tint(correctOption == value ? .accentColor : .secondary.opacity(0.25))
                }
            }
            TextEditor(text: $explanation).frame(minHeight: 65).padding(5)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(.secondary.opacity(0.25)))
            Button("Add question") {
                let prompt = question.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !prompt.isEmpty, options.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else { return }
                let body = prompt + "\n---OPTIONS---\n" + options.joined(separator: "\n")
                    + "\n---CORRECT---\n" + correctOption + "\n---EXPLANATION---\n" + explanation
                store.create(kind: "study_question", title: prompt, body: body,
                    tags: "subject:" + topic, switchToLibrary: false)
                question = ""; topic = ""; options = ["", "", "", ""]; explanation = ""; correctOption = "A"
            }.buttonStyle(.borderedProminent)
            Text("\(questions.count) saved multiple-choice questions").foregroundStyle(.secondary)
            List(questions.prefix(20)) { item in
                let parsed = parsedQuestion(item)
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title)
                    Text(parsed.options.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                    Text("Answer: " + parsed.correct).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var practice: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Practice").font(.title2.bold())
            if questions.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "questionmark.circle").font(.largeTitle)
                    Text("No questions yet").font(.headline)
                    Text("Add multiple-choice questions in the Question bank first.").foregroundStyle(.secondary)
                }.frame(maxWidth: .infinity, minHeight: 160)
            } else {
                let card = questions[practiceIndex % questions.count]
                let parsed = parsedQuestion(card)
                Text("Question \(practiceIndex % questions.count + 1) of \(questions.count) · Score \(practiceScore)")
                    .font(.caption).foregroundStyle(.secondary)
                Text(card.title).font(.title3.bold()).frame(maxWidth: .infinity, minHeight: 90, alignment: .leading)
                ForEach(parsed.options.indices, id: \.self) { index in
                    let letter = ["A", "B", "C", "D"][index]
                    Text(parsed.options[index])
                        .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(alignment: .leading) { Text(letter).fontWeight(.bold).padding(.leading, 8).foregroundStyle(.secondary) }
                        .onTapGesture {
                            guard !practiceAnswered else { return }
                            if letter == parsed.correct { practiceScore += 1 }
                            practiceAnswered = true
                        }
                }
                if practiceAnswered {
                    Text("Correct answer: " + parsed.correct).fontWeight(.semibold)
                    if !parsed.explanation.isEmpty { Text(parsed.explanation).foregroundStyle(.secondary) }
                }
                HStack {
                    Button("Restart") { practiceIndex = 0; practiceScore = 0; practiceAnswered = false }
                    Button("Next question") {
                        practiceIndex = (practiceIndex + 1) % questions.count
                        practiceAnswered = false
                    }.buttonStyle(.borderedProminent)
                }
            }
            Spacer()
        }
    }

    private var reminders: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Study reminders").font(.title2.bold())
            TextField("Reminder title", text: $reminderTitle).textFieldStyle(.roundedBorder)
            TextField("Message (optional)", text: $reminderMessage).textFieldStyle(.roundedBorder)
            DatePicker("Remind me", selection: $reminderDate, in: Date()..., displayedComponents: [.date, .hourAndMinute])
            Button("Schedule reminder") {
                guard !reminderTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                      reminderDate > Date() else {
                    reminderStatus = "Enter a title and a future date."; return
                }
                let title = reminderTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                let message = reminderMessage.trimmingCharacters(in: .whitespacesAndNewlines)
                store.create(kind: "study_reminder", title: title, body: message,
                    tags: "dueAt:\(Int64(reminderDate.timeIntervalSince1970 * 1000))",
                    switchToLibrary: false)
                guard let item = store.items.last else { reminderStatus = "Could not save reminder."; return }
                let center = UNUserNotificationCenter.current()
                center.requestAuthorization(options: [.alert, .sound]) { granted, error in
                    guard granted, error == nil else {
                        DispatchQueue.main.async { reminderStatus = "Notification permission was not granted." }
                        return
                    }
                    let content = UNMutableNotificationContent()
                    content.title = title
                    content.body = message.isEmpty ? "Time for your study session." : message
                    content.sound = .default
                    let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: reminderDate)
                    let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
                    let request = UNNotificationRequest(identifier: "scribe-reminder-\(item.id)", content: content, trigger: trigger)
                    center.add(request) { error in
                        DispatchQueue.main.async {
                            reminderStatus = error == nil ? "Reminder saved and scheduled." : "Saved, but notification scheduling failed."
                        }
                    }
                }
                reminderTitle = ""; reminderMessage = ""
            }.buttonStyle(.borderedProminent)
            Text(reminderStatus).font(.caption).foregroundStyle(.secondary)
            Text("UPCOMING REMINDERS").font(.caption.bold()).foregroundStyle(.secondary)
            let reminders = store.items.filter { $0.kind == "study_reminder" && $0.deletedAt == nil }
                .sorted { $0.tags < $1.tags }
            if reminders.isEmpty { Text("No reminders scheduled.").foregroundStyle(.secondary) }
            ForEach(reminders) { item in
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.title).fontWeight(.medium)
                        Text(item.tags.replacingOccurrences(of: "dueAt:", with: "Due · "))
                            .font(.caption).foregroundStyle(.secondary)
                        if !item.body.isEmpty { Text(item.body).font(.caption).foregroundStyle(.secondary) }
                    }
                    Spacer()
                    Button("Cancel", role: .destructive) {
                        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["scribe-reminder-\(item.id)"])
                        store.remove(item)
                    }
                }.padding(10).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
            }
            Spacer()
        }
    }

    private var diagrams: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Diagram planner").font(.title2.bold())
            TextField("Diagram title", text: $diagramTitle).textFieldStyle(.roundedBorder)
            TextEditor(text: $diagramOutline).frame(minHeight: 180).padding(6)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
            Button("Save diagram outline") {
                store.create(kind: "block", title: diagramTitle, body: diagramOutline, tags: "type:diagram", switchToLibrary: false)
            }.buttonStyle(.borderedProminent)
            Text("Create a structured outline here; it remains editable and can be exported with your library.").font(.caption).foregroundStyle(.secondary)
            Spacer()
        }
    }
}
