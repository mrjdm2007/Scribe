import SwiftUI
import UserNotifications
import ScribeCore

private struct AcademicUnitDefinition: Hashable {
    let symbol: String
    let description: String
    let factor: Double
    var offset: Double = 0
}
private func academicUnit(_ symbol: String, _ description: String, _ factor: Double, _ offset: Double = 0) -> AcademicUnitDefinition {
    AcademicUnitDefinition(symbol: symbol, description: description, factor: factor, offset: offset)
}

// macOS academic tools use the same portable question encoding as Android.
struct AcademicToolsView: View {
    @EnvironmentObject private var store: ScribeStore
    @State private var section = "Converter"
    @State private var converterCategory = "Voltage"
    @State private var converterValue = "1"
    @State private var converterFrom = "V"
    @State private var converterTo = "mV"
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

    private let sections = ["Converter", "Graph", "Equations", "Circuit", "Tables", "Questions", "Practice", "Reminders", "Diagrams"]
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
        ScrollView(.vertical) {
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
                case "Converter": converter
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
                .frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .padding(24)
        }.frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private let unitGroups: [String: [AcademicUnitDefinition]] = [
        "Voltage":[academicUnit("V","volt",1),academicUnit("mV","millivolt",1e-3),academicUnit("µV","microvolt",1e-6),academicUnit("kV","kilovolt",1e3)],
        "Current":[academicUnit("A","ampere",1),academicUnit("mA","milliampere",1e-3),academicUnit("µA","microampere",1e-6),academicUnit("nA","nanoampere",1e-9)],
        "Resistance":[academicUnit("Ω","ohm",1),academicUnit("kΩ","kiloohm",1e3),academicUnit("MΩ","megaohm",1e6),academicUnit("GΩ","gigaohm",1e9)],
        "Capacitance":[academicUnit("F","farad",1),academicUnit("mF","millifarad",1e-3),academicUnit("µF","microfarad",1e-6),academicUnit("nF","nanofarad",1e-9),academicUnit("pF","picofarad",1e-12)],
        "Inductance":[academicUnit("H","henry",1),academicUnit("mH","millihenry",1e-3),academicUnit("µH","microhenry",1e-6),academicUnit("nH","nanohenry",1e-9)],
        "Conductance":[academicUnit("S","siemens",1),academicUnit("mS","millisiemens",1e-3),academicUnit("µS","microsiemens",1e-6)],
        "Frequency":[academicUnit("Hz","hertz",1),academicUnit("kHz","kilohertz",1e3),academicUnit("MHz","megahertz",1e6),academicUnit("GHz","gigahertz",1e9),academicUnit("rpm","revolutions/min",1.0/60.0)],
        "Power":[academicUnit("W","watt",1),academicUnit("mW","milliwatt",1e-3),academicUnit("kW","kilowatt",1e3),academicUnit("MW","megawatt",1e6),academicUnit("hp","mechanical horsepower",745.6998716)],
        "Energy":[academicUnit("J","joule",1),academicUnit("kJ","kilojoule",1e3),academicUnit("Wh","watt-hour",3600),academicUnit("kWh","kilowatt-hour",3.6e6),academicUnit("eV","electronvolt",1.602176634e-19)],
        "Charge":[academicUnit("C","coulomb",1),academicUnit("mC","millicoulomb",1e-3),academicUnit("µC","microcoulomb",1e-6),academicUnit("Ah","ampere-hour",3600),academicUnit("mAh","milliampere-hour",3.6)],
        "Length":[academicUnit("m","metre",1),academicUnit("mm","millimetre",1e-3),academicUnit("cm","centimetre",1e-2),academicUnit("km","kilometre",1e3),academicUnit("µm","micrometre",1e-6),academicUnit("nm","nanometre",1e-9),academicUnit("in","inch",0.0254),academicUnit("ft","foot",0.3048),academicUnit("mi","mile",1609.344)],
        "Area":[academicUnit("m²","square metre",1),academicUnit("cm²","square centimetre",1e-4),academicUnit("mm²","square millimetre",1e-6),academicUnit("km²","square kilometre",1e6),academicUnit("ha","hectare",1e4),academicUnit("ft²","square foot",0.09290304)],
        "Volume":[academicUnit("m³","cubic metre",1),academicUnit("L","litre",1e-3),academicUnit("mL","millilitre",1e-6),academicUnit("cm³","cubic centimetre",1e-6),academicUnit("ft³","cubic foot",0.028316846592),academicUnit("gal","US gallon",0.003785411784)],
        "Mass":[academicUnit("kg","kilogram",1),academicUnit("g","gram",1e-3),academicUnit("mg","milligram",1e-6),academicUnit("t","metric tonne",1e3),academicUnit("lb","pound",0.45359237),academicUnit("oz","ounce",0.028349523125)],
        "Temperature":[academicUnit("K","kelvin",1),academicUnit("°C","degree Celsius",1,273.15),academicUnit("°F","degree Fahrenheit",5.0/9.0,255.3722222222222)],
        "Time":[academicUnit("s","second",1),academicUnit("ms","millisecond",1e-3),academicUnit("µs","microsecond",1e-6),academicUnit("ns","nanosecond",1e-9),academicUnit("min","minute",60),academicUnit("h","hour",3600),academicUnit("day","day",86400)],
        "Speed":[academicUnit("m/s","metre/second",1),academicUnit("km/h","kilometre/hour",1.0/3.6),academicUnit("mph","mile/hour",0.44704),academicUnit("ft/s","foot/second",0.3048)],
        "Acceleration":[academicUnit("m/s²","metre/second²",1),academicUnit("ft/s²","foot/second²",0.3048),academicUnit("g₀","standard gravity",9.80665)],
        "Force":[academicUnit("N","newton",1),academicUnit("kN","kilonewton",1e3),academicUnit("MN","meganewton",1e6),academicUnit("lbf","pound-force",4.4482216152605)],
        "Pressure":[academicUnit("Pa","pascal",1),academicUnit("kPa","kilopascal",1e3),academicUnit("MPa","megapascal",1e6),academicUnit("bar","bar",1e5),academicUnit("atm","atmosphere",101325),academicUnit("psi","pound/in²",6894.757293168),academicUnit("mmHg","millimetre mercury",133.322387415)],
        "Density":[academicUnit("kg/m³","kilogram/cubic metre",1),academicUnit("g/cm³","gram/cubic centimetre",1000),academicUnit("g/L","gram/litre",1),academicUnit("lb/ft³","pound/cubic foot",16.01846337)],
        "Torque":[academicUnit("N·m","newton-metre",1),academicUnit("kN·m","kilonewton-metre",1e3),academicUnit("lb·ft","pound-foot",1.3558179483)],
        "Magnetic field":[academicUnit("T","tesla",1),academicUnit("mT","millitesla",1e-3),academicUnit("µT","microtesla",1e-6),academicUnit("G","gauss",1e-4)],
        "Angle":[academicUnit("rad","radian",1),academicUnit("°","degree",Double.pi/180),academicUnit("rev","revolution",2*Double.pi)]
    ]
    private var converterUnits: [AcademicUnitDefinition] { unitGroups[converterCategory] ?? unitGroups["Voltage"]! }
    private var converterResult: Double? {
        guard let value = Double(converterValue), value.isFinite,
              let source = converterUnits.first(where: { $0.symbol == converterFrom }),
              let target = converterUnits.first(where: { $0.symbol == converterTo }) else { return nil }
        return ((value * source.factor + source.offset) - target.offset) / target.factor
    }

