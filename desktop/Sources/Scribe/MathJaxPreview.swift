import SwiftUI
import WebKit

/// Renders stored LaTeX with MathJax when a network connection is available.
/// The original source remains in Scribe; if MathJax cannot load, the source is shown as a readable fallback.
struct MathJaxPreview: NSViewRepresentable {
    let source: String

    func makeNSView(context: Context) -> WKWebView {
        let view = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
        view.setValue(false, forKey: "drawsBackground")
        context.coordinator.lastSource = source
        let initial = Self.javascriptString(source)
        view.loadHTMLString(Self.html.replacingOccurrences(of: "__INITIAL_SOURCE__", with: initial),
                            baseURL: URL(string: "https://scribe.local/"))
        return view
    }

    func updateNSView(_ view: WKWebView, context: Context) {
        guard context.coordinator.lastSource != source else { return }
        context.coordinator.lastSource = source
        view.evaluateJavaScript("window.setEquation && window.setEquation(\(Self.javascriptString(source)));")
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    private static func javascriptString(_ value: String) -> String {
        let data = (try? JSONSerialization.data(withJSONObject: [value], options: [.fragmentsAllowed])) ?? Data("[\"\"]".utf8)
        let array = String(data: data, encoding: .utf8) ?? "[\"\"]"
        return String(array.dropFirst().dropLast()).replacingOccurrences(of: "</", with: "<\\/")
    }

    private static let html = #"""
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
:root{color-scheme:light dark}
html,body{margin:0;padding:0;background:transparent;color:CanvasText;font:16px -apple-system,BlinkMacSystemFont,sans-serif}
#equation{padding:12px;min-height:48px;overflow-wrap:anywhere}
#fallback{display:none;margin:0;padding:12px;white-space:pre-wrap;overflow-wrap:anywhere;font:14px ui-monospace,monospace;color:CanvasText}
</style>
<script>
window.MathJax={tex:{inlineMath:[['\\(','\\)']],displayMath:[['\\[','\\]']],packages:{'[+]':['ams']}},chtml:{scale:1.0},options:{skipHtmlTags:['script','noscript','style','textarea','pre','code']}};
</script>
<script defer id="mathjax-loader" src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
</head>
<body>
<div id="equation"></div>
<pre id="fallback"></pre>
<script>
let equationSource=__INITIAL_SOURCE__;
function renderEquation(){
  const target=document.getElementById('equation');
  const fallback=document.getElementById('fallback');
  if(!target||!fallback)return;
  target.textContent='\\['+equationSource+'\\]';
  fallback.textContent=equationSource;
  if(window.MathJax&&window.MathJax.typesetPromise){
    fallback.style.display='none';
    MathJax.typesetClear([target]);
    MathJax.typesetPromise([target]).catch(()=>{fallback.style.display='block';});
  }else{
    fallback.style.display='block';
  }
}
window.setEquation=function(value){equationSource=value||'';renderEquation();};
document.getElementById('mathjax-loader').addEventListener('load',renderEquation);
document.getElementById('mathjax-loader').addEventListener('error',function(){renderEquation();});
window.addEventListener('load',renderEquation);
</script>
</body>
</html>
"""#

    final class Coordinator {
        var lastSource = ""
    }
}
