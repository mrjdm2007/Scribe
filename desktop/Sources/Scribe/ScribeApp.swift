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

    var body: some View {
        NavigationSplitView {
            List(selection: $selection) {
                Section("Your Library") {
                    ForEach(library.visibleCourses) { course in
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
                ContentUnavailableView("Your notes, organised.", systemImage: "books.vertical",
                    description: Text("Create a course to start building your notebook."))
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
            ContentUnavailableView("No chapters yet", systemImage: "text.book.closed",
                description: Text("Chapter creation and full editor parity are part of the next implementation stage."))
            Spacer()
        }
        .padding(32)
    }
}
