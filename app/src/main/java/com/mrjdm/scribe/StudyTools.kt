package com.mrjdm.scribe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrjdm.scribe.data.ScribeItem
import com.mrjdm.scribe.data.ScribeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.*

private val ToolGold = Color(0xFFD5B98C)
private val ToolMuted = Color(0xFF96938D)
private val ToolPanel = Color(0xFF1B1C20)

/** Persistent flashcards with simple spaced-repetition intervals. */
@Composable
fun StudyHub(vm: ScribeViewModel) {
    val cards by vm.kind("flashcard").collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var create by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<ScribeItem?>(null) }
    var reveal by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val due = cards.filter { tagLong(it.tags, "due") <= now }
    Column(Modifier.fillMaxSize()) {
        Text("SCRIBE / STUDY", color = ToolGold, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Study smarter.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text("A focused review queue that grows with your notes.", color = ToolMuted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudyMetric("TOTAL CARDS", cards.size.toString(), Modifier.weight(1f))
            StudyMetric("DUE NOW", due.size.toString(), Modifier.weight(1f))
            StudyMetric("MASTERED", cards.count { tagLong(it.tags, "interval") >= 21 }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Review queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { create = true }) { Icon(Icons.Rounded.Add, null); Text("New card") }
        }
        if (active != null) {
            val card = active!!
            val pair = card.body.split("\n---ANSWER---\n", limit = 2)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (reveal) "ANSWER" else "QUESTION", color = ToolGold, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(if (reveal) pair.getOrElse(1) { "" } else pair.firstOrNull().orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(20.dp))
                    if (!reveal) Button(onClick = { reveal = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolGold, contentColor = Color(0xFF211B12))) { Text("Reveal answer") }
                    else {
                        Text("How well did you remember?", color = ToolMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Again" to 0, "Hard" to 1, "Good" to 2, "Easy" to 3).forEach { (label, rating) ->
                                OutlinedButton(onClick = {
                                    val old = tagLong(card.tags, "interval").coerceAtLeast(1)
                                    val next = nextReviewIntervalDays(old, rating)
                                    val reviewedAt = System.currentTimeMillis()
                                    val dueAt = reviewedAt + next * 24L * 60L * 60L * 1000L
                                    val updatedTags = replaceTag(
                                        replaceTag(
                                            replaceTag(card.tags, "interval", next.toString()),
                                            "due", dueAt.toString()
                                        ),
                                        "reviewedAt", reviewedAt.toString()
                                    )
                                    vm.update(card, tags = updatedTags)
                                    active = null; reveal = false
                                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, fontSize = 10.sp) }
                            }
                        }
                    }
                    TextButton(onClick = { active = null; reveal = false }) { Text("Close review") }
                }
            }
        } else {
            val queue = due.sortedBy { tagLong(it.tags, "due") }
            if (queue.isEmpty()) {
                StudyEmpty("You're all caught up", "Add flashcards from your lectures and Scribe will build a review queue.")
            } else {
                queue.take(12).forEach { card ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = ToolPanel)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(card.title, fontWeight = FontWeight.SemiBold)
                                Text(card.body.substringBefore("\n---ANSWER---\n").take(90), color = ToolMuted, fontSize = 12.sp, maxLines = 2)
                            }
                            TextButton(onClick = { active = card; reveal = false }) { Text("Review", color = ToolGold) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("All flashcards", style = MaterialTheme.typography.titleMedium)
        LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
            items(cards, key = { it.id }) { card ->
                ListItem(headlineContent = { Text(card.title) }, supportingContent = { Text("Interval: ${tagLong(card.tags, "interval").coerceAtLeast(1)} day(s)", color = ToolMuted) },
                    leadingContent = { Icon(Icons.Rounded.Style, null, tint = ToolGold) },
                    trailingContent = { IconButton(onClick = { vm.removeTree(card) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = ToolMuted) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
    if (create) {
        var question by remember { mutableStateOf("") }
        var answer by remember { mutableStateOf("") }
        var deck by remember { mutableStateOf("General") }
        AlertDialog(onDismissRequest = { create = false }, title = { Text("Create flashcard") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(question, { question = it }, label = { Text("Question / front") }, minLines = 2)
                OutlinedTextField(answer, { answer = it }, label = { Text("Answer / back") }, minLines = 2)
                OutlinedTextField(deck, { deck = it }, label = { Text("Deck / topic") }, singleLine = true)
            } },
            confirmButton = { TextButton(enabled = question.isNotBlank() && answer.isNotBlank(), onClick = {
                vm.add(null, "flashcard", question.take(70), body = question + "\n---ANSWER---\n" + answer,
                    tags = "deck:$deck;interval:1;due:${System.currentTimeMillis()}")
                create = false
            }) { Text("Save card", color = ToolGold) } },
            dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } },
            containerColor = Color(0xFF202125))
    }
}

@Composable
private fun StudyMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = ToolMuted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = ToolGold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StudyEmpty(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.School, null, tint = ToolGold, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp)); Text(title, fontWeight = FontWeight.SemiBold)
        Text(message, color = ToolMuted, fontSize = 12.sp)
    }
}

