import SwiftUI
import ScribeCore

private struct WorkspaceSyllabusTask: Codable, Identifiable {
    var id = UUID()
    var title: String
    var done = false
}
private struct WorkspaceExamRecord: Codable, Identifiable {
    var id = UUID()
    var title: String
    var subject: String
    var date: String
    var tasks: [WorkspaceSyllabusTask] = []
}
private struct WorkspaceBookmarkFolder: Codable, Identifiable {
    var id = UUID()
    var name: String
}

struct StudyWorkspaceView: View {
    @ObservedObject var store: ScribeStore
    @AppStorage("scribe.workspace.exams") private var examsData = "[]"
    @AppStorage("scribe.workspace.folders") private var foldersData = "[]"
    @AppStorage("scribe.workspace.folderLinks") private var folderLinksData = "{}"
    @AppStorage("scribe.workspace.reflectionWins") private var reflectionWins = ""
    @AppStorage("scribe.workspace.reflectionChallenges") private var reflectionChallenges = ""
    @AppStorage("scribe.workspace.reflectionNext") private var reflectionNext = ""
    @AppStorage("scribe.workspace.dailyGoalMinutes") private var dailyGoalMinutes = 120
    @State private var exams: [WorkspaceExamRecord] = []
    @State private var folders: [WorkspaceBookmarkFolder] = []
    @State private var folderLinks: [String: String] = [:]
    @State private var section = "Overview"
    @State private var showingExamForm = false
    @State private var showingFolderForm = false
    @State private var examTitle = ""
    @State private var examSubject = ""
    @State private var examDate = ""
    @State private var folderName = ""
    @State private var focusSubject = ""
    @State private var focusTopic = ""
    @State private var focusMinutes = 25
    @State private var bookmarkSearch = ""

