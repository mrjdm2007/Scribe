import SwiftUI
import UserNotifications

struct DesktopWorkbenchView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @State private var tool = "Converter"
    @State private var group = "Length"
    @State private var amount = "1"
    @State private var from = "m"
    @State private var to = "cm"
    @State private var expression = "sin(x)"
    @State private var graphRange = 6.28
    @State private var formulaName = "New formula"
    @State private var formulaSource = ""
    @State private var formulaQuery = ""
    @State private var tableTitle = "Measurement table"
    @State private var cells = Array(repeating: Array(repeating: "", count: 3), count: 4)
    @State private var component = "Resistor"
    @State private var reference = "R1"
    @State private var componentValue = "1 kΩ"
    @State private var componentNotes = "Series circuit"
    @State private var componentCount = 1
    @State private var diagramTitle = "Concept map"
    @State private var diagramLabels = "Main idea,Concept A,Concept B,Details"
    @State private var questionPrompt = ""
    @State private var questionTopic = ""
    @State private var questionOptions = Array(repeating: "", count: 4)
    @State private var correctOption = "A"
    @State private var explanation = ""
    @State private var practiceIDs: [UUID] = []
    @State private var practiceIndex = 0
    @State private var practiceSelection: Int?
    @State private var practiceSubmitted = false
    @State private var practiceScore = 0
    @State private var reminderTitle = ""
    @State private var reminderMessage = "Review your notes and formulas."
    @State private var reminderDate = Calendar.current.date(byAdding: .hour, value: 1, to: .now) ?? .now
    @State private var reminderStatus = ""

    private let groups: [String: [String]] = [
        "Length":["m","cm","mm","km"], "Voltage":["V","mV","kV"],
        "Current":["A","mA","µA"], "Resistance":["Ω","kΩ","MΩ"],
        "Capacitance":["F","mF","µF","nF","pF"], "Frequency":["Hz","kHz","MHz","GHz"],
        "Power":["W","mW","kW"], "Energy":["J","kJ","Wh","kWh"],
        "Time":["s","ms","min","h"], "Speed":["m/s","km/h"],
        "Charge":["C","mC","µC"], "Inductance":["H","mH","µH"]
    ]
    private let factors: [String: Double] = [
        "m":1,"cm":0.01,"mm":0.001,"km":1000,"V":1,"mV":0.001,"kV":1000,
        "A":1,"mA":0.001,"µA":0.000001,"Ω":1,"kΩ":1000,"MΩ":1000000,
        "F":1,"mF":0.001,"µF":0.000001,"nF":0.000000001,"pF":0.000000000001,
        "Hz":1,"kHz":1000,"MHz":1000000,"GHz":1000000000,"W":1,"mW":0.001,"kW":1000,
        "J":1,"kJ":1000,"Wh":3600,"kWh":3600000,"s":1,"ms":0.001,"min":60,"h":3600,
        "m/s":1,"km/h":1.0/3.6,"C":1,"mC":0.001,"µC":0.000001,"H":1,"mH":0.001,"µH":0.000001
    ]
    private var units: [String] { groups[group] ?? ["m","cm"] }
    private var conversion: Double? {
        guard let number = Double(amount), number.isFinite, let a = factors[from], let b = factors[to] else { return nil }
        return number * a / b
    }
    private var graphValues: [CGFloat] {
        (0...500).map { index in
            let x = -graphRange + 2 * graphRange * Double(index) / 500
            let y: Double
            switch expression.trimmingCharacters(in: .whitespaces).lowercased() {
            case "cos(x)": y = cos(x)
            case "tan(x)": y = tan(x).clamped(to: -5...5)
            case "x": y = x
            case "x^2": y = x*x
            case "1/x": y = abs(x) < 0.05 ? .nan : 1/x
            case "exp(x)": y = min(exp(x), 10)
            default: y = sin(x)
            }
            return y.isFinite ? CGFloat(y) : .nan
        }
    }
    private var savedQuestions: [ScribeNode] { library.visibleNodes.filter { $0.kind == "study_question" }.sorted { $0.createdAt > $1.createdAt } }
    private var currentQuestion: ScribeNode? { practiceIDs.indices.contains(practiceIndex) ? library.node(practiceIDs[practiceIndex]) : nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("SCRIBE / WORKBENCH").font(.caption.weight(.semibold)).tracking(2).foregroundStyle(.secondary)
                    Text("Academic workbench").font(.largeTitle.weight(.semibold))
                    Text("Offline tools for calculations, notation and visual thinking.").foregroundStyle(.secondary)
                }
                Spacer()
                Picker("Tool", selection: $tool) {
                    ForEach(["Converter","Graph","Equations","Circuit","Tables","Formula","Questions","Practice","Reminders","Diagrams"], id: \.self) { Text($0).tag($0) }
                }.frame(width: 170)
            }
            Divider()
            ScrollView {
                Group {
                    switch tool {
                    case "Converter": converterView
                    case "Graph": graphView
                    case "Equations": equationView
                    case "Circuit": circuitView
                    case "Tables": tableView
                    case "Formula": formulaView
                    case "Questions": questionEditorView
                    case "Practice": practiceView
                    case "Reminders": reminderView
                    default: diagramView
                    }
                }
                .frame(maxWidth: 900, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.bottom, 28)
            }
        }
        .padding(26)
        .navigationTitle("Study tools")
    }

    private var converterView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("ENGINEERING UNIT CONVERTER").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 115))], alignment: .leading, spacing: 8) {
                ForEach(groups.keys.sorted(), id: \.self) { key in
                    Button(key) {
                        group = key
                        from = groups[key]!.first!
                        to = groups[key]!.dropFirst().first ?? groups[key]!.first!
                    }.buttonStyle(.borderedProminent).tint(group == key ? Color.accentColor : Color.gray.opacity(0.2))
                }
            }
            TextField("Value", text: $amount).textFieldStyle(.roundedBorder).frame(maxWidth: 300)
            HStack(spacing: 12) {
                Picker("From", selection: $from) { ForEach(units, id: \.self) { Text($0).tag($0) } }.frame(maxWidth: 180)
                Image(systemName: "arrow.right").foregroundStyle(.secondary)
                Picker("To", selection: $to) { ForEach(units, id: \.self) { Text($0).tag($0) } }.frame(maxWidth: 180)
            }
            if let conversion {
                Text(conversion, format: .number.precision(.significantDigits(1...9))).font(.system(size: 34, weight: .medium, design: .rounded))
                Text("\(amount) \(from) = \(conversion.formatted(.number.precision(.significantDigits(1...9)))) \(to)").foregroundStyle(.secondary)
            } else { Text("Enter a valid finite number.").foregroundStyle(.secondary) }
        }.padding(20).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var graphView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("FUNCTION PLOTTER").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("sin(x), cos(x), tan(x), x, x^2, 1/x, exp(x)", text: $expression).textFieldStyle(.roundedBorder)
            HStack { Text("X range ±\(graphRange, specifier: "%.1f")"); Slider(value: $graphRange, in: 1...30) }
            GraphPreview(values: graphValues)
            Button("Save graph to formula library") {
                _ = library.add(kind: "formula", title: "y = \(expression)", body: "y = \(expression)\nRange: ±\(graphRange) (radians)", tags: "type:graph-formula")
            }.buttonStyle(.borderedProminent)
            Text("Supported functions are evaluated locally; trigonometric functions use radians.").font(.caption).foregroundStyle(.secondary)
        }
    }

    private var equationView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("EQUATION & SYMBOL PAD").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("Formula name", text: $formulaName).textFieldStyle(.roundedBorder)
            TextEditor(text: $formulaSource).font(.system(.body, design: .monospaced)).frame(minHeight: 100)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(.quaternary))
            Text("RENDERED EQUATION").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            MathJaxPreview(source: formulaSource.isEmpty ? "\\text{Your equation will appear here}" : formulaSource)
                .frame(minHeight: 84, maxHeight: 160)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(.quaternary.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))
            let symbols = ["α","β","γ","θ","λ","μ","π","ω","Δ","Σ","∫","∂","∇","√","∞","±","×","÷","≤","≥","≠","→","↔","₀","₁","₂","³","⁴","⁵","²","⁻¹","/","^","_","{","}"]
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 6) {
                ForEach(symbols, id: \.self) { symbol in Button(symbol) { formulaSource += symbol }.buttonStyle(.bordered) }
            }
            Button("Save formula") { _ = library.add(kind: "formula", title: formulaName, body: formulaSource, tags: "type:equation;source:latex") }
                .disabled(formulaSource.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty).buttonStyle(.borderedProminent)
            Text("LaTeX is rendered with MathJax when online; if it cannot load, the original source remains visible.").font(.caption).foregroundStyle(.secondary)
        }
    }

    private var circuitView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("CIRCUIT SKETCHPAD").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            Text("Choose a component, record its value and save a schematic note.").foregroundStyle(.secondary)
            Picker("Component", selection: $component) { ForEach(["Resistor","Capacitor","Inductor","Diode","LED","Battery"], id: \.self) { Text($0).tag($0) } }
                .pickerStyle(.segmented)
            CircuitSymbolPreview(component: component).frame(height: 150).padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
            HStack {
                TextField("Reference", text: $reference).textFieldStyle(.roundedBorder)
                TextField("Value", text: $componentValue).textFieldStyle(.roundedBorder)
            }
            TextField("Circuit notes", text: $componentNotes, axis: .vertical).textFieldStyle(.roundedBorder)
            Stepper("Components in sketch: \(componentCount)", value: $componentCount, in: 1...100)
            Button("Save circuit note") {
                let body = "Component: \(component)\nReference: \(reference)\nValue: \(componentValue)\nQuantity in sketch: \(componentCount)\nNotes: \(componentNotes)"
                _ = library.add(kind: "diagram", title: "Circuit · \(reference)", body: body, tags: "type:circuit;subject:electronics")
            }.buttonStyle(.borderedProminent)
            Text("This is a schematic starter, not a full circuit CAD editor or simulator.").font(.caption).foregroundStyle(.secondary)
        }
    }

    private var tableView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("SCIENTIFIC TABLE").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("Table title", text: $tableTitle).textFieldStyle(.roundedBorder)
            HStack {
                Button("Add row") { if cells.count < 12 { cells.append(Array(repeating: "", count: cells.first?.count ?? 3)) } }
                Button("Add column") { if (cells.first?.count ?? 0) < 8 { cells = cells.map { $0 + [""] } } }
                Button("Reset") { cells = Array(repeating: Array(repeating: "", count: 3), count: 4) }
            }.buttonStyle(.bordered)
            ScrollView(.horizontal) {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(cells.indices, id: \.self) { row in
                        HStack {
                            ForEach(cells[row].indices, id: \.self) { column in
                                TextField(row == 0 ? "Col \(column + 1)" : "", text: Binding(
                                    get: { cells[row][column] },
                                    set: { value in cells[row][column] = value }
                                )).textFieldStyle(.roundedBorder).frame(width: 125)
                            }
                        }
                    }
                }.padding(10)
            }.background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
            Button("Save table to library") {
                let tsv = cells.map { $0.joined(separator: "\t") }.joined(separator: "\n")
                _ = library.add(kind: "block", title: tableTitle, body: tsv, tags: "type:table;format:tsv")
            }.buttonStyle(.borderedProminent)
        }
    }

    private var formulaView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("FORMULA SHELF").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("Find a formula", text: $formulaQuery).textFieldStyle(.roundedBorder)
            let presets = [
                ("Ohm's law","V = IR"),("Electrical power","P = VI = I²R = V²/R"),
                ("Capacitive reactance","Xc = 1/(2πfC)"),("Inductive reactance","XL = 2πfL"),
                ("Resonant frequency","f₀ = 1/(2π√LC)"),("RC time constant","τ = RC"),
                ("Kirchhoff's current law","ΣI = 0"),("Kirchhoff's voltage law","ΣV = 0"),
                ("Voltage divider","Vout = Vin · R₂/(R₁ + R₂)"),("Non-inverting op-amp","Av = 1 + Rf/Rin")
            ]
            Text("ENGINEERING ESSENTIALS").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(presets.filter { formulaQuery.isEmpty || $0.0.localizedCaseInsensitiveContains(formulaQuery) || $0.1.localizedCaseInsensitiveContains(formulaQuery) }, id: \.0) { preset in
                HStack {
                    VStack(alignment: .leading) { Text(preset.0).fontWeight(.medium); Text(preset.1).foregroundStyle(.tint) }
                    Spacer()
                    Button { _ = library.add(kind: "formula", title: preset.0, body: preset.1, tags: "subject:engineering") } label: { Image(systemName: "plus.circle") }
                }.padding(12).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
            }
            Text("YOUR SAVED FORMULAS · \(library.visibleNodes.filter { $0.kind == "formula" }.count)").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(library.visibleNodes.filter { $0.kind == "formula" }) { item in
                VStack(alignment: .leading) { Text(item.title).fontWeight(.medium); Text(item.body).foregroundStyle(.secondary) }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(10).background(.quaternary.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private var questionEditorView: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("QUESTION BANK").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("Question / prompt", text: $questionPrompt, axis: .vertical).textFieldStyle(.roundedBorder)
            TextField("Topic / subject", text: $questionTopic).textFieldStyle(.roundedBorder)
            ForEach(questionOptions.indices, id: \.self) { index in
                TextField("Option \(["A","B","C","D"][index])", text: Binding(
                    get: { questionOptions[index] }, set: { questionOptions[index] = $0 }
                )).textFieldStyle(.roundedBorder)
            }
            HStack {
                Text("Correct option")
                Picker("Correct option", selection: $correctOption) { ForEach(["A","B","C","D"], id: \.self) { Text($0).tag($0) } }.frame(width: 100)
            }
            TextField("Explanation (optional)", text: $explanation, axis: .vertical).textFieldStyle(.roundedBorder)
            Button("Save question") {
                let body = questionPrompt + "\n---OPTIONS---\n" + questionOptions.joined(separator: "\n") + "\n---CORRECT---\n" + correctOption + "\n---EXPLANATION---\n" + explanation
                _ = library.add(kind: "study_question", title: questionPrompt, body: body, tags: "subject:\(questionTopic)")
                questionPrompt = ""; questionTopic = ""; questionOptions = Array(repeating: "", count: 4); explanation = ""
            }.disabled(questionPrompt.isEmpty || questionOptions.contains(where: { $0.isEmpty })).buttonStyle(.borderedProminent)
            Divider()
            Text("SAVED QUESTIONS · \(savedQuestions.count)").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(savedQuestions) { item in
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title).fontWeight(.medium)
                    Text(item.body.components(separatedBy: "\n---OPTIONS---\n").first ?? item.body).font(.caption).foregroundStyle(.secondary)
                }.padding(10).frame(maxWidth: .infinity, alignment: .leading).background(.quaternary.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private var practiceView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("PRACTICE MODE").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            if practiceIDs.isEmpty {
                Text("\(savedQuestions.count) questions available").font(.title2)
                Text("Start a mixed practice set. Your score is calculated locally.").foregroundStyle(.secondary)
                Button("Start practice") {
                    practiceIDs = savedQuestions.shuffled().map(\.id)
                    practiceIndex = 0; practiceScore = 0; practiceSelection = nil; practiceSubmitted = false
                }.disabled(savedQuestions.isEmpty).buttonStyle(.borderedProminent)
            } else if practiceIndex >= practiceIDs.count || currentQuestion == nil {
                Text("Practice complete").font(.title2.weight(.semibold))
                Text("\(practiceScore) / \(practiceIDs.count)").font(.largeTitle).foregroundStyle(.tint)
                Button("Finish") { practiceIDs = [] }.buttonStyle(.borderedProminent)
            } else if let item = currentQuestion {
                let parsed = parseQuestion(item)
                Text("QUESTION \(practiceIndex + 1) OF \(practiceIDs.count)").font(.caption).foregroundStyle(.secondary)
                Text(parsed.prompt).font(.title2.weight(.medium))
                ForEach(parsed.options.indices, id: \.self) { index in
                    Button {
                        if !practiceSubmitted { practiceSelection = index }
                    } label: {
                        HStack {
                            Text("\(["A","B","C","D"][index]).  \(parsed.options[index])")
                            Spacer()
                            if practiceSubmitted && ["A","B","C","D"][index] == parsed.correct { Image(systemName: "checkmark.circle.fill").foregroundStyle(.green) }
                        }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
                            .background(practiceSelection == index ? Color.accentColor.opacity(0.16) : Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 10))
                    }.buttonStyle(.plain)
                }
                if practiceSubmitted && !parsed.explanation.isEmpty { Text("Explanation: \(parsed.explanation)").foregroundStyle(.secondary) }
                Button(practiceSubmitted ? (practiceIndex == practiceIDs.count - 1 ? "See results" : "Next question") : "Check answer") {
                    if practiceSubmitted {
                        practiceIndex += 1; practiceSelection = nil; practiceSubmitted = false
                    } else {
                        if practiceSelection == ["A","B","C","D"].firstIndex(of: parsed.correct) { practiceScore += 1 }
                        practiceSubmitted = true
                    }
                }.disabled(!practiceSubmitted && practiceSelection == nil).buttonStyle(.borderedProminent)
                Button("End practice early") { practiceIDs = []; practiceSelection = nil; practiceSubmitted = false }.buttonStyle(.bordered)
            }
        }
    }

    private var reminderView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("LOCAL STUDY REMINDERS").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            Text("Plan a revision session. Reminders are stored locally on this Mac.")
            TextField("Reminder title", text: $reminderTitle).textFieldStyle(.roundedBorder)
            TextField("What should you review?", text: $reminderMessage, axis: .vertical).textFieldStyle(.roundedBorder)
            DatePicker("Date and time", selection: $reminderDate, in: Date.now..., displayedComponents: [.date,.hourAndMinute])
            Button("Schedule reminder") { scheduleOneOffReminder() }.disabled(reminderTitle.trimmingCharacters(in: .whitespaces).isEmpty).buttonStyle(.borderedProminent)
            if !reminderStatus.isEmpty { Text(reminderStatus).font(.caption).foregroundStyle(.secondary) }
            Divider()
            Text("UPCOMING REMINDERS").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(library.visibleNodes.filter { $0.kind == "study_reminder" }.sorted { reminderDateValue($0) < reminderDateValue($1) }) { item in
                HStack {
                    VStack(alignment: .leading) { Text(item.title).fontWeight(.medium); Text(item.body).font(.caption).foregroundStyle(.secondary); Text(reminderDateValue(item).formatted()).font(.caption2).foregroundStyle(.secondary) }
                    Spacer()
                    Button(role: .destructive) { UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [item.id.uuidString]); library.moveToTrash(item.id) } label: { Image(systemName: "trash") }
                }.padding(10).background(.quaternary.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private var diagramView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("DIAGRAM & CONCEPT MAP STARTER").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("Diagram title", text: $diagramTitle).textFieldStyle(.roundedBorder)
            TextField("Node labels, separated by commas", text: $diagramLabels, axis: .vertical).textFieldStyle(.roundedBorder)
            ConceptMapPreview(labels: diagramLabels).frame(height: 200).padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
            Button("Save diagram outline") {
                let nodes = diagramLabels.split(separator: ",").map { "• " + $0.trimmingCharacters(in: .whitespaces) }.joined(separator: "\n")
                _ = library.add(kind: "diagram", title: diagramTitle, body: nodes, tags: "type:concept-map")
            }.buttonStyle(.borderedProminent)
            Text("Circuit sketches and graph plots are available in their own workbench sections.").font(.caption).foregroundStyle(.secondary)
        }
    }

    private func scheduleOneOffReminder() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert,.sound]) { granted, error in
            guard granted else { DispatchQueue.main.async { reminderStatus = error?.localizedDescription ?? "Notification permission was not granted." }; return }
            let id = UUID()
            var components = Calendar.current.dateComponents([.year,.month,.day,.hour,.minute], from: reminderDate)
            components.second = 0
            let content = UNMutableNotificationContent()
            content.title = reminderTitle
            content.body = reminderMessage
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(identifier: id.uuidString, content: content, trigger: trigger)
            UNUserNotificationCenter.current().add(request) { error in
                DispatchQueue.main.async {
                    if let error { reminderStatus = error.localizedDescription }
                    else {
                        _ = library.add(kind: "study_reminder", title: reminderTitle, body: reminderMessage, tags: "dueAt:\(reminderDate.timeIntervalSince1970);notificationID:\(id.uuidString)")
                        reminderStatus = "Reminder scheduled."
                        reminderTitle = ""
                    }
                }
            }
        }
    }
}