private fun tagLong(tags: String, key: String): Long =
    tags.split(';', ',').firstOrNull { it.trim().startsWith("$key:") }?.substringAfter(':')?.toLongOrNull() ?: 0L

private fun replaceTag(tags: String, key: String, value: String): String {
    val kept = tags.split(';', ',').map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("$key:") }
    return (kept + "$key:$value").joinToString(";")
}

/** Offline academic utilities: unit conversion, graph plotting, equation symbols and diagram starters. */
@Composable
fun AcademicToolsHub(vm: ScribeViewModel) {
    var selected by remember { mutableStateOf("Converter") }
    val sections = listOf("Converter", "Graph", "Equations", "Circuit", "Tables", "Formula", "Questions", "Practice", "Reminders", "Focus", "Diagrams")
    Column(Modifier.fillMaxSize()) {
        Text("SCRIBE / WORKBENCH", color = ToolGold, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Academic workbench", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text("Offline tools for calculations, notation and visual thinking.", color = ToolMuted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.forEach { section ->
                FilterChip(selected == section, onClick = { selected = section }, label = { Text(section, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when(selected) {
            "Converter" -> UnitConverter()
            "Graph" -> GraphTool(vm)
            "Equations" -> EquationComposer(vm)
            "Circuit" -> CircuitWorkbench(vm)
            "Tables" -> ScientificTable(vm)
            "Formula" -> FormulaLibrary(vm)
            "Questions" -> QuestionBankScreen(vm, practice = false)
            "Practice" -> QuestionBankScreen(vm, practice = true)
            "Reminders" -> ReminderHub(vm)
            "Focus" -> FocusTimer(vm)
            else -> DiagramStarter(vm)
        }
    }
}

@Composable
private fun ReminderHub(vm: ScribeViewModel) {
    val context = LocalContext.current
    val reminders by vm.kind("study_reminder").collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Review your notes and formulas.") }
    var dateTime by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + 60L * 60L * 1000L))) }
    var error by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("LOCAL NOTIFICATIONS", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.3.sp)
        Text("Plan a revision session.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Reminders stay on this device. Pending reminders are rescheduled after a reboot.", color = ToolMuted, fontSize = 12.sp)
        OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Reminder title") }, singleLine = true)
        OutlinedTextField(message, { message = it }, modifier = Modifier.fillMaxWidth(), label = { Text("What should you review?") }, minLines = 2)
        OutlinedTextField(dateTime, { dateTime = it; error = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Date and time · yyyy-MM-dd HH:mm") }, singleLine = true)
        if (error.isNotBlank()) Text(error, color = Color(0xFFE49A91), fontSize = 11.sp)
        Button(onClick = {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }
            val parsePosition = ParsePosition(0)
            val parsed = runCatching { parser.parse(dateTime, parsePosition)?.time?.takeIf { parsePosition.index == dateTime.length } }.getOrNull()
            if (title.isBlank()) error = "Add a title for this reminder."
            else if (parsed == null || parsed <= System.currentTimeMillis()) error = "Enter a valid future date and time."
            else {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    error = "Allow notifications, then tap Schedule reminder again."
                } else {
                    vm.add(null, "study_reminder", title.trim(), body = message.trim(), tags = "dueAt:$parsed") { id ->
                        scheduleStudyReminder(context, id, title.trim(), message.trim(), parsed)
                    }
                    title = ""; error = "Reminder scheduled."
                }
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolGold, contentColor = Color(0xFF211B12))) {
            Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(7.dp)); Text("Schedule reminder")
        }
        if (error == "Reminder scheduled.") Text(error, color = Color(0xFF8DC6A7), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text("UPCOMING", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
        if (reminders.isEmpty()) Text("No reminders yet.", color = ToolMuted, fontSize = 12.sp)
        reminders.sortedBy { it.tags.substringAfter("dueAt:", "").substringBefore(";").toLongOrNull() ?: Long.MAX_VALUE }.forEach { item ->
            val at = item.tags.substringAfter("dueAt:", "").substringBefore(";").toLongOrNull()
            Card(colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Medium)
                        Text(at?.let { SimpleDateFormat("EEE, dd MMM · HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Date unavailable", color = ToolGold, fontSize = 11.sp)
                        Text(item.body, color = ToolMuted, fontSize = 11.sp, maxLines = 2)
                    }
                    IconButton(onClick = { cancelStudyReminder(context, item.id); vm.removeTree(item) }) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = ToolMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitConverter() {
    val groups = EngineeringUnitCatalog.groups
    var group by remember { mutableStateOf("Voltage") }
    var amount by remember { mutableStateOf("1") }
    var from by remember { mutableStateOf("V") }
    var to by remember { mutableStateOf("mV") }
    val units = groups[group].orEmpty()
    val result = amount.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it.isFinite() }
        ?.let { EngineeringUnitCatalog.convert(it, from, to) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ENGINEERING & SCIENTIFIC CONVERTER", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text("Convert real physical quantities — not just metric prefixes.", color = ToolMuted, fontSize = 12.sp)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 172.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(groups.keys.toList().chunked(3).flatten().distinct()) { category ->
                // Categories are rendered in the grid below; this branch is intentionally empty.
            }
        }
        groups.keys.toList().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { category ->
                    FilterChip(
                        selected = group == category,
                        onClick = {
                            group = category
                            val next = groups.getValue(category)
                            from = next.first().symbol
                            to = next.getOrElse(1) { next.first() }.symbol
                        },
                        label = { Text(category, fontSize = 10.sp) }
                    )
                }
            }
        }
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            label = { Text("Value to convert") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FROM", color = ToolMuted, fontSize = 10.sp)
                UnitPicker(units.map { it.symbol }, from) { from = it }
            }
            Icon(Icons.Rounded.ArrowForward, null, tint = ToolGold)
            Column(Modifier.weight(1f)) {
                Text("TO", color = ToolMuted, fontSize = 10.sp)
                UnitPicker(units.map { it.symbol }, to) { to = it }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("CONVERTED VALUE", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(
                    result?.let { EngineeringUnitCatalog.format(it) } ?: "Enter a valid number",
                    color = ToolGold, style = MaterialTheme.typography.headlineMedium
                )
                Text("$amount $from = ${result?.let { EngineeringUnitCatalog.format(it) } ?: "—"} $to", color = ToolMuted, fontSize = 12.sp)
            }
        }
        Text("QUICK REFERENCE", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        groups[group].orEmpty().forEach { unit ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(unit.symbol, fontWeight = FontWeight.Medium)
                Text(unit.description, color = ToolMuted, fontSize = 11.sp)
            }
        }
        Text("Temperature uses exact offset conversions. All other conversions use SI-based scale factors.", color = ToolMuted, fontSize = 10.sp)
    }
}

