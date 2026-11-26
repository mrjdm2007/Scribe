import SwiftUI
import WebKit

struct ScribeReadingMode: View {
    let title: String
    let bodyText: String
    let attachments: [ScribeItem]
    let allItems: [ScribeItem]

    @Environment(\.dismiss) private var dismiss
    @State private var paper = "Dark"
    @State private var fontSize = 19.0
    @State private var readingWidth = 760.0
    @State private var showingSettings = false
    @State private var isBookmarked = false
    @State private var progress = 0.0

    private var headings: [String] {
        bodyText.components(separatedBy: .newlines).compactMap { line in
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard trimmed.hasPrefix("#") else { return nil }
            return trimmed.drop(while: { $0 == "#" }).trimmingCharacters(in: .whitespaces)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                Button { dismiss() } label: { Image(systemName: "xmark").font(.system(size: 13, weight: .semibold)).frame(width: 34, height: 34) }
                    .buttonStyle(.plain).background(.white.opacity(0.07), in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text("READING MODE").font(.system(size: 9, weight: .bold, design: .rounded)).tracking(2).foregroundStyle(Color(red: 0.84, green: 0.72, blue: 0.55))
                    Text(title).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                }
                Spacer()
                Button { isBookmarked.toggle() } label: {
                    Image(systemName: isBookmarked ? "bookmark.fill" : "bookmark").font(.system(size: 15))
                        .foregroundStyle(isBookmarked ? Color(red: 0.84, green: 0.72, blue: 0.55) : .secondary)
                        .frame(width: 34, height: 34)
                }.buttonStyle(.plain).help("Bookmark this reading position")
                Button { withAnimation(.easeInOut(duration: 0.2)) { showingSettings.toggle() } } label: {
                    Image(systemName: "slider.horizontal.3").frame(width: 34, height: 34)
                }.buttonStyle(.plain).help("Reading appearance")
            }.padding(.horizontal, 20).padding(.vertical, 14)
            Rectangle().fill(.white.opacity(0.07)).frame(height: 1)
            GeometryReader { geo in
                Rectangle().fill(Color(red: 0.84, green: 0.72, blue: 0.55))
                    .frame(width: geo.size.width * progress, height: 2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }.frame(height: 2)
            if showingSettings {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("PAPER").font(.system(size: 9, weight: .bold)).tracking(1.5).foregroundStyle(.secondary)
                        Spacer()
                        Picker("Paper", selection: $paper) {
                            Text("Dark").tag("Dark"); Text("Light").tag("Light"); Text("Warm").tag("Warm")
                        }.pickerStyle(.segmented).frame(maxWidth: 300)
                    }
                    HStack {
                        Text("TEXT SIZE").font(.system(size: 9, weight: .bold)).tracking(1.5).foregroundStyle(.secondary)
                        Slider(value: $fontSize, in: 14...27, step: 1)
                        Text("\(Int(fontSize)) pt").font(.caption.monospacedDigit()).frame(width: 48)
                    }
                    HStack {
                        Text("READING WIDTH").font(.system(size: 9, weight: .bold)).tracking(1.5).foregroundStyle(.secondary)
                        Slider(value: $readingWidth, in: 540...1040, step: 20)
                        Text(readingWidth < 680 ? "Narrow" : readingWidth < 880 ? "Comfort" : "Wide").font(.caption).frame(width: 65)
                    }
                }.padding(.horizontal, 24).padding(.vertical, 14).background(.white.opacity(0.035))
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
            HStack {
                Text("\(Int(progress * 100))% read").font(.system(size: 10, weight: .medium, design: .rounded)).monospacedDigit().foregroundStyle(.secondary)
                Spacer()
                Text(isBookmarked ? "Bookmark set" : "Distraction-free reading").font(.system(size: 10)).foregroundStyle(.secondary)
            }.padding(.horizontal, 22).padding(.vertical, 11).background(.white.opacity(0.035))
        }
        .background(paperColor)
        .preferredColorScheme(paper == "Light" || paper == "Warm" ? .light : .dark)
        .frame(minWidth: 560, minHeight: 500)
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
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "readerProgress")
        let web = WKWebView(frame: .zero, configuration: config)
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
        context.coordinator.progress = $progress
        context.coordinator.webView = web
        if context.coordinator.lastHTML != html {
            context.coordinator.lastHTML = html
            web.loadHTMLString(html, baseURL: URL(string: "https://scribe.local/"))
        }
    }

    private var html: String {
        let encoded = (try? JSONSerialization.data(withJSONObject: [title, markdown])).flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\",\"\"]"
        let parts = encoded.dropFirst().dropLast()
        let background = paper == "Light" ? "#F7F7F4" : paper == "Warm" ? "#F1E6D0" : "#111316"
        let foreground = paper == "Light" ? "#252522" : paper == "Warm" ? "#342B20" : "#E9E5DD"
        return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <script src="https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js"></script>
        <script>window.MathJax={tex:{inlineMath:[['\\\\(','\\\\)'],['$','$']],displayMath:[['\\\\[','\\\\]'],['$$','$$']],processEscapes:true}};</script>
        <script defer src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
        <style>
        *{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:(background);color:(foreground);font:(fontSize)px/1.82 Georgia,serif}
        article{max-width:(width)px;margin:0 auto;padding:46px 30px 100px}header{border-bottom:1px solid color-mix(in srgb,(foreground) 18%,transparent);padding-bottom:22px;margin-bottom:30px}
        .kicker{font:10px -apple-system,sans-serif;letter-spacing:2px;opacity:.55;margin-bottom:12px}h1{font-size:2.1em;line-height:1.18;letter-spacing:-.03em;margin:0}h2{font-size:1.45em;margin:2em 0 .65em}h3{font-size:1.15em;margin:1.7em 0 .5em}
        p{margin:0 0 1.1em}li{margin:.35em 0}blockquote{border-left:3px solid #c5a778;padding-left:1em;opacity:.78;margin-left:0}
        pre{padding:16px;border-radius:12px;overflow:auto;background:color-mix(in srgb,(foreground) 8%,transparent)}code{font-family:ui-monospace,monospace;background:color-mix(in srgb,(foreground) 8%,transparent);padding:.12em .3em;border-radius:4px}
        table{border-collapse:collapse;width:100%;display:block;overflow:auto}td,th{border:1px solid color-mix(in srgb,(foreground) 24%,transparent);padding:8px 11px}a{color:#b78e53}img{max-width:100%;height:auto;border-radius:12px}mjx-container[display=true]{max-width:100%;overflow:auto;padding:10px 0}
        </style></head><body><article><header><div class="kicker">SCRIBE · READING MODE</div><h1 id="title"></h1></header><main id="content"></main></article>
        <script>
        const data=[(parts)];document.getElementById('title').textContent=data[0];const root=document.getElementById('content');
        if(window.markdownit)root.innerHTML=window.markdownit({html:false,linkify:true,typographer:true}).enable(['table','strikethrough']).render(data[1]);else{root.textContent=data[1];root.style.whiteSpace='pre-wrap'}
        window.jumpToHeading=i=>root.querySelectorAll('h1,h2,h3')[i]?.scrollIntoView({behavior:'smooth',block:'start'});
        function progress(){let max=document.documentElement.scrollHeight-innerHeight;window.webkit.messageHandlers.readerProgress.postMessage(max<=0?1:scrollY/max)}addEventListener('scroll',progress,{passive:true});
        addEventListener('load',async()=>{try{await MathJax.startup.promise;await MathJax.typesetPromise([root])}catch(e){}progress()});
        </script></body></html>
        """
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        @Binding var progress: Double
        weak var webView: WKWebView?
        var lastHTML = ""
        var jumpObserver: NSObjectProtocol?
        init(progress: Binding<Double>) { _progress = progress }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if let value = message.body as? Double { DispatchQueue.main.async { self.progress = min(1, max(0, value)) } }
        }
        deinit { if let jumpObserver { NotificationCenter.default.removeObserver(jumpObserver) } }
    }
}