private func reminderDateValue(_ node: ScribeNode) -> Date {
    let value = node.tags.split(separator: ";").first(where: { $0.hasPrefix("dueAt:") }).flatMap { Double($0.dropFirst(6)) } ?? 0
    return Date(timeIntervalSince1970: value)
}

private func parseQuestion(_ item: ScribeNode) -> (prompt: String, options: [String], correct: String, explanation: String) {
    let optionMarker = "\n---OPTIONS---\n", answerMarker = "\n---CORRECT---\n", explanationMarker = "\n---EXPLANATION---\n"
    let optionsPart = item.body.components(separatedBy: optionMarker).dropFirst().first ?? ""
    let correctPart = optionsPart.components(separatedBy: answerMarker).dropFirst().first ?? "A"
    let explanation = correctPart.components(separatedBy: explanationMarker).dropFirst().first ?? ""
    return (item.body.components(separatedBy: optionMarker).first ?? item.body,
            optionsPart.components(separatedBy: answerMarker).first?.split(separator: "\n").map(String.init) ?? [],
            correctPart.components(separatedBy: explanationMarker).first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "A",
            explanation)
}

private struct CircuitSymbolPreview: View {
    let component: String
    var body: some View {
        Canvas { context, size in
            let y = size.height / 2, left = size.width * 0.15, right = size.width * 0.85
            var wires = Path(); wires.move(to: CGPoint(x: left, y: y)); wires.addLine(to: CGPoint(x: size.width * 0.36, y: y))
            wires.move(to: CGPoint(x: size.width * 0.64, y: y)); wires.addLine(to: CGPoint(x: right, y: y))
            context.stroke(wires, with: .color(.accentColor), lineWidth: 2)
            var symbol = Path()
            switch component {
            case "Capacitor":
                symbol.move(to: CGPoint(x: size.width * 0.47, y: y - 30)); symbol.addLine(to: CGPoint(x: size.width * 0.47, y: y + 30))
                symbol.move(to: CGPoint(x: size.width * 0.53, y: y - 30)); symbol.addLine(to: CGPoint(x: size.width * 0.53, y: y + 30))
            case "Battery":
                symbol.move(to: CGPoint(x: size.width * 0.48, y: y - 30)); symbol.addLine(to: CGPoint(x: size.width * 0.48, y: y + 30))
                symbol.move(to: CGPoint(x: size.width * 0.54, y: y - 18)); symbol.addLine(to: CGPoint(x: size.width * 0.54, y: y + 18))
            case "Diode", "LED":
                symbol.move(to: CGPoint(x: size.width * 0.43, y: y - 24)); symbol.addLine(to: CGPoint(x: size.width * 0.57, y: y)); symbol.addLine(to: CGPoint(x: size.width * 0.43, y: y + 24)); symbol.closeSubpath()
                symbol.move(to: CGPoint(x: size.width * 0.59, y: y - 24)); symbol.addLine(to: CGPoint(x: size.width * 0.59, y: y + 24))
            default:
                symbol.move(to: CGPoint(x: size.width * 0.36, y: y))
                for index in 0...5 { symbol.addLine(to: CGPoint(x: size.width * CGFloat(0.39 + Double(index) * 0.05), y: y + (index.isMultiple(of: 2) ? -16 : 16))) }
                symbol.addLine(to: CGPoint(x: size.width * 0.64, y: y))
            }
            context.stroke(symbol, with: .color(.accentColor), style: StrokeStyle(lineWidth: 2, lineJoin: .round))
        }
    }
}