@Composable
private fun UnitPicker(units: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected); Icon(Icons.Rounded.ArrowDropDown, null) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            units.forEach { unit -> DropdownMenuItem(text = { Text(unit) }, onClick = { onSelect(unit); expanded = false }) }
        }
    }
}

@Composable
private fun GraphTool(vm: ScribeViewModel) {
    var expression by remember { mutableStateOf("sin(x)") }
    var range by remember { mutableStateOf("6.28") }
    val maxX = range.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(1f, 30f) ?: 6.28f
    val supported = expression.trim().lowercase() in setOf("sin(x)", "cos(x)", "tan(x)", "x", "x^2", "1/x", "exp(x)")
    val fn: (Float) -> Float = { x ->
        when(expression.trim().lowercase()) {
            "sin(x)" -> sin(x); "cos(x)" -> cos(x); "tan(x)" -> tan(x).coerceIn(-5f, 5f)
            "x" -> x; "x^2" -> x*x; "1/x" -> if (abs(x) < .05f) Float.NaN else 1f/x
            "exp(x)" -> exp(x).coerceIn(-10f, 10f); else -> Float.NaN
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FUNCTION PLOTTER", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        OutlinedTextField(expression, { expression = it }, label = { Text("Function: sin(x), cos(x), tan(x), x, x^2, 1/x, exp(x)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(range, { range = it }, label = { Text("X range (−range to +range)") }, singleLine = true)
        if (!supported) Text("Unsupported expression. Choose sin(x), cos(x), tan(x), x, x^2, 1/x or exp(x).", color = Color(0xFFE49A91), fontSize = 11.sp)
        Card(colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(250.dp).padding(12.dp)) {
                val cx = size.width/2; val cy = size.height/2
                drawLine(ToolMuted.copy(alpha=.6f), Offset(0f,cy), Offset(size.width,cy), 1.dp.toPx())
                drawLine(ToolMuted.copy(alpha=.6f), Offset(cx,0f), Offset(cx,size.height), 1.dp.toPx())
                for(i in 1..8) {
                    val x = size.width*i/9f
                    val y = size.height*i/9f
                    drawLine(ToolMuted.copy(alpha=.12f), Offset(x,0f), Offset(x,size.height), 1.dp.toPx())
                    drawLine(ToolMuted.copy(alpha=.12f), Offset(0f,y), Offset(size.width,y), 1.dp.toPx())
                }
                val path = Path()
                var started = false
                val scaleY = size.height/12f
                for(i in 0..500) {
                    val x = -maxX + 2*maxX*i/500f
                    val y = fn(x)
                    val px = size.width*i/500f
                    val py = cy - y*scaleY
                    if(y.isFinite() && py in 0f..size.height) {
                        if(!started) { path.moveTo(px,py); started = true } else path.lineTo(px,py)
                    } else started = false
                }
                drawPath(path, ToolGold, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        Button(enabled = supported, onClick = {
            vm.add(null, "formula", "y = $expression", body = "y = $expression\nRange: -$range to +$range (radians)", tags = "type:graph-formula")
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolGold, contentColor = Color(0xFF211B12))) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save graph to formula library")
        }
        Text("Supported functions are evaluated locally. Use radians for trigonometric functions. Unsupported expressions fall back to sin(x).", color = ToolMuted, fontSize = 11.sp)
    }
}

@Composable
private fun EquationComposer(vm: ScribeViewModel) {
    var latex by remember { mutableStateOf("") }
    var formulaTitle by remember { mutableStateOf("New formula") }
    val savedFormulas by vm.kind("formula").collectAsState(initial = emptyList())
    val symbols = listOf("α","β","γ","θ","λ","μ","π","ω","Δ","Σ","∫","∂","∇","√","∞","±","×","÷","≤","≥","≠","→","↔","₀","₁","₂","³","⁴","⁵","²","⁻¹","/","^","_","{","}")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("EQUATION & SYMBOL PAD", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        OutlinedTextField(formulaTitle, { formulaTitle = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Formula name") })
        OutlinedTextField(latex, { latex = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Equation / LaTeX source") })
        Card(colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text("RENDERED EQUATION", color = ToolMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                MathJaxPreview(latex.ifBlank { "\\text{Your equation will appear here}" },
                    Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 180.dp))
            }
        }
        Text("SYMBOLS", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        symbols.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { symbol ->
                    OutlinedButton(onClick = { latex += symbol }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) { Text(symbol, fontSize = 15.sp) }
                }
            }
        }
        Button(onClick = {
            vm.add(null, "formula", formulaTitle.ifBlank { "New formula" }, body = latex, tags = "type:equation;source:latex")
            latex = ""
        }, enabled = latex.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolGold, contentColor = Color(0xFF211B12))) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save formula")
        }
        if (savedFormulas.isNotEmpty()) {
            Text("SAVED FORMULAS", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            savedFormulas.take(8).forEach { formula ->
                Card(colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formula.title, fontWeight = FontWeight.SemiBold)
                            Text(formula.body, color = ToolGold, fontSize = 12.sp, maxLines = 2)
                        }
                        IconButton(onClick = { formulaTitle = formula.title; latex = formula.body }) { Icon(Icons.Rounded.Edit, null, tint = ToolMuted) }
                        IconButton(onClick = { vm.removeTree(formula) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = ToolMuted) }
                    }
                }
            }
        }
        Text("Equations are rendered with MathJax when online. Your original LaTeX source remains saved and readable when offline.", color = ToolMuted, fontSize = 11.sp)
    }
}

