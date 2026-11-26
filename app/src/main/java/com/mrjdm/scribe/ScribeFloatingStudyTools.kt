package com.mrjdm.scribe

import android.content.Context
import com.mrjdm.scribe.data.ScribeViewModel
import com.mrjdm.scribe.data.ScribeItem
import com.mrjdm.scribe.data.ScribeCloudSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Always-available elapsed study timer and read-only note viewer.
 * Timer state is stored locally and survives navigation and process recreation.
 */
@Composable
fun ScribeFloatingStudyTools(item: ScribeItem?, vm: ScribeViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("scribe_global_study_timer", Context.MODE_PRIVATE) }
    var elapsedBase by remember { mutableLongStateOf(prefs.getLong("elapsed_ms", 0L)) }
    var startedAt by remember { mutableLongStateOf(prefs.getLong("started_at", 0L)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var readingText by remember { mutableStateOf("") }
    var aiOpen by remember { mutableStateOf(false) }
    var aiQuestion by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf("") }
    var aiMode by remember { mutableStateOf("notes") }
    val scope = rememberCoroutineScope()
    val aiConversation = remember { mutableStateListOf<String>() }
    LaunchedEffect(startedAt) {
        while (startedAt > 0L) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = elapsedBase + if (startedAt > 0L) (now - startedAt).coerceAtLeast(0L) else 0L
    val totalSeconds = elapsed / 1000L
    val clock = String.format(Locale.getDefault(), "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60)

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expanded) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xF0222328), tonalElevation = 8.dp) {
                Column(Modifier.width(210.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STUDY TIME", color = Color(0xFFD5B98C), fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                    Text(clock, fontFamily = FontFamily.Monospace, fontSize = 23.sp, color = Color(0xFFD5B98C))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = {
                            if (startedAt > 0L) {
                                elapsedBase = elapsed
                                startedAt = 0L
                            } else startedAt = System.currentTimeMillis()
                            prefs.edit().putLong("elapsed_ms", if (startedAt == 0L) elapsedBase else elapsedBase)
                                .putLong("started_at", startedAt).apply()
                        }) {
                            Icon(if (startedAt > 0L) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                            Text(if (startedAt > 0L) "Pause" else "Start")
                        }
                        OutlinedButton(onClick = {
                            elapsedBase = 0L; startedAt = 0L
                            prefs.edit().clear().apply()
                        }) { Icon(Icons.Rounded.Refresh, null) }
                    }
                }
            }
        }
        FilledTonalButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(clock, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        FilledTonalButton(onClick = { aiOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("AI tutor", fontSize = 11.sp)
        }
        if (item != null && item.kind in listOf("topic", "page", "block")) {
            FilledTonalButton(onClick = {
                reading = true
                scope.launch {
                    val all = vm.all()
                    val output = StringBuilder(item.body)
                    fun appendDescendants(parentId: Long, depth: Int) {
                        all.filter { it.parentId == parentId && it.deletedAt == null }.sortedBy { it.position }.forEach { child ->
                            output.append("\n\n").append("#".repeat((depth + 1).coerceAtMost(4))).append(" ").append(child.title).append("\n\n")
                            if (child.body.isNotBlank()) output.append(child.body)
                            appendDescendants(child.id, depth + 1)
                        }
                    }
                    appendDescendants(item.id, 0)
                    readingText = output.toString()
                }
            }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.Book, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Read")
            }
        }
    }

    if (aiOpen) {
        Dialog(onDismissRequest = { if (!aiBusy) aiOpen = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f)) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SCRIBE INTELLIGENCE", color = Color(0xFFD5B98C), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Text("Your study assistant", style = MaterialTheme.typography.titleLarge)
                        }
                        TextButton(onClick = { if (!aiBusy) { aiConversation.clear(); aiAnswer = ""; aiQuestion = "" } }) { Text("New chat") }
                        TextButton(onClick = { if (!aiBusy) aiOpen = false }) { Text("Close") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("notes", "explain", "exam", "quiz", "research", "summary", "flashcards", "mindmap", "socratic", "teach", "revise", "memorise", "practice", "test", "followup", "compare", "glossary", "formula_sheet", "study_plan", "lesson", "misconception", "mnemonics", "case_study").forEach { mode ->
                            FilterChip(selected = aiMode == mode, onClick = { aiMode = mode }, label = { Text(mode.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) })
                        }
                    }
                    OutlinedTextField(aiQuestion, { aiQuestion = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ask, compare concepts, make a formula sheet or plan revision") }, minLines = 2, maxLines = 4)
                    Button(enabled = aiQuestion.isNotBlank() && !aiBusy, onClick = {
                        aiBusy = true; aiError = ""; aiAnswer = ""
                        val studentMessage = aiQuestion
                        val priorTurns = aiConversation.takeLast(8).joinToString("\n\n")
                        val conversationPrompt = (if (priorTurns.isBlank()) "" else "$priorTurns\n\n") + "STUDENT: $studentMessage"
                        scope.launch {
                            val all = vm.all()
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val terms = aiQuestion.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
                                    val relevant = all.filter { it.deletedAt == null && it.body.isNotBlank() }
                                        .map { note -> note to terms.count { term -> note.title.contains(term, true) || note.body.contains(term, true) } }
                                        .filter { it.second > 0 }.sortedByDescending { it.second }.take(24)
                                    fun notebookPath(note: ScribeItem): String {
                                        val chain = mutableListOf(note.title)
                                        val seen = mutableSetOf<Long>()
                                        var parentId = note.parentId
                                        while (parentId != null && seen.add(parentId)) {
                                            val parent = all.firstOrNull { it.id == parentId } ?: break
                                            chain.add(parent.title)
                                            parentId = parent.parentId
                                        }
                                        return chain.asReversed().joinToString(" / ")
                                    }
                                    val contextArray = JSONArray()
                                    relevant.forEach { (note, _) ->
                                        contextArray.put(JSONObject().put("title", note.title).put("path", notebookPath(note)).put("content", note.body.take(12000)))
                                    }
                                    val session = ScribeCloudSession.read(context)
                                        ?: error("Sign in to Scribe Cloud and sync once before using Scribe Intelligence.")
                                    val base = session.url.trimEnd('/')
                                    val anon = session.anonKey
                                    val refresh = (URL("$base/auth/v1/token?grant_type=refresh_token").openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 30000; doOutput = true
                                        setRequestProperty("Content-Type", "application/json"); setRequestProperty("apikey", anon)
                                    }
                                    refresh.outputStream.use {
                                        it.write(JSONObject().put("refresh_token", session.refreshToken).toString().toByteArray(Charsets.UTF_8))
                                    }
                                    val refreshCode = refresh.responseCode
                                    val refreshStream = if (refreshCode in 200..299) refresh.inputStream else refresh.errorStream
                                    val refreshed = JSONObject(refreshStream.bufferedReader().use { it.readText() })
                                    refresh.disconnect()
                                    if (refreshCode !in 200..299) error("Your Scribe Cloud session expired. Sign in again in Settings.")
                                    val accessToken = refreshed.getString("access_token")
                                    refreshed.optString("refresh_token").takeIf { it.isNotBlank() }?.let {
                                        ScribeCloudSession.save(context, base, anon, session.email, it)
                                    }
                                    val connection = (URL("$base/functions/v1/study-assistant").openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 90000; doOutput = true
                                        setRequestProperty("Content-Type", "application/json")
                                        setRequestProperty("apikey", anon); setRequestProperty("Authorization", "Bearer $accessToken")
                                    }
                                    val payload = JSONObject().put("question", conversationPrompt).put("mode", aiMode).put("note_context", contextArray)
                                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                                    val status = connection.responseCode
                                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                                    val response = stream.bufferedReader().use { it.readText() }
                                    connection.disconnect()
                                    val json = JSONObject(response)
                                    if (status !in 200..299) error(json.optString("error", "AI request failed ($status)"))
                                    json.optString("answer").ifBlank { "The assistant returned an empty response." }
                                }
                            }
                            result.onSuccess {
                                aiAnswer = it
                                aiConversation.add("Student: $studentMessage")
                                aiConversation.add("Scribe: $it")
                                while (aiConversation.size > 16) { aiConversation.removeAt(0) }
                                aiQuestion = ""
                            }.onFailure { aiError = it.message ?: "Unable to reach Scribe Intelligence." }
                            aiBusy = false
                        }
                    }) { if (aiBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Ask Scribe") }
                    if (aiError.isNotBlank()) Text(aiError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (aiAnswer.isNotBlank()) Text(aiAnswer, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp), color = MaterialTheme.colorScheme.onSurface)
                        else Text("Ask questions across your notebooks, generate exam questions, practise quizzes or research a topic. Note references are retrieved locally and sent only with your request.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (reading && item != null) {
        Dialog(onDismissRequest = { reading = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f)) {
                Column(Modifier.fillMaxSize().padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("READING MODE", color = Color(0xFFD5B98C), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { reading = false; aiOpen = true }) { Text("Ask AI") }
                        TextButton(onClick = { reading = false }) { Text("Done") }
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(readingText.ifBlank { item.body }, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, lineHeight = 29.sp), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = Color(0xFFD5B98C))
                        Spacer(Modifier.width(8.dp))
                        Text(clock, fontFamily = FontFamily.Monospace, color = Color(0xFFD5B98C))
                        Spacer(Modifier.weight(1f))
                        Text("Read-only", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
