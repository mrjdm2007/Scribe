package com.mrjdm.scribe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrjdm.scribe.data.ScribeItem
import com.mrjdm.scribe.data.ScribeViewModel

private val QuizGold = Color(0xFFD9BD91)
private val QuizMuted = Color(0xFFAAA69E)
private val QuizPanel = Color(0xFF1B1D21)
private const val OPTIONS_MARK = "\n---OPTIONS---\n"
private const val ANSWER_MARK = "\n---CORRECT---\n"
private const val EXPLANATION_MARK = "\n---EXPLANATION---\n"

private data class QuizContent(val prompt: String, val options: List<String>, val correct: String, val explanation: String)

private fun parseQuiz(item: ScribeItem): QuizContent {
    val optionsPart = item.body.substringAfter(OPTIONS_MARK, "")
    val correctPart = optionsPart.substringAfter(ANSWER_MARK, "")
    val explanationPart = correctPart.substringAfter(EXPLANATION_MARK, "")
    val correct = correctPart.substringBefore(EXPLANATION_MARK).trim().ifBlank { "A" }
    return QuizContent(
        prompt = item.body.substringBefore(OPTIONS_MARK),
        options = optionsPart.substringBefore(ANSWER_MARK).lines().filter { it.isNotBlank() }.take(4),
        correct = correct,
        explanation = explanationPart
    )
}