    private var converter: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Engineering & scientific converter").font(.title2.bold())
            Text("Electrical, mechanical and scientific quantities with SI-based factors and proper temperature offsets.")
                .foregroundStyle(.secondary)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 112))], alignment: .leading, spacing: 8) {
                ForEach(unitGroups.keys.sorted(), id: \.self) { key in
                    Button(key) {
                        converterCategory = key
                        converterFrom = unitGroups[key]!.first!.symbol
                        converterTo = unitGroups[key]!.dropFirst().first?.symbol ?? unitGroups[key]!.first!.symbol
                    }.buttonStyle(.borderedProminent)
                        .tint(converterCategory == key ? Color.accentColor : Color.secondary.opacity(0.2))
                }
            }
            TextField("Value to convert", text: $converterValue)
                .textFieldStyle(.roundedBorder).frame(maxWidth: 360)
            HStack(spacing: 12) {
                Picker("From", selection: $converterFrom) {
                    ForEach(converterUnits, id: \.symbol) { Text($0.symbol).tag($0.symbol) }
                }.frame(maxWidth: 220)
                Image(systemName: "arrow.right").foregroundStyle(.secondary)
                Picker("To", selection: $converterTo) {
                    ForEach(converterUnits, id: \.symbol) { Text($0.symbol).tag($0.symbol) }
                }.frame(maxWidth: 220)
            }
            if let converterResult {
                Text(converterResult, format: .number.precision(.significantDigits(1...9)))
                    .font(.system(size: 34, weight: .medium, design: .rounded))
                Text("\(converterValue) \(converterFrom) = \(converterResult.formatted(.number.precision(.significantDigits(1...9)))) \(converterTo)")
                    .foregroundStyle(.secondary)
            } else {
                Text("Enter a valid finite number.").foregroundStyle(.secondary)
            }
            Divider()
            Text("UNIT REFERENCE").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(converterUnits, id: \.symbol) { unit in
                HStack { Text(unit.symbol).fontWeight(.medium); Spacer(); Text(unit.description).foregroundStyle(.secondary) }
            }
        }
        .padding(20)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
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
