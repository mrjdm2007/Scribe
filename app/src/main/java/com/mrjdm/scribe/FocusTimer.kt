package com.mrjdm.scribe

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import com.mrjdm.scribe.data.ScribeViewModel
import kotlinx.coroutines.delay
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

private val FocusGold = Color(0xFFD5B98C)
private val FocusPanel = Color(0xFF1B1C20)
private val FocusMuted = Color(0xFF96938D)

/** Foreground focus timer. Completed sessions are stored locally in the study history. */
@Composable
fun FocusTimer(vm: ScribeViewModel) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("scribe_preferences", 0) }
    var dailyGoalMinutes by remember {
        mutableIntStateOf(preferences.getInt("daily_study_goal_minutes", 60).coerceIn(30, 240))
    }
    var durationMinutes by remember { mutableIntStateOf(25) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    var sessionLabel by remember { mutableStateOf("Focused study") }
    var completed by remember { mutableIntStateOf(0) }
    val savedSessions by vm.kind("study_session").collectAsState(initial = emptyList())
    // Recompute so the weekly summary rolls over at the week boundary.
    val weekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val weeklySessions = savedSessions.filter { it.createdAt >= weekStart && it.deletedAt == null }
    val weeklyMinutes = weeklySessions.sumOf { it.tags.substringAfter("minutes:", "").substringBefore(";").toIntOrNull() ?: 0 }
    val today = LocalDate.now()
    val todayMinutes = StudyProgress.minutesStudied(savedSessions, today)
    val studyStreak = StudyProgress.currentStreakDays(savedSessions, today)

    LaunchedEffect(running) {
        if (running) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
            }
            if (remainingSeconds == 0) {
                running = false
                completed += 1
                vm.add(null, "study_session", sessionLabel.ifBlank { "Focused study" },
                    body = "Completed a $durationMinutes minute focus session.",
                    tags = "minutes:$durationMinutes;completedAt:${System.currentTimeMillis()}")
            }
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("FOCUS MODE", color = FocusMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text("Make time for deep work.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(sessionLabel, { sessionLabel = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Session / subject") }, singleLine = true)
        Card(colors = CardDefaults.cardColors(containerColor = FocusPanel), shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (running) "STAY IN THE FLOW" else "READY WHEN YOU ARE",
                    color = FocusMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                Text("%02d:%02d".format(minutes, seconds), color = FocusGold,
                    style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 1f - remainingSeconds.toFloat() / (durationMinutes * 60f).coerceAtLeast(1f) },
                    modifier = Modifier.fillMaxWidth(.82f), color = FocusGold,
                    trackColor = Color(0xFF38352F)
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60).forEach { value ->
                        FilterChip(selected = durationMinutes == value && !running, onClick = {
                            if (!running) { durationMinutes = value; remainingSeconds = value * 60 }
                        }, label = { Text("$value min") }, enabled = !running)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { running = !running },
                        colors = ButtonDefaults.buttonColors(containerColor = FocusGold, contentColor = Color(0xFF211B12))) {
                        Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp)); Text(if (running) "Pause" else "Start focus")
                    }
                    OutlinedButton(onClick = { running = false; remainingSeconds = durationMinutes * 60 }) {
                        Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reset")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = FocusPanel)) {
                Column(Modifier.padding(14.dp)) {
                    Text("THIS WEEK", color = FocusMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("%dh %02dm".format(weeklyMinutes / 60, weeklyMinutes % 60), color = FocusGold, fontWeight = FontWeight.SemiBold)
                    Text("Study time", color = FocusMuted, fontSize = 11.sp)
                }
            }
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = FocusPanel)) {
                Column(Modifier.padding(14.dp)) {
                    Text("SESSIONS", color = FocusMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("${weeklySessions.size}", color = FocusGold, fontWeight = FontWeight.SemiBold)
                    Text("Completed this week", color = FocusMuted, fontSize = 11.sp)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = FocusPanel),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TODAY'S STUDY GOAL", color = FocusMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("$todayMinutes / $dailyGoalMinutes min", color = FocusGold, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("🔥 $studyStreak day${if (studyStreak == 1) "" else "s"}",
                            color = FocusGold, fontWeight = FontWeight.SemiBold)
                        Text("study streak", color = FocusMuted, fontSize = 10.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120).forEach { value ->
                        FilterChip(
                            selected = dailyGoalMinutes == value,
                            onClick = {
                                dailyGoalMinutes = value
                                preferences.edit().putInt("daily_study_goal_minutes", value).apply()
                            },
                            label = { Text("$value min", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { (todayMinutes.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = FocusGold,
                    trackColor = Color(0xFF38352F)
                )
                Text(
                    if (todayMinutes >= dailyGoalMinutes) "Daily goal completed"
                    else "${dailyGoalMinutes - todayMinutes} min left to reach today's goal",
                    color = FocusMuted, fontSize = 11.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = FocusPanel)) {
                Column(Modifier.padding(14.dp)) {
                    Text("THIS VISIT", color = FocusMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("$completed completed", color = FocusGold, fontWeight = FontWeight.SemiBold)
                }
            }
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = FocusPanel)) {
                Column(Modifier.padding(14.dp)) {
                    Text("TIP", color = FocusMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("Take a short break between sessions.", fontSize = 12.sp)
                }
            }
        }
        Text("RECENT SESSIONS", color = FocusMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        if (savedSessions.isEmpty()) {
            Text("Your completed focus sessions will appear here.", color = FocusMuted, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedSessions.sortedByDescending { it.createdAt }.take(5).forEach { session ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(session.createdAt)),
                                color = FocusMuted, fontSize = 11.sp)
                        }
                        val minutes = session.tags.substringAfter("minutes:", "").substringBefore(";").toIntOrNull()
                        Text("${minutes?.toString() ?: "—"} min", color = FocusGold, fontSize = 12.sp)
                    }
                }
            }
        }
        Text("The timer runs while Scribe remains open. Completed sessions are saved locally; background alarms are not used.",
            color = FocusMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
