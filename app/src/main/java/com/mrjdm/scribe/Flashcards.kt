package com.mrjdm.scribe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flip
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FlashGold = Color(0xFFD5B98C)
private val FlashMuted = Color(0xFF96938D)
private val FlashPanel = Color(0xFF1B1D21)

private fun flashMeta(tags: String, key: String, fallback: String): String =
    tags.split(";").firstOrNull { it.startsWith("$key:") }?.substringAfter(":") ?: fallback

@Composable
fun FlashcardsScreen(vm: ScribeViewModel) {
    val cards by vm.kind("flashcard").collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    val due = cards.filter { (flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L) <= now }
        .sortedBy { flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L }
    val next = due.firstOrNull()
    var revealed by remember(next?.id) { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    val upcoming = cards.count { (flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L) > now }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SPACED REPETITION", color = FlashMuted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Text("Flashcards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("TOTAL", cards.size.toString(), Modifier.weight(1f))
            StatPill("DUE NOW", due.size.toString(), Modifier.weight(1f))
            StatPill("UPCOMING", upcoming.toString(), Modifier.weight(1f))
        }
        if (next == null) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = FlashPanel)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF8DC6A7), modifier = Modifier.size(36.dp))
                    Text(if (cards.isEmpty()) "Build your first deck" else "You're caught up", style = MaterialTheme.typography.titleLarge)
                    Text(if (cards.isEmpty()) "Create cards for formulas, definitions, circuit concepts and key facts." else "$upcoming cards are scheduled for later review.", color = FlashMuted, fontSize = 12.sp)
                }
            }
        } else {
            Text("REVIEWED ${cards.size - upcoming - due.size} · ${due.size} DUE", color = FlashMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = FlashPanel)) {
                Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (revealed) "ANSWER" else "QUESTION", color = FlashGold, fontSize = 10.sp, letterSpacing = 1.4.sp)
                        Text(next.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (revealed) {
                            HorizontalDivider(color = Color(0xFF3B3D41))
                            Text(next.body.ifBlank { "No answer has been added." }, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE5E0D7))
                        }
                    }
                    OutlinedButton(onClick = { revealed = !revealed }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Flip, null); Spacer(Modifier.width(7.dp)); Text(if (revealed) "Show question" else "Reveal answer")
                    }
                }
            }
            Text("How well did you remember it?", color = FlashMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ReviewButton("Again", Color(0xFFE49A91), Modifier.weight(1f), enabled = revealed) {
                    scheduleFlashcard(vm, next, interval = 0.006, easeDelta = -0.2)
                }
                ReviewButton("Good", FlashGold, Modifier.weight(1f), enabled = revealed) {
                    val old = flashMeta(next.tags, "interval", "0").toDoubleOrNull() ?: 0.0
                    scheduleFlashcard(vm, next, interval = if (old < 1.0) 1.0 else old * (flashMeta(next.tags, "ease", "2.5").toDoubleOrNull() ?: 2.5), easeDelta = 0.0)
                }
                ReviewButton("Easy", Color(0xFF8DC6A7), Modifier.weight(1f), enabled = revealed) {
                    val old = flashMeta(next.tags, "interval", "0").toDoubleOrNull() ?: 0.0
                    scheduleFlashcard(vm, next, interval = if (old < 1.0) 4.0 else old * (flashMeta(next.tags, "ease", "2.5").toDoubleOrNull() ?: 2.5) * 1.3, easeDelta = 0.15)
                }
            }
        }
        OutlinedButton(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Create flashcard")
        }
        if (cards.isNotEmpty()) {
            Text("UPCOMING REVIEWS", color = FlashMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            cards.filter { (flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L) > now }
                .sortedBy { flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L }
                .take(3).forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 12.sp)
                            Text("Due " + SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(flashMeta(item.tags, "dueAt", "0").toLongOrNull() ?: now)), color = FlashMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { vm.removeTree(item) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = FlashMuted) }
                    }
                }
        }
    }

    if (create) {
        var front by remember { mutableStateOf("") }
        var back by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text("New flashcard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(front, { front = it }, label = { Text("Question / front") }, minLines = 2)
                    OutlinedTextField(back, { back = it }, label = { Text("Answer / back") }, minLines = 3)
                    OutlinedTextField(subject, { subject = it }, label = { Text("Subject or topic (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = front.isNotBlank() && back.isNotBlank(), onClick = {
                    val tags = "dueAt:${System.currentTimeMillis()};interval:0;ease:2.5;subject:${subject.trim()}"
                    vm.add(null, "flashcard", front.trim(), body = back.trim(), tags = tags)
                    create = false
                }) { Text("Save card", color = FlashGold) }
            },
            dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } },
            containerColor = Color(0xFF202125)
        )
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = FlashPanel), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(label, color = FlashMuted, fontSize = 9.sp, letterSpacing = 0.7.sp)
            Text(value, color = FlashGold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReviewButton(label: String, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 3.dp)) {
        Text(label, color = color, fontSize = 12.sp)
    }
}

private fun scheduleFlashcard(vm: ScribeViewModel, item: ScribeItem, interval: Double, easeDelta: Double) {
    val oldEase = flashMeta(item.tags, "ease", "2.5").toDoubleOrNull() ?: 2.5
    val newEase = (oldEase + easeDelta).coerceIn(1.3, 3.0)
    val days = interval.coerceIn(0.006, 3650.0)
    val dueAt = System.currentTimeMillis() + (days * 24.0 * 60.0 * 60.0 * 1000.0).toLong()
    val newTags = item.tags.split(";").filterNot { it.startsWith("dueAt:") || it.startsWith("interval:") || it.startsWith("ease:") }
        .plus("dueAt:$dueAt").plus("interval:$days").plus("ease:$newEase").joinToString(";")
    vm.update(item, tags = newTags)
}