private struct ConceptMapPreview: View {
    let labels: String
    var body: some View {
        let nodes = labels.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.prefix(6)
        Canvas { context, size in
            let center = CGPoint(x: size.width/2, y: size.height/2)
            for index in nodes.indices where index > 0 {
                let angle = Double(index-1) * 2 * .pi / Double(max(nodes.count-1,1))
                let point = CGPoint(x: center.x + CGFloat(cos(angle))*size.width*0.34, y: center.y + CGFloat(sin(angle))*size.height*0.34)
                var line = Path(); line.move(to: center); line.addLine(to: point)
                context.stroke(line, with: .color(.secondary.opacity(0.7)), lineWidth: 1.5)
            }
            context.fill(Path(ellipseIn: CGRect(x: center.x-48,y: center.y-20,width:96,height:40)), with: .color(.accentColor.opacity(0.2)))
            for index in nodes.indices where index > 0 {
                let angle = Double(index-1) * 2 * .pi / Double(max(nodes.count-1,1))
                let point = CGPoint(x: center.x + cos(angle)*size.width*0.34, y: center.y + sin(angle)*size.height*0.34)
                context.fill(RoundedRectangle(cornerRadius: 8).path(in: CGRect(x: point.x-42,y: point.y-16,width:84,height:32)), with: .color(.secondary.opacity(0.18)))
            }
        }
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self { min(max(self, range.lowerBound), range.upperBound) }
}
