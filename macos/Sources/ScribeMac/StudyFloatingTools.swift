import SwiftUI
import AppKit
import WebKit
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


 // Compact timer remains visible in the distraction-free reader while the
 // global floating panel is covered by the reading sheet.
private struct ScribeReadingTimerStatus: View {
    @AppStorage("scribe.focus.runningSince") private var runningSince = 0.0
    @AppStorage("scribe.focus.elapsedBeforeRun") private var elapsedBeforeRun = 0.0
    private var elapsed: TimeInterval {
        elapsedBeforeRun + (runningSince > 0 ? max(0, Date().timeIntervalSince1970 - runningSince) : 0)
    }
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "timer").foregroundStyle(Color(red: 0.84, green: 0.72, blue: 0.55))
            TimelineView(.periodic(from: .now, by: 1)) { _ in
                let seconds = Int(elapsed)
                Text(String(format: "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60))
                    .font(.system(.caption, design: .monospaced)).monospacedDigit()
            }
            Button(runningSince > 0 ? "Pause" : "Resume") {
                if runningSince > 0 { elapsedBeforeRun = elapsed; runningSince = 0 }
                else { runningSince = Date().timeIntervalSince1970 }
            }.buttonStyle(.borderless)
        }
    }
}

/// Read-only, distraction-free rendering. It never writes to the underlying note.

struct ScribeReadingTimerChip: View {
    @AppStorage("scribe.focus.runningSince") private var runningSince = 0.0
    @AppStorage("scribe.focus.elapsedBeforeRun") private var elapsedBeforeRun = 0.0
    private var elapsed: TimeInterval {
        elapsedBeforeRun + (runningSince > 0 ? max(0, Date().timeIntervalSince1970 - runningSince) : 0)
    }
    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { _ in
            HStack(spacing: 7) {
                Image(systemName: "timer")
                Text(String(format: "%02d:%02d:%02d", Int(elapsed) / 3600, (Int(elapsed) / 60) % 60, Int(elapsed) % 60))
                    .monospacedDigit()
                Text("Study time").foregroundStyle(.secondary)
            }.font(.system(.caption, design: .rounded))
                .padding(.horizontal, 13).padding(.vertical, 9)
                .background(.ultraThinMaterial, in: Capsule())
                .overlay(Capsule().stroke(.white.opacity(0.16)))
        }
    }
}


struct ScribeReadingMode: View {
    let title: String
    let bodyText: String
    let attachments: [ScribeItem]
    let allItems: [ScribeItem]
    @Environment(\.dismiss) private var dismiss
    @State private var showingAI = false
    @State private var showingSettings = false
    @State private var paper = "Dark"
    @State private var fontSize = 19.0
    @State private var readingWidth = 760.0
    @State private var progress = 0.0

