package com.mrjdm.scribe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import java.util.Calendar
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
    fun deckOf(item: ScribeItem): String = flashMeta(item.tags, "deck", flashMeta(item.tags, "subject", "General")).ifBlank { "General" }
    val decks = listOf("All decks") + cards.map(::deckOf).distinct().sorted()
    var selectedDeck by remember { mutableStateOf("All decks") }
    val visibleCards = cards.filter { selectedDeck == "All decks" || deckOf(it) == selectedDeck }
    val due = visibleCards.filter { (flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L) <= now }
        .sortedBy { flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L }
    val next = due.firstOrNull()
    var revealed by remember(next?.id) { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<ScribeItem?>(null) }
    val upcoming = visibleCards.count { (flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L) > now }
    val todayStart = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val reviewedToday = visibleCards.count { (flashMeta(it.tags, "lastReviewedAt", "0").toLongOrNull() ?: 0L) >= todayStart }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SPACED REPETITION", color = FlashMuted, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Text("Flashcards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            decks.forEach { deck -> FilterChip(selected = selectedDeck == deck, onClick = { selectedDeck = deck }, label = { Text(deck) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("DUE NOW", due.size.toString(), Modifier.weight(1f))
            StatPill("UPCOMING", upcoming.toString(), Modifier.weight(1f))
            StatPill("REVIEWED TODAY", reviewedToday.toString(), Modifier.weight(1f))
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
            Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = FlashPanel)) {
                Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (revealed) "ANSWER" else "QUESTION", color = FlashGold, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editingCard = next }) { Text("Edit") }
                        }
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
                    scheduleFlashcard(vm, next, rating = 0)
                }
                ReviewButton("Hard", Color(0xFFE5C17B), Modifier.weight(1f), enabled = revealed) { scheduleFlashcard(vm, next, rating = 1) }
                ReviewButton("Good", FlashGold, Modifier.weight(1f), enabled = revealed) {
                    scheduleFlashcard(vm, next, rating = 2)
                }
                ReviewButton("Easy", Color(0xFF8DC6A7), Modifier.weight(1f), enabled = revealed) {
                    scheduleFlashcard(vm, next, rating = 3)
                }
            }
        }
        OutlinedButton(onClick = { create = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(7.dp)); Text("Create flashcard")
        }
        if (cards.isNotEmpty()) {
            Text("UPCOMING REVIEWS", color = FlashMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            visibleCards.filter { (flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L) > now }
                .sortedBy { flashMeta(it.tags, "dueAt", "0").toLongOrNull() ?: 0L }
                .take(3).forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 12.sp)
                            Text("Due " + SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(flashMeta(item.tags, "dueAt", "0").toLongOrNull() ?: now)), color = FlashMuted, fontSize = 10.sp)
                        }
                        TextButton(onClick = { editingCard = item }) { Text("Edit") }
                        IconButton(onClick = { vm.removeTree(item) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = FlashMuted) }
                    }
                }
        }
    }

    if (create) {
        var front by remember { mutableStateOf("") }
        var back by remember { mutableStateOf("") }
        var deck by remember { mutableStateOf("General") }
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text("New flashcard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(front, { front = it }, label = { Text("Question / front") }, minLines = 2)
                    OutlinedTextField(back, { back = it }, label = { Text("Answer / back") }, minLines = 3)
                    OutlinedTextField(deck, { deck = it }, label = { Text("Deck / subject") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = front.isNotBlank() && back.isNotBlank(), onClick = {
                    val safeDeck = deck.trim().ifBlank { "General" }.replace(";", " ")
                    val tags = "dueAt:${System.currentTimeMillis()};interval:0;ease:2.5;deck:$safeDeck"
                    vm.add(null, "flashcard", front.trim(), body = back.trim(), tags = tags)
                    create = false
                }) { Text("Save card", color = FlashGold) }
            },
            dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } },
            containerColor = Color(0xFF202125)
        )
    }
    editingCard?.let { card ->
        var front by remember(card.id) { mutableStateOf(card.title) }
        var back by remember(card.id) { mutableStateOf(card.body) }
        var deck by remember(card.id) { mutableStateOf(flashMeta(card.tags, "deck", flashMeta(card.tags, "subject", "General"))) }
        AlertDialog(
            onDismissRequest = { editingCard = null },
            title = { Text("Edit flashcard") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(front, { front = it }, label = { Text("Question / front") }, minLines = 2)
                OutlinedTextField(back, { back = it }, label = { Text("Answer / back") }, minLines = 3)
                OutlinedTextField(deck, { deck = it }, label = { Text("Deck / subject") }, singleLine = true)
            } },
            confirmButton = { TextButton(enabled = front.isNotBlank() && back.isNotBlank(), onClick = {
                val safeDeck = deck.trim().ifBlank { "General" }.replace(";", " ")
                val retained = card.tags.split(";").filterNot { it.startsWith("deck:") || it.startsWith("subject:") }
                vm.update(card, title = front.trim(), body = back.trim(), tags = (retained + "deck:$safeDeck").joinToString(";"))
                editingCard = null
            }) { Text("Save changes", color = FlashGold) } },
            dismissButton = { TextButton(onClick = { editingCard = null }) { Text("Cancel") } },
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

private fun scheduleFlashcard(vm: ScribeViewModel, item: ScribeItem, rating: Int) {
    vm.update(item, tags = flashcardReviewTags(item.tags, rating, System.currentTimeMillis()))
}