@Composable
fun QuestionBankScreen(vm: ScribeViewModel, practice: Boolean) {
    val questions by vm.kind("study_question").collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<List<Long>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    val active = questions.firstOrNull { it.id == session.getOrNull(index) }
    val content = active?.let(::parseQuiz)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (practice) "PRACTICE MODE" else "QUESTION BANK", color = QuizMuted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Text(if (practice) "Test your understanding." else "Build your own question library.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(if (practice) "Answer saved multiple-choice questions and review explanations." else "Store questions by topic and use them in a focused practice session.", color = QuizMuted, fontSize = 12.sp)
        if (!practice) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${questions.size} QUESTIONS", color = QuizMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = QuizGold, contentColor = Color(0xFF211B12))) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add question")
                }
            }
            if (questions.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = QuizPanel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Quiz, null, tint = QuizGold, modifier = Modifier.size(32.dp))
                        Text("Your question bank is empty", fontWeight = FontWeight.SemiBold)
                        Text("Create MCQs and use Practice to test yourself.", color = QuizMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(questions, key = { it.id }) { item ->
                        val q = parseQuiz(item)
                        Card(colors = CardDefaults.cardColors(containerColor = QuizPanel), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { vm.removeTree(item) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = QuizMuted) }
                                }
                                Text(q.prompt, color = QuizMuted, fontSize = 12.sp)
                                Text("${q.options.size} options · Answer ${q.correct}", color = QuizGold, fontSize = 11.sp)
                                if (q.explanation.isNotBlank()) Text(q.explanation, color = QuizMuted, fontSize = 11.sp, maxLines = 2)
                            }
                        }
                    }
                }
            }
        } else {
            if (session.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = QuizPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("READY WHEN YOU ARE", color = QuizGold, fontSize = 10.sp, letterSpacing = 1.3.sp)
                        Text("${questions.size} questions available", style = MaterialTheme.typography.titleLarge)
                        Text("Start a mixed practice set. Your score is calculated locally and nothing is sent anywhere.", color = QuizMuted, fontSize = 12.sp)
                        Button(onClick = {
                            session = questions.shuffled().map { it.id }
                            index = 0; score = 0; selected = null; submitted = false
                        }, enabled = questions.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = QuizGold, contentColor = Color(0xFF211B12))) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start practice")
                        }
                    }
                }
            } else if (active == null || index >= session.size) {
                Card(colors = CardDefaults.cardColors(containerColor = QuizPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = QuizGold, modifier = Modifier.size(38.dp))
                        Text("Practice complete", style = MaterialTheme.typography.titleLarge)
                        Text("$score / ${session.size}", color = QuizGold, style = MaterialTheme.typography.headlineLarge)
                        Text("Review the topics you found difficult, then try another set.", color = QuizMuted, fontSize = 12.sp)
                        Button(onClick = { session = emptyList() }, colors = ButtonDefaults.buttonColors(containerColor = QuizGold, contentColor = Color(0xFF211B12))) { Text("Finish") }
                    }
                }
            } else if (content != null) {
                Text("QUESTION ${index + 1} OF ${session.size}", color = QuizMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                Card(colors = CardDefaults.cardColors(containerColor = QuizPanel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(active.title, color = QuizGold, fontSize = 11.sp)
                        Text(content.prompt, style = MaterialTheme.typography.titleMedium)
                        content.options.forEachIndexed { optionIndex, option ->
                            val letter = ('A'.code + optionIndex).toChar().toString()
                            val isCorrect = submitted && letter == content.correct
                            val isWrong = submitted && selected == optionIndex && letter != content.correct
                            Card(
                                onClick = { if (!submitted) selected = optionIndex },
                                colors = CardDefaults.cardColors(containerColor = when {
                                    isCorrect -> Color(0xFF294238)
                                    isWrong -> Color(0xFF49302F)
                                    selected == optionIndex -> Color(0xFF39342A)
                                    else -> Color(0xFF25272C)
                                }),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(letter, color = QuizGold, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(option, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    if (isCorrect) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF8DC6A7))
                                    else if (isWrong) Icon(Icons.Rounded.Cancel, null, tint = Color(0xFFE49A91))
                                }
                            }
                        }
                        if (submitted && content.explanation.isNotBlank()) Text("Explanation: ${content.explanation}", color = QuizMuted, fontSize = 12.sp)
                        Button(
                            onClick = {
                                if (!submitted) {
                                    if (selected != null && ('A'.code + selected!!).toChar().toString() == content.correct) score++
                                    submitted = true
                                } else {
                                    index++; selected = null; submitted = false
                                }
                            },
                            enabled = submitted || selected != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = QuizGold, contentColor = Color(0xFF211B12))
                        ) { Text(if (submitted) if (index == session.lastIndex) "See results" else "Next question" else "Check answer") }
                    }
                }
                TextButton(onClick = { session = emptyList(); selected = null; submitted = false }) { Text("End practice early", color = QuizMuted) }
            }
        }
    }

    if (showCreate) {
        var prompt by remember { mutableStateOf("") }
        var topic by remember { mutableStateOf("") }
        var options by remember { mutableStateOf(List(4) { "" }) }
        var correct by remember { mutableStateOf("A") }
        var explanation by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Add a question") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prompt, { prompt = it }, label = { Text("Question") }, minLines = 2)
                    OutlinedTextField(topic, { topic = it }, label = { Text("Topic / subject") }, singleLine = true)
                    options.forEachIndexed { i, value ->
                        OutlinedTextField(value, { v -> options = options.mapIndexed { j, old -> if (i == j) v else old } },
                            label = { Text("Option ${('A'.code+i).toChar()}") }, singleLine = true)
                    }
                    Text("CORRECT OPTION", color = QuizMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("A","B","C","D").forEach { option ->
                            FilterChip(correct == option, { correct = option }, label = { Text(option) })
                        }
                    }
                    OutlinedTextField(explanation, { explanation = it }, label = { Text("Explanation (optional)") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = prompt.isNotBlank() && options.all { it.isNotBlank() }, onClick = {
                    val body = prompt + OPTIONS_MARK + options.joinToString("\n") + ANSWER_MARK + correct + EXPLANATION_MARK + explanation
                    vm.add(null, "study_question", prompt.take(80), body = body, tags = "subject:$topic")
                    showCreate = false
                }) { Text("Save question", color = QuizGold) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
            containerColor = Color(0xFF202125)
        )
    }
}
