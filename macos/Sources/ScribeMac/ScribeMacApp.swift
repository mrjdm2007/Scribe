import SwiftUI
import ScribeCore

@main
struct ScribeMacApp: App {
    @StateObject private var store = ScribeStore()
    var body: some Scene {
        WindowGroup {
            ScribeMacRoot()
                .environmentObject(store)
                .frame(minWidth: 980, minHeight: 650)
        }
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("New Topic") {
                    if let parent = store.selected, parent.kind == "chapter" {
                        store.create(kind: "topic", title: "Untitled topic", parentId: parent.id)
                    }
                }
                    .keyboardShortcut("n", modifiers: .command)
                    .disabled(store.selected?.kind != "chapter")
                Button("New Course") { store.create(kind: "course", title: "Untitled course") }
                    .keyboardShortcut("n", modifiers: [.command, .shift])
            }
            CommandGroup(after: .textEditing) {
                Button("Focus Mode") { store.activeTool = .focus }
                    .keyboardShortcut("f", modifiers: [.command, .option])
                Button("Search Library") { store.focusSearch.toggle() }
                    .keyboardShortcut("f", modifiers: .command)
            }
        }
    }
}
