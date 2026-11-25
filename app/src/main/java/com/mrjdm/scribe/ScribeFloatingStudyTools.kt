package com.mrjdm.scribe

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
fun ScribeFloatingStudyTools(item: ScribeItem?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("scribe_global_study_timer", Context.MODE_PRIVATE) }
    var elapsedBase by remember { mutableLongStateOf(prefs.getLong("elapsed_ms", 0L)) }
    var startedAt by remember { mutableLongStateOf(prefs.getLong("started_at", 0L)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
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
        if (item != null) {
            FilledTonalButton(onClick = { reading = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.Rounded.Book, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Read")
            }
        }
    }

    if (reading && item != null) {
        Dialog(onDismissRequest = { reading = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f)) {
                Column(Modifier.fillMaxSize().padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("READING MODE", color = Color(0xFFD5B98C), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { reading = false }) { Text("Done") }
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text(item.body, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, lineHeight = 29.sp), color = MaterialTheme.colorScheme.onSurface)
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
