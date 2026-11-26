package com.mrjdm.scribe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrjdm.scribe.data.ScribeItem
import com.mrjdm.scribe.data.ScribeViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScribeIntelligenceScreen(vm: ScribeViewModel) {
    val scope = rememberCoroutineScope()
    val context: android.content.Context = LocalContext.current
    val chatPreferences: android.content.SharedPreferences = remember(context) {
        context.getSharedPreferences("scribe_chat_preferences", android.content.Context.MODE_PRIVATE)
    }
    var notes by remember { mutableStateOf<List<ScribeItem>>(emptyList()) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("notes") }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var artifact by remember { mutableStateOf<JSONObject?>(null) }
    var savedCards by remember { mutableIntStateOf(0) }
    var conversationId by remember { mutableStateOf("") }
    var conversationTurns by remember { mutableStateOf(emptyList<String>()) }
    val modes = listOf("notes", "explain", "exam", "quiz", "research", "summary", "flashcards",
        "mindmap", "socratic", "teach", "revise", "memorise", "practice", "test", "followup",
        "compare", "glossary", "formula_sheet", "study_plan", "lesson", "misconception", "mnemonics", "case_study")

    LaunchedEffect(Unit) {
        notes = runCatching { vm.all() }.getOrDefault(emptyList())
        val preferences = chatPreferences
        val savedId = preferences.getString("active_conversation_id", "").orEmpty()
        if (savedId.isNotBlank()) {
            runCatching {
                val response = vm.loadFirebaseConversation(savedId)
                val conversation = response.optJSONObject("conversation")
                val messages = conversation?.optJSONArray("messages") ?: JSONArray()
                val restored = buildList {
                    for (index in 0 until messages.length()) {
                        val message = messages.optJSONObject(index) ?: continue
                        val role = message.optString("role")
                        val content = message.optString("content")
                        if (content.isNotBlank()) add((if (role == "assistant") "SCRIBE: " else "STUDENT: ") + content)
                    }
                }
                conversationId = savedId
                conversationTurns = restored.takeLast(16)
            }
        }
    }

    fun pathFor(item: ScribeItem): String {
        val names = mutableListOf(item.title)
        var parent = item.parentId
        val seen = mutableSetOf<Long>()
        while (parent != null && seen.add(parent)) {
            val found = notes.firstOrNull { it.id == parent } ?: break
            names.add(found.title)
            parent = found.parentId
        }
        return names.asReversed().joinToString(" / ")
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("SCRIBE INTELLIGENCE", fontSize = 11.sp, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.secondary)
                Text("Your study assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { question = ""; answer = ""; error = ""; artifact = null; savedCards = 0; conversationId = ""; conversationTurns = emptyList(); chatPreferences.edit().remove("active_conversation_id").apply() }) { Text("New chat") }
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(value = mode.replace('_', ' ').replaceFirstChar { it.uppercase() },
                onValueChange = {}, readOnly = true, label = { Text("Study mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                modes.forEach { value ->
                    DropdownMenuItem(text = { Text(value.replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                        onClick = { mode = value; expanded = false })
                }
            }
        }
        OutlinedTextField(value = question, onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
            label = { Text("Ask Scribe") },
            placeholder = { Text("Ask your notes, explain a concept, practise or plan revision…") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI uses matching notes as source context", color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Button(enabled = question.isNotBlank() && !busy, onClick = {
                val studentQuestion = question.trim()
                val terms = studentQuestion.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.length > 2 }.toSet()
                val relevant = notes.filter { it.deletedAt == null && it.body.isNotBlank() }
                    .map { item -> item to (terms.count { item.title.contains(it, true) } * 3 + terms.count { item.body.contains(it, true) }) }
                    .filter { it.second > 0 }.sortedByDescending { it.second }.take(24)
                val noteContext = JSONArray()
                relevant.forEach { (item, _) ->
                    noteContext.put(JSONObject().put("title", item.title).put("path", pathFor(item)).put("content", item.body.take(12000)))
                }
                busy = true; error = ""; answer = ""; artifact = null
                val priorTurns = conversationTurns.takeLast(8).joinToString("\n\n")
                val contextualQuestion = if (priorTurns.isBlank()) studentQuestion
                    else priorTurns + "\n\nSTUDENT: " + studentQuestion
                scope.launch {
                    try {
                        val result = vm.askFirebaseAI(contextualQuestion, mode, noteContext)
                        answer = result.optString("answer").ifBlank { "The assistant returned an empty response." }
                        artifact = result.optJSONObject("artifact")
                        runCatching {
                            vm.saveFirebaseConversationTurn(conversationId.ifBlank { null }, mode, studentQuestion, answer)
                        }.onSuccess { conversationId = it; chatPreferences.edit().putString("active_conversation_id", it).apply() }
                            .onFailure { error = "Answer generated, but conversation history could not sync: ${it.localizedMessage}" }
                        conversationTurns = (conversationTurns + listOf("STUDENT: " + studentQuestion, "SCRIBE: " + answer)).takeLast(16)
                        question = ""
                    } catch (failure: Throwable) {
                        error = failure.localizedMessage ?: "The Firebase AI request failed."
                    } finally { busy = false }
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Rounded.Send, null); Spacer(Modifier.width(6.dp)); Text("Ask") }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (answer.isNotBlank()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SCRIBE RESPONSE", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Text(answer, style = MaterialTheme.typography.bodyLarge)
                    if (mode == "flashcards" && artifact?.optJSONArray("flashcards") != null) {
                        OutlinedButton(enabled = savedCards == 0, onClick = {
                            val cards = artifact?.optJSONArray("flashcards") ?: JSONArray()
                            var count = 0
                            for (i in 0 until cards.length()) {
                                val card = cards.optJSONObject(i) ?: continue
                                val front = card.optString("front").trim()
                                val back = card.optString("back").trim()
                                if (front.isNotBlank() && back.isNotBlank()) {
                                    val source = card.optString("sourceTitle").replace(";", ",")
                                    vm.add(null, "flashcard", front, body = back,
                                        tags = if (source.isBlank()) "deck:AI generated" else "deck:AI generated;source_title:$source")
                                    count++
                                }
                            }
                            savedCards = count
                        }) { Text(if (savedCards > 0) "Saved $savedCards flashcards" else "Save flashcards to library") }
                    }
                }
            }
        } else if (!busy) {
            Text("Connect Firebase in Settings to use Scribe Intelligence. Your notes remain available offline.",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
