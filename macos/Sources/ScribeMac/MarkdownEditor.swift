import SwiftUI
import WebKit

struct MarkdownEditor: NSViewRepresentable {
    @Binding var text: String
    @Binding var selection: NSRange
    let searchQuery: String
    let matchIndex: Int

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> WKWebView {
        let controller = WKUserContentController()
        controller.add(context.coordinator, name: "scribeEditor")
        let configuration = WKWebViewConfiguration()
        configuration.userContentController = controller
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.setValue(false, forKey: "drawsBackground")
        context.coordinator.parent = self
        context.coordinator.lastMarkdown = text
        let initial = Self.javascriptString(text)
        view.loadHTMLString(Self.html.replacingOccurrences(of: "__INITIAL__", with: initial), baseURL: URL(string: "https://scribe.local/"))
        return view
    }

    func updateNSView(_ view: WKWebView, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.lastMarkdown != text {
            context.coordinator.lastMarkdown = text
            let value = Self.javascriptString(text)
            view.evaluateJavaScript("window.setMarkdown && window.setMarkdown(\(value));")
        }
        if !searchQuery.isEmpty {
            let query = Self.javascriptString(searchQuery)
            view.evaluateJavaScript("window.findText && window.findText(\(query), \(max(0, matchIndex)));")
        }
    }

    private static func javascriptString(_ value: String) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: [value], options: [.fragmentsAllowed])) ?? Data("[\"\"]".utf8)
        let array = String(data: data, encoding: .utf8) ?? "[\"\"]"
        return String(array.dropFirst().dropLast())
    }

    private static let html = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js"></script>
<style>
:root{color-scheme:light dark}*{box-sizing:border-box}body{margin:0;padding:16px;background:transparent;color:CanvasText;font:15px/1.7 -apple-system,BlinkMacSystemFont,sans-serif}
#editor{min-height:320px;outline:none;overflow-wrap:anywhere}#editor:empty:before{content:"Start writing…";opacity:.5}
h1,h2,h3{line-height:1.25;color:CanvasText}blockquote{border-left:3px solid #c5a778;padding-left:12px;opacity:.8;margin-left:0}
pre,code{font-family:ui-monospace,monospace;background:color-mix(in srgb,CanvasText 10%,transparent);padding:3px;border-radius:4px}
pre{padding:12px;overflow:auto}pre code{background:transparent}table{border-collapse:collapse;width:100%}td,th{border:1px solid color-mix(in srgb,CanvasText 30%,transparent);padding:6px 9px}
a{color:#b18a52}#editor mark{background:#f2d36b;color:#242424}
</style></head><body><div id="editor" contenteditable="true" spellcheck="true"></div>
<script>
const editor=document.getElementById("editor");let lastMarkdown="";
function md(n){
 if(n.nodeType===3)return n.nodeValue||"";if(n.nodeType!==1)return "";
 const t=n.tagName.toLowerCase(),c=()=>Array.from(n.childNodes).map(md).join("");
 if(t==="br")return "\\n";if(t==="strong"||t==="b")return "**"+c()+"**";if(t==="em"||t==="i")return "*"+c()+"*";
 if(t==="u")return "<u>"+c()+"</u>";if(t==="s"||t==="del")return "~~"+c()+"~~";
 if(/^h[1-6]$/.test(t))return "#".repeat(+t[1])+" "+c().trim()+"\\n\\n";
 if(t==="blockquote")return c().trim().split("\\n").map(x=>"> "+x).join("\\n")+"\\n\\n";
 if(t==="pre")return "    "+(n.innerText||n.textContent||"").replace(/\\n/g,"\\n    ")+"\\n\\n";
 if(t==="a")return "["+c()+"]("+n.getAttribute("href")+")";
 if(t==="li")return (n.parentElement&&n.parentElement.tagName==="OL"?(Array.from(n.parentElement.children).indexOf(n)+1)+". ":"- ")+c().trim()+"\\n";
 if(t==="ul"||t==="ol")return Array.from(n.children).map(md).join("")+"\\n";
 if(t==="p"||t==="div")return c().trim()+"\\n\\n";if(t==="hr")return "\\n---\\n\\n";
 if(t==="td"||t==="th")return c().trim()+" | ";if(t==="tr")return "| "+Array.from(n.children).map(md).join("")+"\\n";
 if(t==="table")return c()+"\\n";return c();
}
function toMarkdown(){return Array.from(editor.childNodes).map(md).join("").replace(/\\n{3,}/g,"\\n\\n").trim();}
function publish(){const value=toMarkdown();if(value!==lastMarkdown){lastMarkdown=value;window.webkit.messageHandlers.scribeEditor.postMessage(value);}}
window.setMarkdown=function(value){if(value===lastMarkdown)return;lastMarkdown=value;const parser=window.markdownit?window.markdownit({html:false,linkify:false,typographer:false}).enable("table"):null;editor.innerHTML=parser?parser.render(value):"<pre>"+value.replace(/&/g,"&amp;").replace(/</g,"&lt;")+"</pre>";};
window.findText=function(query,index){if(!query)return;const walker=document.createTreeWalker(editor,NodeFilter.SHOW_TEXT);let node,found=[];while(node=walker.nextNode()){let at=0;while((at=node.textContent.indexOf(query,at))>=0){found.push({node:node,at:at});at+=query.length;}}if(found.length){const item=found[Math.min(index,found.length-1)];const range=document.createRange();range.setStart(item.node,item.at);range.setEnd(item.node,item.at+query.length);const selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);range.startContainer.parentElement.scrollIntoView({block:"center"});}};
editor.addEventListener("input",publish);editor.addEventListener("blur",publish);
window.addEventListener("DOMContentLoaded",()=>window.setMarkdown(__INITIAL__));
</script></body></html>
"""

    final class Coordinator: NSObject, WKScriptMessageHandler {
        var parent: MarkdownEditor
        var lastMarkdown = ""
        init(_ parent: MarkdownEditor) { self.parent = parent; self.lastMarkdown = parent.text }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let value = message.body as? String else { return }
            lastMarkdown = value
            parent.text = value
            parent.selection = NSRange(location: (value as NSString).length, length: 0)
        }
    }
}
