import SwiftUI
import UserNotifications
import ScribeCore

struct AcademicToolsView: View {
    @EnvironmentObject private var store: ScribeStore
    @State private var section = "Graph"
    @State private var slope = "1"
    @State private var intercept = "0"
    @State private var equationTitle = ""
    @State private var equationText = ""
    @State private var circuitTitle = "Circuit design"
    @State private var circuitComponents = "V1 5V\nR1 1kΩ\nLED1 red LED"
    @State private var circuitNotes = ""
    @State private var tableTitle = "Scientific table"
    @State private var tableCells = Array(repeating: Array(repeating: "", count: 4), count: 4)
    @State private var question = ""
    @State private var answer = ""
    @State private var practiceIndex = 0
    @State private var revealAnswer = false
    @State private var diagramTitle = "Concept map"
    @State private var diagramOutline = ""
    @State private var reminderTitle = ""
    @State private var reminderMessage = ""
    @State private var reminderDate = Date().addingTimeInterval(3600)
    @State private var reminderStatus = ""

    private let sections = ["Graph", "Equations", "Circuit", "Tables", "Questions", "Practice", "Reminders", "Diagrams"]
    private var questions: [ScribeItem] { store.items.filter { $0.kind == "question" && $0.deletedAt == nil }.sorted { $0.updatedAt > $1.updatedAt } }

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
            Text("Linear graph plotter").font(.title2.bold())
            HStack {
                TextField("Slope (m)", text: $slope).textFieldStyle(.roundedBorder)
                TextField("Intercept (c)", text: $intercept).textFieldStyle(.roundedBorder)
            }.frame(maxWidth: 420)
            Text("y = mx + c").font(.system(.title3, design: .monospaced))
            Canvas { context, size in
                let midX = size.width / 2, midY = size.height / 2
                var axes = Path()
                axes.move(to: CGPoint(x: 0, y: midY)); axes.addLine(to: CGPoint(x: size.width, y: midY))
                axes.move(to: CGPoint(x: midX, y: 0)); axes.addLine(to: CGPoint(x: midX, y: size.height))
                context.stroke(axes, with: .color(.secondary.opacity(0.7)), lineWidth: 1)
                guard let m = Double(slope), let c = Double(intercept) else { return }
                var plot = Path()
                for step in 0...100 {
                    let x = (Double(step) / 100.0 * 10.0) - 5.0
                    let y = m * x + c
                    let point = CGPoint(x: Double(step) / 100.0 * size.width,
                                         y: midY - y * size.height / 12.0)
                    if step == 0 { plot.move(to: point) } else { plot.addLine(to: point) }
                }
                context.stroke(plot, with: .color(.orange), lineWidth: 2.5)
            }
            .frame(height: 260)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
            Button("Save graph to library") {
                store.create(kind: "block", title: "Graph · y = \(slope)x + \(intercept)",
                             body: "Linear graph: y = \(slope)x + \(intercept)", tags: "type:graph")
            }.buttonStyle(.borderedProminent)
            Spacer()
        }
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
                             body: equationText, tags: "subject:engineering")
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
                             tags: "type:circuit;subject:electronics")
            }.buttonStyle(.borderedProminent)
            Text("Use one component per line and describe node connections explicitly.").font(.caption).foregroundStyle(.secondary)
            Spacer()
        }
    }

    private var tables: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Scientific table").font(.title2.bold())
            TextField("Table title", text: $tableTitle).textFieldStyle(.roundedBorder).frame(maxWidth: 360)
            Grid(horizontalSpacing: 8, verticalSpacing: 8) {
                ForEach(0..<4, id: \.self) { row in
                    GridRow {
                        ForEach(0..<4, id: \.self) { column in
                            TextField(row == 0 ? "Header" : "—", text: cellBinding(row, column))
                                .textFieldStyle(.roundedBorder).frame(minWidth: 90)
                        }
                    }
                }
            }
            Button("Save table") {
                let text = tableCells.map { $0.joined(separator: "\t") }.joined(separator: "\n")
                store.create(kind: "block", title: tableTitle, body: text, tags: "type:table")
            }.buttonStyle(.borderedProminent)
            Spacer()
        }
    }

    private func cellBinding(_ row: Int, _ column: Int) -> Binding<String> {
        Binding(get: { tableCells[row][column] }, set: { tableCells[row][column] = $0 })
    }

    private var questionsEditor: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Question bank").font(.title2.bold())
            TextField("Question", text: $question, axis: .vertical).lineLimit(2...4).textFieldStyle(.roundedBorder)
            TextEditor(text: $answer).frame(minHeight: 100).padding(6)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
            Button("Add question") {
                guard !question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                store.create(kind: "question", title: question, body: answer, tags: "subject:general")
                question = ""; answer = ""
            }.buttonStyle(.borderedProminent)
            Text("\(questions.count) saved questions").foregroundStyle(.secondary)
            List(questions.prefix(12)) { item in
                VStack(alignment: .leading) {
                    Text(item.title)
                    Text(item.body).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
            }
            Spacer()
        }
    }

    private var practice: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Practice").font(.title2.bold())
            if questions.isEmpty {
                ContentUnavailableView("No questions yet", systemImage: "questionmark.circle", description: Text("Add questions in the Question bank first."))
            } else {
                let card = questions[practiceIndex % questions.count]
                Text("Question \((practiceIndex % questions.count) + 1) of \(questions.count)").font(.caption).foregroundStyle(.secondary)
                Text(card.title).font(.title3.bold()).frame(maxWidth: .infinity, minHeight: 120, alignment: .leading)
                if revealAnswer {
                    Text(card.body).padding().frame(maxWidth: .infinity, alignment: .leading)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                }
                HStack {
                    Button(revealAnswer ? "Hide answer" : "Reveal answer") { revealAnswer.toggle() }
                    Button("Next question") { practiceIndex = (practiceIndex + 1) % questions.count; revealAnswer = false }
                        .buttonStyle(.borderedProminent)
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
                guard !reminderTitle.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                    reminderStatus = "Enter a reminder title."; return
                }
                let center = UNUserNotificationCenter.current()
                center.requestAuthorization(options: [.alert, .sound]) { granted, error in
                    guard granted, error == nil else {
                        DispatchQueue.main.async { reminderStatus = "Notification permission was not granted." }
                        return
                    }
                    let content = UNMutableNotificationContent()
                    content.title = reminderTitle
                    content.body = reminderMessage.isEmpty ? "Time for your study session." : reminderMessage
                    content.sound = .default
                    let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: reminderDate)
                    let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
                    let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger)
                    center.add(request) { error in
                        DispatchQueue.main.async { reminderStatus = error == nil ? "Reminder scheduled." : "Could not schedule reminder." }
                    }
                }
            }.buttonStyle(.borderedProminent)
            Text(reminderStatus).font(.caption).foregroundStyle(.secondary)
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
                store.create(kind: "block", title: diagramTitle, body: diagramOutline, tags: "type:diagram")
            }.buttonStyle(.borderedProminent)
            Text("Create a structured outline here; it remains editable and can be exported with your library.").font(.caption).foregroundStyle(.secondary)
            Spacer()
        }
    }
}