    private let sections = ["Overview", "Exams", "Focus", "Bookmarks", "Reflect"]

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("STUDY WORKSPACE").font(.caption.bold()).tracking(1.6).foregroundStyle(.secondary)
                    Text("Plan, focus and reflect").font(.largeTitle.bold())
                }
                Spacer()
                Picker("Workspace section", selection: $section) {
                    ForEach(sections, id: \.self) { Text($0).tag($0) }
                }.pickerStyle(.segmented).frame(maxWidth: 520)
            }
            Group {
                switch section {
                case "Exams": examsView
                case "Focus": focusView
                case "Bookmarks": bookmarksView
                case "Reflect": reflectionView
                default: overviewView
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .padding(26)
        .onAppear(perform: load)
        .onChange(of: reflectionWins) { _ in saveReflection() }
        .onChange(of: reflectionChallenges) { _ in saveReflection() }
        .onChange(of: reflectionNext) { _ in saveReflection() }
        .onChange(of: dailyGoalMinutes) { _ in saveDailyGoal() }
        .sheet(isPresented: $showingExamForm) { examForm }
        .sheet(isPresented: $showingFolderForm) { folderForm }
    }

    private var overviewView: some View {
        let sessions = store.items.filter { $0.kind == "study_session" && $0.deletedAt == nil }
            .sorted { $0.createdAt > $1.createdAt }
        let weekStart = Calendar.current.date(byAdding: .day, value: -6, to: Calendar.current.startOfDay(for: Date())) ?? Date()
        let weekMinutes = sessions.filter { Date(timeIntervalSince1970: Double($0.createdAt) / 1000) >= weekStart }
            .reduce(0) { $0 + minutes(from: $1) }
        let todayMinutes = ScribeStudyProgress.minutesStudied(on: Date(), sessions: sessions)
        let daysWithSessions = Set(sessions.filter { minutes(from: $0) > 0 }.map { Calendar.current.startOfDay(for: Date(timeIntervalSince1970: Double($0.createdAt) / 1000)) })
        var streak = 0
        var cursor = Calendar.current.startOfDay(for: Date())
        while daysWithSessions.contains(cursor) && streak < 365 {
            streak += 1
            cursor = Calendar.current.date(byAdding: .day, value: -1, to: cursor) ?? cursor
            if streak == 365 { break }
        }
        let done = exams.reduce(0) { $0 + $1.tasks.filter(\.done).count }
        let total = exams.reduce(0) { $0 + $1.tasks.count }
        return ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 12) {
                    metric("WEEKLY STUDY", "\(weekMinutes) min")
                    metric("EXAMS PLANNED", "\(exams.count)")
                    metric("SYLLABUS", "\(done)/\(total)")
                    metric("BOOKMARKS", "\(store.items.filter { $0.bookmarked && $0.deletedAt == nil }.count)")
                    metric("STREAK", "\(streak) days")
                }
                VStack(alignment: .leading, spacing: 8) {
                    HStack { Text("DAILY STUDY GOAL").font(.caption.bold()).foregroundStyle(.secondary); Spacer(); Text("\(todayMinutes) / \(dailyGoalMinutes) min").font(.headline) }
                    ProgressView(value: min(1, Double(todayMinutes) / Double(max(1, dailyGoalMinutes))))
                    Slider(value: Binding(get: { Double(dailyGoalMinutes) }, set: { dailyGoalMinutes = Int($0) }), in: 30...600, step: 15)
                    Text("Your target is saved to the shared study workspace.").font(.caption).foregroundStyle(.secondary)
                }.padding(14).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                Text("Upcoming exams").font(.title2.bold())
                if exams.isEmpty { empty("No exams planned", "Add an exam and build a checklist from its syllabus.") }
                ForEach(exams.sorted { $0.date < $1.date }.prefix(4)) { exam in
                    VStack(alignment: .leading, spacing: 5) {
                        Text(exam.title).font(.headline)
                        Text("\(exam.subject) · \(exam.date)").foregroundStyle(.secondary)
                        Text(examCountdown(exam.date)).font(.caption).foregroundStyle(.secondary)
                        ProgressView(value: Double(exam.tasks.filter(\.done).count), total: Double(max(1, exam.tasks.count)))
                    }.padding(14).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                }
                Text("Recent focus sessions").font(.title2.bold())
                if sessions.isEmpty { empty("No sessions yet", "Log completed focus sessions to build your study history.") }
                ForEach(sessions.prefix(5)) { session in
                    HStack {
                        Image(systemName: "timer").foregroundStyle(.secondary)
                        Text(session.title)
                        Spacer()
                        Text("\(minutes(from: session)) min").foregroundStyle(.secondary)
                    }.padding(.vertical, 4)
                }
            }
        }
    }

    private var examsView: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack { Text("Exam planner").font(.title2.bold()); Spacer(); Button { showingExamForm = true } label: { Label("Add exam", systemImage: "plus") }.buttonStyle(.borderedProminent) }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if exams.isEmpty { empty("Your exam plan starts here", "Create an exam, then add each chapter or topic as a checklist item.") }
                    ForEach(exams) { exam in
                        VStack(alignment: .leading, spacing: 9) {
                            HStack {
                                VStack(alignment: .leading) { Text(exam.title).font(.headline); Text("\(exam.subject) · \(exam.date)").font(.caption).foregroundStyle(.secondary) }
                                Spacer()
                                Button(role: .destructive) { exams.removeAll { $0.id == exam.id }; saveExams() } label: { Image(systemName: "trash") }.buttonStyle(.borderless)
                            }
                            ForEach(exam.tasks) { task in
                                Toggle(task.title, isOn: Binding(
                                    get: { exams.first(where: { $0.id == exam.id })?.tasks.first(where: { $0.id == task.id })?.done ?? false },
                                    set: { value in
                                        guard let e = exams.firstIndex(where: { $0.id == exam.id }),
                                              let t = exams[e].tasks.firstIndex(where: { $0.id == task.id }) else { return }
                                        exams[e].tasks[t].done = value; saveExams()
                                    }
                                )).toggleStyle(.checkbox)
                            }
                            AddSyllabusTask { title in
                                guard let index = exams.firstIndex(where: { $0.id == exam.id }) else { return }
                                exams[index].tasks.append(WorkspaceSyllabusTask(title: title)); saveExams()
                            }
                        }.padding(16).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                    }
                }
            }
        }
    }

    private var focusView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Focus session log").font(.title2.bold())
            HStack(spacing: 6) {
                ForEach([15, 25, 50, 90], id: \.self) { minutes in
                    Button("\(minutes)m") { focusMinutes = minutes }.buttonStyle(focusMinutes == minutes ? .borderedProminent : .bordered)
                }
            }
            HStack {
                TextField("Subject", text: $focusSubject).textFieldStyle(.roundedBorder)
                TextField("Topic", text: $focusTopic).textFieldStyle(.roundedBorder)
                Stepper("\(focusMinutes) min", value: $focusMinutes, in: 1...240, step: 5).frame(width: 125)
                Button("Save session") {
                    let subject = focusSubject.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !subject.isEmpty else { return }
                    store.create(kind: "study_session", title: subject, tags: "minutes:\(focusMinutes);topic:\(focusTopic)", switchToLibrary: false)
                    focusSubject = ""; focusTopic = ""
                }.buttonStyle(.borderedProminent)
            }
            Text("The Focus tool already provides the running timer. This log records subject-specific sessions in the shared local study history.")
                .font(.caption).foregroundStyle(.secondary)
            List(store.items.filter { $0.kind == "study_session" && $0.deletedAt == nil }.sorted { $0.createdAt > $1.createdAt }) { session in
                HStack { Text(session.title); Spacer(); Text("\(minutes(from: session)) min").foregroundStyle(.secondary) }
            }.listStyle(.inset)
        }
    }

    private var bookmarksView: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack { Text("Bookmark folders").font(.title2.bold()); Spacer(); Button { showingFolderForm = true } label: { Label("New folder", systemImage: "folder.badge.plus") } }
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(folders) { folder in
                        HStack {
                            Image(systemName: "folder").foregroundStyle(.secondary)
                            Text(folder.name)
                            Spacer()
                            Text("\(folderLinks.values.filter { $0 == folder.id.uuidString }.count) items").foregroundStyle(.secondary)
                            Button(role: .destructive) { folders.removeAll { $0.id == folder.id }; folderLinks = folderLinks.filter { $0.value != folder.id.uuidString }; saveFolders() } label: { Image(systemName: "trash") }.buttonStyle(.borderless)
                        }.padding(10).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                    }
                    TextField("Search saved notes", text: $bookmarkSearch).textFieldStyle(.roundedBorder)
                    Text("Saved notes").font(.title2.bold()).padding(.top, 8)
                    ForEach(store.items.filter { $0.bookmarked && $0.deletedAt == nil && (bookmarkSearch.isEmpty || $0.title.localizedCaseInsensitiveContains(bookmarkSearch) || $0.body.localizedCaseInsensitiveContains(bookmarkSearch)) }) { item in
                        HStack {
                            VStack(alignment: .leading) { Text(item.title); Text(item.kind.capitalized).font(.caption).foregroundStyle(.secondary) }
                            Spacer()
                            Picker("Folder", selection: Binding(
                                get: { folderLinks[bookmarkKey(item)] ?? "" },
                                set: { value in
                                    let key = ensureBookmarkKey(item)
                                    if value.isEmpty { folderLinks.removeValue(forKey: key) }
                                    else { folderLinks[key] = value }
                                    saveFolders()
                                }
                            )) {
                                Text("Unfiled").tag("")
                                ForEach(folders) { Text($0.name).tag($0.id.uuidString) }
                            }.frame(width: 180)
                        }.padding(10).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
        }
    }

    private var reflectionView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Weekly reflection").font(.title2.bold())
            Text("A short review helps turn study sessions into a plan for the next week. Your reflection is saved on this Mac.")
                .font(.callout).foregroundStyle(.secondary)
            Group {
                Text("What went well?").font(.headline)
                TextEditor(text: $reflectionWins).frame(minHeight: 90).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                Text("What was challenging?").font(.headline)
                TextEditor(text: $reflectionChallenges).frame(minHeight: 90).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
                Text("Priorities for next week").font(.headline)
                TextEditor(text: $reflectionNext).frame(minHeight: 90).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    private var examForm: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Plan an exam").font(.title2.bold())
            TextField("Exam name", text: $examTitle).textFieldStyle(.roundedBorder)
            TextField("Subject", text: $examSubject).textFieldStyle(.roundedBorder)
            TextField("Date (YYYY-MM-DD)", text: $examDate).textFieldStyle(.roundedBorder)
            HStack { Spacer(); Button("Cancel") { showingExamForm = false }; Button("Save") {
                guard !examTitle.isEmpty, !examSubject.isEmpty, examDate.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil else { return }
                exams.append(WorkspaceExamRecord(title: examTitle, subject: examSubject, date: examDate))
                saveExams(); examTitle = ""; examSubject = ""; examDate = ""; showingExamForm = false
            }.buttonStyle(.borderedProminent) }
        }.padding(22).frame(width: 420)
    }
    private var folderForm: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("New bookmark folder").font(.title2.bold())
            TextField("Folder name", text: $folderName).textFieldStyle(.roundedBorder)
            HStack { Spacer(); Button("Cancel") { showingFolderForm = false }; Button("Create") {
                guard !folderName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                folders.append(WorkspaceBookmarkFolder(name: folderName)); saveFolders(); folderName = ""; showingFolderForm = false
            }.buttonStyle(.borderedProminent) }
        }.padding(22).frame(width: 380)
    }

    private func metric(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) { Text(title).font(.caption.bold()).foregroundStyle(.secondary); Text(value).font(.title2.bold()) }
            .frame(maxWidth: .infinity, alignment: .leading).padding(14).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
    private func empty(_ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline); Text(detail).font(.callout).foregroundStyle(.secondary) }
            .frame(maxWidth: .infinity, alignment: .leading).padding(16).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
    private func minutes(from item: ScribeItem) -> Int {
        Int(item.tags.range(of: #"minutes:\d+"#, options: .regularExpression).map { String(item.tags[$0].dropFirst(8)) } ?? "") ?? 0
    }
    private func load() {
        let examBody = store.items.first { $0.kind == "study_workspace_exams" && $0.deletedAt == nil }?.body
        let bookmarkBody = store.items.first { $0.kind == "study_workspace_bookmarks" && $0.deletedAt == nil }?.body
        let reflectionBody = store.items.first { $0.kind == "study_workspace_reflection" && $0.deletedAt == nil }?.body
        if let goalBody = store.items.first(where: { $0.kind == "study_workspace_daily_goal" && $0.deletedAt == nil })?.body,
           let data = goalBody.data(using: .utf8),
           let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
           let minutes = object["minutes"] as? Int { dailyGoalMinutes = min(600, max(30, minutes)) }
        exams = (try? JSONDecoder().decode([WorkspaceExamRecord].self, from: Data((examBody ?? examsData).utf8))) ?? []
        if let bookmarkBody,
           let object = (try? JSONSerialization.jsonObject(with: Data(bookmarkBody.utf8))) as? [String: Any] {
            let folderData = (try? JSONSerialization.data(withJSONObject: object["folders"] ?? [])) ?? Data("[]".utf8)
            let linksData = (try? JSONSerialization.data(withJSONObject: object["links"] ?? [:])) ?? Data("{}".utf8)
            folders = (try? JSONDecoder().decode([WorkspaceBookmarkFolder].self, from: folderData)) ?? []
            folderLinks = (try? JSONDecoder().decode([String: String].self, from: linksData)) ?? [:]
        } else {
            folders = (try? JSONDecoder().decode([WorkspaceBookmarkFolder].self, from: Data(foldersData.utf8))) ?? []
            folderLinks = (try? JSONDecoder().decode([String: String].self, from: Data(folderLinksData.utf8))) ?? [:]
        }
        if let reflectionBody,
           let object = (try? JSONSerialization.jsonObject(with: Data(reflectionBody.utf8))) as? [String: String] {
            reflectionWins = object["wins"] ?? reflectionWins
            reflectionChallenges = object["challenges"] ?? reflectionChallenges
            reflectionNext = object["nextWeek"] ?? reflectionNext
        }
    }
    private func bookmarkKey(_ item: ScribeItem) -> String {
        item.tags.split(separator: ";").map(String.init)
            .first(where: { $0.hasPrefix("bookmark_key:") })
            .map { String($0.dropFirst("bookmark_key:".count)) }
            .flatMap { $0.isEmpty ? nil : $0 } ?? "local_\(item.id)"
    }
    private func ensureBookmarkKey(_ item: ScribeItem) -> String {
        if let existing = item.tags.split(separator: ";").map(String.init)
            .first(where: { $0.hasPrefix("bookmark_key:") }) {
            let value = String(existing.dropFirst("bookmark_key:".count))
            if !value.isEmpty { return value }
        }
        let key = UUID().uuidString
        store.update(item, tags: appendingTag(item.tags, tag: "bookmark_key:\(key)"))
        return key
    }
    private func saveRecord(kind: String, title: String, body: String) {
        if let existing = store.items.first(where: { $0.kind == kind && $0.title == title && $0.deletedAt == nil }) {
            store.update(existing, body: body)
        } else {
            store.create(kind: kind, title: title, body: body, tags: "workspace", switchToLibrary: false)
        }
    }
    private func saveExams() {
        if let data = try? JSONEncoder().encode(exams) {
            let value = String(decoding: data, as: UTF8.self)
            examsData = value
            saveRecord(kind: "study_workspace_exams", title: "Exam planner", body: value)
        }
    }
    private func saveFolders() {
        if let data = try? JSONEncoder().encode(folders) { foldersData = String(decoding: data, as: UTF8.self) }
        if let data = try? JSONEncoder().encode(folderLinks) { folderLinksData = String(decoding: data, as: UTF8.self) }
        let folderObjects = folders.map { ["id": $0.id.uuidString, "name": $0.name] }
        let object: [String: Any] = ["folders": folderObjects, "links": folderLinks]
        if let data = try? JSONSerialization.data(withJSONObject: object), let value = String(data: data, encoding: .utf8) {
            saveRecord(kind: "study_workspace_bookmarks", title: "Bookmark folders", body: value)
        }
    }
    private func saveDailyGoal() {
        let body = "{\"minutes\":\(min(600, max(30, dailyGoalMinutes)))}"
        saveRecord(kind: "study_workspace_daily_goal", title: "Daily study goal", body: body)
    }
    private func examCountdown(_ value: String) -> String {
        let formatter = DateFormatter(); formatter.calendar = Calendar(identifier: .gregorian); formatter.dateFormat = "yyyy-MM-dd"; formatter.isLenient = false
        guard let date = formatter.date(from: value) else { return "Date: " + value }
        let days = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: Date()), to: Calendar.current.startOfDay(for: date)).day ?? 0
        if days < 0 { return "Date passed" }; if days == 0 { return "Today" }; if days == 1 { return "Tomorrow" }; return "\(days) days remaining"
    }
    private func saveReflection() {
        let object = ["wins": reflectionWins, "challenges": reflectionChallenges, "nextWeek": reflectionNext]
        if let data = try? JSONSerialization.data(withJSONObject: object), let value = String(data: data, encoding: .utf8) {
            saveRecord(kind: "study_workspace_reflection", title: "Weekly reflection", body: value)
        }
    }
}

private struct AddSyllabusTask: View {
    var onAdd: (String) -> Void
    @State private var title = ""
    var body: some View {
        HStack {
            TextField("Add syllabus topic", text: $title).textFieldStyle(.roundedBorder)
            Button { let value = title.trimmingCharacters(in: .whitespacesAndNewlines); if !value.isEmpty { onAdd(value); title = "" } } label: { Image(systemName: "plus.circle.fill") }
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }
}
