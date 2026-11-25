import SwiftUI
import ScribeCore

/// Persistent compact focus timer shared by every macOS screen.
struct ScribeFloatingStudyTimer: View {
    let onAskAI: () -> Void
    @AppStorage("scribe.focus.runningSince") private var runningSince = 0.0
    @AppStorage("scribe.focus.elapsedBeforeRun") private var elapsedBeforeRun = 0.0
    @AppStorage("scribe.focus.visible") private var visible = true
    @State private var expanded = false

    private var elapsed: TimeInterval {
        elapsedBeforeRun + (runningSince > 0 ? max(0, Date().timeIntervalSince1970 - runningSince) : 0)
    }
    private var clock: String {
        let seconds = Int(elapsed)
        return String(format: "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }

    var body: some View {
        if visible {
            VStack(alignment: .trailing, spacing: 8) {
                Button(action: onAskAI) { Label("Ask Scribe AI", systemImage: "sparkles").padding(.horizontal, 13).padding(.vertical, 9).background(.ultraThinMaterial, in: Capsule()).overlay(Capsule().stroke(.white.opacity(0.17))) }.buttonStyle(.plain)
                if expanded {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack { Text("STUDY TIME").font(.caption.bold()).tracking(1.2); Spacer(); Button { expanded = false } label: { Image(systemName: "xmark") }.buttonStyle(.plain) }
                        TimelineView(.periodic(from: .now, by: 1)) { _ in
                            Text(clock).font(.system(size: 25, weight: .light, design: .rounded)).monospacedDigit().foregroundStyle(Color(red: 0.84, green: 0.72, blue: 0.55))
                        }
                        HStack {
                            Button(runningSince > 0 ? "Pause" : "Resume") {
                                if runningSince > 0 {
                                    elapsedBeforeRun = elapsed
                                    runningSince = 0
                                } else { runningSince = Date().timeIntervalSince1970 }
                            }
                            .buttonStyle(.borderedProminent).tint(Color(red: 0.84, green: 0.72, blue: 0.55))
                            Button("Reset") { runningSince = 0; elapsedBeforeRun = 0 }.buttonStyle(.bordered)
                        }
                    }
                    .padding(14).frame(width: 220).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(.white.opacity(0.16)))
                }
                Button { expanded.toggle() } label: {
                    HStack(spacing: 7) {
                        Image(systemName: "timer")
                        TimelineView(.periodic(from: .now, by: 1)) { _ in Text(clock).monospacedDigit().font(.system(.caption, design: .rounded)) }
                    }.padding(.horizontal, 13).padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: Capsule()).overlay(Capsule().stroke(.white.opacity(0.17)))
                }.buttonStyle(.plain).help("Study timer")
            }.padding(18)
        }
    }
}

/// Read-only, distraction-free rendering. It never writes to the underlying note.
struct ScribeReadingMode: View {
    let title: String
    let bodyText: String
    let attachments: [ScribeItem]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text(title).font(.system(size: 34, weight: .semibold, design: .serif))
                    Text(bodyText).font(.system(size: 18, weight: .regular, design: .serif)).lineSpacing(9).textSelection(.enabled)
                    ForEach(attachments) { item in
                        VStack(alignment: .leading, spacing: 9) {
                            Text(item.title).font(.title3.bold())
                            if !item.body.isEmpty { Text(item.body).font(.system(size: 17, design: .serif)).lineSpacing(7).textSelection(.enabled) }
                        }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
                            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                }.frame(maxWidth: 760, alignment: .leading).padding(.horizontal, 34).padding(.vertical, 42)
            }
            .background(Color(nsColor: .windowBackgroundColor))
            .toolbar { ToolbarItem(placement: .primaryAction) { Button("Done") { dismiss() } } }
        }.frame(minWidth: 560, minHeight: 520)
    }
}
