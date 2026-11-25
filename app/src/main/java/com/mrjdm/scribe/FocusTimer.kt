package com.mrjdm.scribe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrjdm.scribe.data.ScribeViewModel
import com.mrjdm.scribe.data.StudyProgress
import kotlinx.coroutines.delay

private val FocusGold = Color(0xFFD5B98C)
private val FocusPanel = Color(0xFF1B1C20)
private val FocusMuted = Color(0xFF96938D)

/** Foreground focus timer. Completed sessions are stored locally in the study history. */
@Composable
fun FocusTimer(vm: ScribeViewModel) {
    val context = LocalContext.current
    val sessions by vm.kind("study_session").collectAsState(initial = emptyList())
    val preferences = remember { context.getSharedPreferences("scribe_study", android.content.Context.MODE_PRIVATE) }
    var dailyGoalMinutes by remember { mutableIntStateOf(preferences.getInt("daily_goal_minutes", 60)) }
    var durationMinutes by remember { mutableIntStateOf(25) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    val nowMillis = System.currentTimeMillis()
    val todayMinutes = StudyProgress.todayMinutes(sessions, nowMillis)
    val weeklySessions = StudyProgress.sessionsThisWeek(sessions, nowMillis)
    val weeklyMinutes = weeklySessions.sumOf { StudyProgress.sessionMinutes(it.tags) }
    val streak = StudyProgress.streakDays(sessions, nowMillis)
    var running by remember { mutableStateOf(false) }
    var sessionLabel by remember { mutableStateOf("Focused study") }
    var completed by remember { mutableIntStateOf(0) }

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
        Card(colors = CardDefaults.cardColors(containerColor = FocusPanel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TODAY'S STUDY GOAL", color = FocusMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("$todayMinutes / $dailyGoalMinutes min", color = FocusGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("🔥 $streak day${if (streak == 1) "" else "s"}", color = FocusGold, fontWeight = FontWeight.SemiBold)
                        Text("study streak", color = FocusMuted, fontSize = 10.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120).forEach { value ->
                        FilterChip(selected = dailyGoalMinutes == value, onClick = {
                            dailyGoalMinutes = value
                            preferences.edit().putInt("daily_goal_minutes", value).apply()
                        }, label = { Text("$value min", fontSize = 10.sp) })
                    }
                }
                LinearProgressIndicator(
                    progress = { (todayMinutes.toFloat() / dailyGoalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = FocusGold, trackColor = Color(0xFF38352F)
                )
                Text(if (todayMinutes >= dailyGoalMinutes) "Daily goal completed" else "${dailyGoalMinutes - todayMinutes} min left today",
                    color = FocusMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("THIS WEEK", color = FocusMuted, fontSize = 9.sp)
                        Text("${weeklyMinutes / 60}h ${weeklyMinutes % 60}m", color = FocusGold, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("SESSIONS THIS WEEK", color = FocusMuted, fontSize = 9.sp)
                        Text(weeklySessions.size.toString(), color = FocusGold, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
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
        Text("The timer runs while Scribe remains open. Completed sessions are saved locally and included in your daily goal, streak and weekly statistics.",
            color = FocusMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