    private var headings: [String] {
        bodyText.components(separatedBy: .newlines).compactMap { line in
            let value = line.trimmingCharacters(in: .whitespaces)
            guard value.hasPrefix("#") else { return nil }
            return value.drop(while: { $0 == "#" }).trimmingCharacters(in: .whitespaces)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 13) {
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 12, weight: .semibold))
                        .frame(width: 34, height: 34).background(.white.opacity(0.07), in: Circle())
                }.buttonStyle(.plain).help("Close reading mode")
                VStack(alignment: .leading, spacing: 3) {
                    Text("READING MODE").font(.system(size: 9, weight: .bold, design: .rounded))
                        .tracking(2).foregroundStyle(Color(red: 0.84, green: 0.72, blue: 0.55))
                    Text(title).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                }
                Spacer()
                ScribeReadingTimerStatus()
                Button { showingSettings.toggle() } label: {
                    Image(systemName: "slider.horizontal.3").frame(width: 34, height: 34)
                }.buttonStyle(.plain).help("Reading appearance")
                Button { showingAI = true } label: {
                    Label("Ask AI", systemImage: "sparkles").font(.system(size: 12, weight: .medium))
                }.buttonStyle(.bordered).tint(Color(red: 0.84, green: 0.72, blue: 0.55))
                Button("Done") { dismiss() }.keyboardShortcut(.escape)
            }.padding(.horizontal, 20).padding(.vertical, 13)
            Rectangle().fill(.white.opacity(0.08)).frame(height: 1)
            GeometryReader { geometry in
                Rectangle().fill(Color(red: 0.84, green: 0.72, blue: 0.55))
                    .frame(width: geometry.size.width * progress, height: 2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }.frame(height: 2)
            if showingSettings {
                VStack(spacing: 10) {
                    HStack {
                        Text("PAPER").font(.system(size: 9, weight: .bold)).tracking(1.4).foregroundStyle(.secondary)
                        Spacer()
                        Picker("Paper", selection: $paper) {
                            Text("Dark").tag("Dark"); Text("Light").tag("Light"); Text("Warm").tag("Warm")
                        }.pickerStyle(.segmented).frame(maxWidth: 300)
                    }
                    HStack {
                        Text("TEXT SIZE").font(.system(size: 9, weight: .bold)).tracking(1.4).foregroundStyle(.secondary)
                        Slider(value: $fontSize, in: 14...27, step: 1)
                        Text("\(Int(fontSize)) pt").font(.caption.monospacedDigit()).frame(width: 48)
                    }
                    HStack {
                        Text("READING WIDTH").font(.system(size: 9, weight: .bold)).tracking(1.4).foregroundStyle(.secondary)
                        Slider(value: $readingWidth, in: 540...1040, step: 20)
                        Text(readingWidth < 680 ? "Narrow" : readingWidth < 880 ? "Comfort" : "Wide").font(.caption).frame(width: 65)
                    }
                }.padding(.horizontal, 24).padding(.vertical, 13).background(.white.opacity(0.035))
            }
            if !headings.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 7) {
                        ForEach(Array(headings.enumerated()), id: \.offset) { index, heading in
                            Button { NotificationCenter.default.post(name: .scribeReaderJump, object: index) } label: {
                                Text(heading).font(.system(size: 11, weight: .medium)).lineLimit(1)
                                    .padding(.horizontal, 11).padding(.vertical, 7)
                                    .background(.white.opacity(0.055), in: Capsule())
                            }.buttonStyle(.plain)
                        }
                    }.padding(.horizontal, 20).padding(.vertical, 9)
                }
            }
            ReaderWebView(markdown: bodyText, title: title, paper: paper, fontSize: fontSize, width: readingWidth, progress: $progress)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            if !attachments.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(attachments) { item in
                            HStack(spacing: 9) {
                                Image(systemName: item.tags.contains("type:image") ? "photo" : "doc.text")
                                    .foregroundStyle(Color(red: 0.84, green: 0.72, blue: 0.55))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title).font(.caption.weight(.semibold)).lineLimit(1)
                                    Text(item.tags.contains("type:image") ? "Image attachment" : "Note attachment")
                                        .font(.system(size: 10)).foregroundStyle(.secondary)
                                }
                            }.padding(10).background(.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 10))
                        }
                    }.padding(.horizontal, 18).padding(.vertical, 8)
                }
            }
            HStack {
                Text("\(Int(progress * 100))% read").font(.system(size: 10, weight: .medium, design: .rounded)).monospacedDigit().foregroundStyle(.secondary)
                Spacer()
                Text("Original note is unchanged").font(.system(size: 10)).foregroundStyle(.secondary)
            }.padding(.horizontal, 22).padding(.vertical, 10).background(.white.opacity(0.035))
        }
        .background(paperColor)
        .preferredColorScheme(paper == "Dark" ? .dark : .light)
        .frame(minWidth: 600, minHeight: 560)
        .sheet(isPresented: $showingAI) { ScribeIntelligenceView(items: allItems) }
    }

    private var paperColor: Color {
        switch paper {
        case "Light": return Color(red: 0.97, green: 0.97, blue: 0.95)
        case "Warm": return Color(red: 0.94, green: 0.90, blue: 0.82)
        default: return Color(red: 0.065, green: 0.07, blue: 0.08)
        }
    }
}

private extension Notification.Name {
    static let scribeReaderJump = Notification.Name("scribeReaderJump")
}

private struct ReaderWebView: NSViewRepresentable {
    let markdown: String
    let title: String
    let paper: String
    let fontSize: Double
    let width: Double
    @Binding var progress: Double

    func makeCoordinator() -> Coordinator { Coordinator(progress: $progress) }

