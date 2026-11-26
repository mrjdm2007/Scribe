package com.mrjdm.scribe

import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

private enum class ReaderPaper(val label: String, val background: String, val foreground: String, val muted: String) {
    DARK("Dark", "#111316", "#E9E5DD", "#A5A098"),
    LIGHT("Light", "#F7F7F4", "#252522", "#77756F"),
    WARM("Warm", "#F1E6D0", "#342B20", "#82735E")
}

/** A read-only presentation surface. The source note is never edited by this component. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesReadingMode(
    title: String,
    markdown: String,
    onDismiss: () -> Unit
) {
    var paper by remember { mutableStateOf(ReaderPaper.DARK) }
    var fontSize by remember { mutableStateOf(18f) }
    var readingWidth by remember { mutableStateOf(760f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var bookmarked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val headings = remember(markdown) {
        markdown.lines().mapNotNull { line ->
            val match = Regex("^#{1,3}\\s+(.+)$").find(line.trim())
            match?.groupValues?.get(1)?.trim()
        }
    }
    val context = LocalContext.current
    var readerWebView by remember { mutableStateOf<WebView?>(null) }
    val safeTitle = JSONObject.quote(title).replace("<", "\\u003C")
    val safeMarkdown = JSONObject.quote(markdown).replace("<", "\\u003C")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(Color(0xFF101114))
                .border(1.dp, Color.White.copy(alpha = 0.10f))
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF181A1E)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close reader", tint = Color(0xFFE8E2D7)) }
                Column(Modifier.weight(1f)) {
                    Text("READING MODE", color = Color(0xFFD5B98C), fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                    Text(title, color = Color(0xFFF0ECE5), fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                IconButton(onClick = { bookmarked = !bookmarked }) {
                    Icon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        "Toggle reading bookmark", tint = if (bookmarked) Color(0xFFD5B98C) else Color(0xFFAAA49A))
                }
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(Icons.Rounded.Tune, "Reading settings", tint = Color(0xFFE8E2D7))
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                Modifier.fillMaxWidth().height(2.dp),
                color = Color(0xFFD5B98C),
                trackColor = Color(0xFF292B2F)
            )
            if (showSettings) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF191B1F)).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("APPEARANCE", color = Color(0xFFAAA49A), fontSize = 10.sp, letterSpacing = 1.2.sp)
                        Spacer(Modifier.weight(1f))
                        ReaderPaper.values().forEach { option ->
                            TextButton(onClick = { paper = option }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)) {
                                Text(option.label, color = if (paper == option) Color(0xFFD5B98C) else Color(0xFFAAA49A), fontSize = 12.sp)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TEXT SIZE", color = Color(0xFFAAA49A), fontSize = 10.sp, letterSpacing = 1.2.sp)
                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 14f..26f, modifier = Modifier.weight(1f))
                        Text("${fontSize.toInt()} sp", color = Color(0xFFE8E2D7), fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("READING WIDTH", color = Color(0xFFAAA49A), fontSize = 10.sp, letterSpacing = 1.2.sp)
                        Slider(value = readingWidth, onValueChange = { readingWidth = it }, valueRange = 560f..1040f, modifier = Modifier.weight(1f))
                        Text(if (readingWidth < 700) "Narrow" else if (readingWidth < 900) "Comfort" else "Wide", color = Color(0xFFE8E2D7), fontSize = 12.sp)
                    }
                }
            }
            if (headings.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF141619)).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headings.take(8).forEachIndexed { index, heading ->
                        TextButton(onClick = {
                            readerWebView?.evaluateJavascript("window.jumpToHeading($index)", null)
                        }) { Text(heading, color = Color(0xFFBEB7AB), fontSize = 11.sp, maxLines = 1) }
                    }
                }
            }
            key(paper, fontSize, readingWidth, markdown) {
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = {
                        WebView(context).apply {
                            readerWebView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(object {
                                @JavascriptInterface fun onProgress(value: Float) {
                                    post { progress = value.coerceIn(0f, 1f) }
                                }
                            }, "ScribeReader")
                            loadDataWithBaseURL("https://scribe.local/", readerHtml(safeTitle, safeMarkdown, paper, fontSize, readingWidth), "text/html", "UTF-8", null)
                        }
                    },
                    update = { view -> readerWebView = view }
                )
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF181A1E)).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${(progress * 100).toInt()}% read", color = Color(0xFFAAA49A), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(if (bookmarked) "Bookmark saved for this session" else "Distraction-free reading", color = Color(0xFFAAA49A), fontSize = 11.sp)
            }
        }
    }
}


private fun readerHtml(title: String, markdown: String, paper: ReaderPaper, fontSize: Float, width: Float): String {
    val safeBackground = paper.background
    val safeForeground = paper.foreground
    val safeMuted = paper.muted
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js"></script>
<script>window.MathJax={tex:{inlineMath:[['\\(','\\)'],['$','$']],displayMath:[['\\[','\\]'],['$$','$$']],processEscapes:true,packages:{'[+]':['ams']}},options:{skipHtmlTags:['script','noscript','style','textarea','pre','code']}};</script>
<script defer src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
<style>
*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:$safeBackground;color:$safeForeground;font:${fontSize}px/1.78 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;font-weight:400;letter-spacing:-.012em;-webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility}
article{max-width:${width}px;margin:0 auto;padding:46px 30px 100px}header{margin-bottom:34px;border-bottom:1px solid color-mix(in srgb,$safeMuted 28%,transparent);padding-bottom:24px}
.kicker{font:10px/1.4 -apple-system,sans-serif;letter-spacing:2px;color:$safeMuted;text-transform:uppercase;margin-bottom:12px}
h1{font:650 2.05em/1.2 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;letter-spacing:-.04em;margin:0 0 14px}h2{font:650 1.42em/1.32 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:2em 0 .65em;letter-spacing:-.028em}h3{font:600 1.13em/1.38 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:1.7em 0 .5em;letter-spacing:-.018em}
p{margin:0 0 1.15em;line-height:1.78}ul,ol{padding-left:1.5em;margin:.5em 0 1.25em}li{padding-left:.25em;margin:.35em 0}blockquote{border-left:3px solid #c5a778;margin:1.4em 0;padding:.2em 0 .2em 1.1em;color:$safeMuted}
pre{padding:16px;border-radius:12px;overflow:auto;background:color-mix(in srgb,$safeForeground 8%,transparent);font: .78em/1.6 ui-monospace,monospace}code{font-family:ui-monospace,monospace;background:color-mix(in srgb,$safeForeground 9%,transparent);padding:.12em .35em;border-radius:4px}pre code{background:transparent;padding:0}
table{border-collapse:collapse;width:100%;display:block;overflow-x:auto;margin:1.2em 0}th,td{border:1px solid color-mix(in srgb,$safeMuted 38%,transparent);padding:9px 12px;text-align:left}th{font-weight:700}
a{color:#b78e53;text-decoration-thickness:1px;text-underline-offset:3px}img{max-width:100%;height:auto;border-radius:12px}hr{border:0;border-top:1px solid color-mix(in srgb,$safeMuted 35%,transparent);margin:2em 0}
mjx-container[display="true"]{max-width:100%;overflow-x:auto;overflow-y:hidden;padding:12px 0}
@media(max-width:600px){article{padding:28px 20px 72px}h1{font-size:1.8em}}
</style></head><body><article><header><div class="kicker">SCRIBE · READING MODE</div><h1 id="note-title"></h1></header><main id="content"></main></article>
<script>
const title=$title,source=$markdown;
document.getElementById('note-title').textContent=title;
const root=document.getElementById('content');
if(window.markdownit){root.innerHTML=window.markdownit({html:false,linkify:true,typographer:true}).enable(['table','strikethrough']).render(source);}
else{root.textContent=source;root.style.whiteSpace='pre-wrap';}
window.jumpToHeading=function(i){const nodes=root.querySelectorAll('h1,h2,h3');nodes[i]?.scrollIntoView({behavior:'smooth',block:'start'});};
function updateProgress(){const max=document.documentElement.scrollHeight-innerHeight;const value=max<=0?1:scrollY/max;window.ScribeReader?.onProgress(value);}
addEventListener('scroll',updateProgress,{passive:true});addEventListener('resize',updateProgress);
addEventListener('load',async()=>{try{if(window.MathJax?.startup){await MathJax.startup.promise;await MathJax.typesetPromise([root]);}}catch(e){}updateProgress();});
</script></body></html>
    """.trimIndent()
}
