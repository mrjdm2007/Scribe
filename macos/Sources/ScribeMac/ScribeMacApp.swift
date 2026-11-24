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
                Button("New Page") { store.create(kind: "page", title: "Untitled page", parentId: store.selectedID) }
                    .keyboardShortcut("n", modifiers: .command)
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