@Composable
private fun DiagramStarter(vm: ScribeViewModel) {
    var title by remember { mutableStateOf("Concept map") }
    var labels by remember { mutableStateOf("Main idea,Concept A,Concept B,Details") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("DIAGRAM & CONCEPT MAP STARTER", color = ToolMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        OutlinedTextField(title, { title = it }, label = { Text("Diagram title") }, singleLine = true)
        OutlinedTextField(labels, { labels = it }, label = { Text("Node labels (comma separated)") }, minLines = 2)
        Card(colors = CardDefaults.cardColors(containerColor = ToolPanel), shape = RoundedCornerShape(16.dp)) {
            Canvas(Modifier.fillMaxWidth().height(190.dp).padding(12.dp)) {
                val points = listOf(Offset(size.width*.5f,size.height*.18f),Offset(size.width*.2f,size.height*.75f),Offset(size.width*.8f,size.height*.75f),Offset(size.width*.5f,size.height*.9f))
                points.drop(1).forEach { drawLine(ToolGold.copy(alpha=.65f),points[0],it,2.dp.toPx()) }
                points.forEach { drawCircle(ToolGold.copy(alpha=.18f),22.dp.toPx(),it); drawCircle(ToolGold,22.dp.toPx(),it,style=Stroke(1.5.dp.toPx())) }
            }
        }
        Button(onClick = {
            val nodes = labels.split(',').map { it.trim() }.filter { it.isNotBlank() }
            vm.add(null, "diagram", title, body = nodes.joinToString("\n") { "• $it" }, tags = "type:concept-map")
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ToolGold, contentColor = Color(0xFF211B12))) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save diagram outline to library")
        }
        Text("A saved outline can be expanded into a full diagram page. Circuit sketches and graph plots can be added as image blocks.", color = ToolMuted, fontSize = 11.sp)
    }
}