    func makeNSView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.userContentController.add(context.coordinator, name: "readerProgress")
        let web = WKWebView(frame: .zero, configuration: configuration)
        web.setValue(false, forKey: "drawsBackground")
        web.loadHTMLString(html, baseURL: URL(string: "https://scribe.local/"))
        context.coordinator.webView = web
        context.coordinator.jumpObserver = NotificationCenter.default.addObserver(forName: .scribeReaderJump, object: nil, queue: .main) { note in
            guard let index = note.object as? Int else { return }
            web.evaluateJavaScript("window.jumpToHeading(\(index))")
        }
        return web
    }

    func updateNSView(_ web: WKWebView, context: Context) {
        context.coordinator.updateProgressBinding($progress)
        context.coordinator.webView = web
        if context.coordinator.lastHTML != html {
            context.coordinator.lastHTML = html
            web.loadHTMLString(html, baseURL: URL(string: "https://scribe.local/"))
        }
    }

    private var html: String {
        let json = (try? JSONSerialization.data(withJSONObject: [title, markdown])).flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\",\"\"]"
        let safeJSON = json.replacingOccurrences(of: "</", with: "<\\/")
        let data = String(safeJSON.dropFirst().dropLast())
        let background = paper == "Light" ? "#F7F7F4" : paper == "Warm" ? "#F1E6D0" : "#111316"
        let foreground = paper == "Light" ? "#252522" : paper == "Warm" ? "#342B20" : "#E9E5DD"
        return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <script src="https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js"></script>
        <script>window.MathJax={tex:{inlineMath:[['\\\\(','\\\\)'],['$','$']],displayMath:[['\\\\[','\\\\]'],['$$','$$']],processEscapes:true}};</script>
        <script defer src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
        <style>
        *{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:\(background);color:\(foreground);font:\(fontSize)px/1.82 Georgia,serif}
        article{max-width:\(width)px;margin:0 auto;padding:46px 30px 100px}header{border-bottom:1px solid color-mix(in srgb,\(foreground) 18%,transparent);padding-bottom:22px;margin-bottom:30px}
        .kicker{font:10px -apple-system,sans-serif;letter-spacing:2px;opacity:.55;margin-bottom:12px}h1{font-size:2.1em;line-height:1.18;letter-spacing:-.03em;margin:0}h2{font-size:1.45em;margin:2em 0 .65em}h3{font-size:1.15em;margin:1.7em 0 .5em}
        p{margin:0 0 1.1em}li{margin:.35em 0}blockquote{border-left:3px solid #c5a778;padding-left:1em;opacity:.78;margin-left:0}
        pre{padding:16px;border-radius:12px;overflow:auto;background:color-mix(in srgb,\(foreground) 8%,transparent)}code{font-family:ui-monospace,monospace;background:color-mix(in srgb,\(foreground) 8%,transparent);padding:.12em .3em;border-radius:4px}
        table{border-collapse:collapse;width:100%;display:block;overflow:auto}td,th{border:1px solid color-mix(in srgb,\(foreground) 24%,transparent);padding:8px 11px}a{color:#b78e53}img{max-width:100%;height:auto;border-radius:12px}mjx-container[display=true]{max-width:100%;overflow:auto;padding:10px 0}
        </style></head><body><article><header><div class="kicker">SCRIBE · READING MODE</div><h1 id="title"></h1></header><main id="content"></main></article>
        <script>
        const data=[\(data)];document.getElementById('title').textContent=data[0];const root=document.getElementById('content');
        if(window.markdownit)root.innerHTML=window.markdownit({html:false,linkify:true,typographer:true}).enable(['table','strikethrough']).render(data[1]);else{root.textContent=data[1];root.style.whiteSpace='pre-wrap'}
        window.jumpToHeading=i=>root.querySelectorAll('h1,h2,h3')[i]?.scrollIntoView({behavior:'smooth',block:'start'});
        function updateProgress(){let max=document.documentElement.scrollHeight-innerHeight;window.webkit.messageHandlers.readerProgress.postMessage(max<=0?1:scrollY/max)}addEventListener('scroll',updateProgress,{passive:true});
        addEventListener('load',async()=>{try{await MathJax.startup.promise;await MathJax.typesetPromise([root])}catch(e){}updateProgress()});
        </script></body></html>
        """
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        @Binding var progress: Double
        weak var webView: WKWebView?
        var lastHTML = ""
        var jumpObserver: NSObjectProtocol?
        init(progress: Binding<Double>) { _progress = progress }
        func updateProgressBinding(_ binding: Binding<Double>) { _progress = binding }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if let value = message.body as? Double { DispatchQueue.main.async { self.progress = min(1, max(0, value)) } }
        }
        deinit { if let jumpObserver { NotificationCenter.default.removeObserver(jumpObserver) } }
    }
}
