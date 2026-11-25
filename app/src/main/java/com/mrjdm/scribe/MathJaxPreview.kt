package com.mrjdm.scribe

import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Renders a LaTeX source string with MathJax. Source is JSON-escaped before crossing
 * the JavaScript boundary; the original text remains the saved notebook content.
 * If the CDN is unavailable, a readable source fallback is shown.
 */
@Composable
fun MathJaxPreview(source: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://scribe.local/", html(source), "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.evaluateJavascript("window.setEquation && window.setEquation(${JSONObject.quote(source)});", null)
        }
    )
}

private fun html(initial: String): String {
    val safeInitial = JSONObject.quote(initial)
    return """
<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
html,body{margin:0;padding:0;background:transparent;color:#e9e6df;font:16px sans-serif}
#equation{padding:12px;min-height:44px;overflow-wrap:anywhere}
#fallback{display:none;padding:12px;white-space:pre-wrap;overflow-wrap:anywhere;font:14px monospace;color:#d5b98c}
</style>
<script>
window.MathJax={tex:{inlineMath:[['\\\\(','\\\\)']],displayMath:[['\\\\[','\\\\]']],packages:{'[+]':['ams']}},chtml:{scale:1.0}};
</script>
<script defer id="mathjax-loader" src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
</head><body>
<div id="equation"></div><pre id="fallback"></pre>
<script>
let equationSource=$safeInitial;
function renderEquation(){
 const target=document.getElementById('equation'),fallback=document.getElementById('fallback');
 target.textContent='\\\\['+equationSource+'\\\\]';fallback.textContent=equationSource;
 if(window.MathJax&&window.MathJax.typesetPromise){
  fallback.style.display='none';MathJax.typesetClear([target]);
  MathJax.typesetPromise([target]).catch(()=>fallback.style.display='block');
 }else{fallback.style.display='block';}
}
window.setEquation=function(value){equationSource=value||'';renderEquation();};
document.getElementById('mathjax-loader').addEventListener('load',renderEquation);
document.getElementById('mathjax-loader').addEventListener('error',renderEquation);
window.addEventListener('load',renderEquation);
</script></body></html>
""".trimIndent()
}
