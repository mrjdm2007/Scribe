import SwiftUI

@main
struct ScribeApp: App {
    @StateObject private var library = ScribeLibrary()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(library)
                .frame(minWidth: 900, minHeight: 620)
        }
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("New Course") { library.addCourse() }
                    .keyboardShortcut("n", modifiers: [.command, .shift])
            }
        }
    }
}

struct ContentView: View {
    @EnvironmentObject private var library: ScribeLibrary
    @State private var selection: UUID?
    @State private var search = ""

    private var filteredCourses: [ScribeNode] {
        let courses = library.visibleCourses
        guard !search.isEmpty else { return courses }
        return courses.filter { $0.title.localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        NavigationSplitView {
            List(selection: $selection) {
                Section("Your Library") {
                    ForEach(filteredCourses) { course in
                        NavigationLink(value: course.id) {
                            Label(course.title, systemImage: "book.closed")
                        }
                    }
                }
            }
            .searchable(text: $search, prompt: "Search courses")
            .navigationTitle("Scribe")
            .toolbar {
                Button(action: library.addCourse) { Image(systemName: "plus") }
                    .help("New course")
            }
        } detail: {
            if let course = library.visibleCourses.first(where: { $0.id == selection }) {
                CourseDetail(course: course)
            } else {
                EmptyState(title: "Your notes, organised.", symbol: "books.vertical",
                           message: "Create a course to start building your notebook.")
            }
        }
        .background(.regularMaterial)
    }
}

struct CourseDetail: View {
    let course: ScribeNode
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(course.title).font(.largeTitle.weight(.semibold))
            Text("Course workspace").foregroundStyle(.secondary)
            EmptyState(title: "No chapters yet", symbol: "text.book.closed",
                       message: "Chapter creation and full editor parity are not implemented yet.")
            Spacer()
        }
        .padding(32)
    }
}

struct EmptyState: View {
    let title: String
    let symbol: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 38)).foregroundStyle(.secondary)
            Text(title).font(.title3.weight(.semibold))
            Text(message).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }
}
