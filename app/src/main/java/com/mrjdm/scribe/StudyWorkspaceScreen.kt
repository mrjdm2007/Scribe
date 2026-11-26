package com.mrjdm.scribe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrjdm.scribe.data.ScribeItem
import com.mrjdm.scribe.data.LearningTipScheduler
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val WorkspaceGold = androidx.compose.ui.graphics.Color(0xFFD5B98C)
private val WorkspaceMuted = androidx.compose.ui.graphics.Color(0xFF96938D)

private data class WorkspaceTask(val id: String, val title: String, val done: Boolean = false, val notes: String = "")
private data class WorkspaceExam(val id: String, val title: String, val subject: String, val date: String, val notes: String, val tasks: List<WorkspaceTask>)
private data class WorkspaceFocus(val id: String, val subject: String, val topic: String, val minutes: Int, val planned: Int, val interruptions: Int, val date: String)
private data class WorkspaceFolder(val id: String, val name: String)

private class WorkspaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("scribe_study_workspace", Context.MODE_PRIVATE)
    fun read(key: String) = prefs.getString(key, "").orEmpty()
    fun write(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun bool(key: String, fallback: Boolean) = prefs.getBoolean(key, fallback)
    fun setBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun int(key: String, fallback: Int) = prefs.getInt(key, fallback)
    fun setInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyWorkspaceScreen(bookmarks: List<ScribeItem>, vm: com.mrjdm.scribe.data.ScribeViewModel) {
    val context = LocalContext.current
    val store = remember { WorkspaceStore(context) }
    val allItems by vm.observeAllItems().collectAsState(initial = emptyList())
    fun mirrorWorkspaceRecord(kind: String, title: String, body: String) {
        val existing = allItems.firstOrNull { it.kind == kind && it.title == title && it.deletedAt == null }
        if (existing != null) vm.update(existing, body = body)
        else vm.add(null, kind, title, body = body, tags = "workspace")
    }
    fun bookmarkKey(item: ScribeItem): String =
        item.tags.split(";").firstOrNull { it.startsWith("bookmark_key:") }?.substringAfter("bookmark_key:")?.takeIf { it.isNotBlank() }
            ?: "local_${item.id}"
    fun ensureBookmarkKey(item: ScribeItem): String {
        val existing = item.tags.split(";").firstOrNull { it.startsWith("bookmark_key:") }?.substringAfter("bookmark_key:")?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val key = UUID.randomUUID().toString()
        val nextTags = (item.tags.split(";").filter { it.isNotBlank() } + "bookmark_key:$key").joinToString(";")
        vm.update(item, tags = nextTags)
        return key
    }
    var section by remember { mutableStateOf("Overview") }
    val sections = listOf("Overview", "Exams", "Focus", "Bookmarks", "Reflect", "Reminders")
    val exams = remember { mutableStateListOf<WorkspaceExam>() }
    val focus = remember { mutableStateListOf<WorkspaceFocus>() }
    val folders = remember { mutableStateListOf<WorkspaceFolder>() }
    var folderLinks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var showExamDialog by remember { mutableStateOf(false) }
    var showFocusDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var examTitle by remember { mutableStateOf("") }
    var examSubject by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var focusSubject by remember { mutableStateOf("") }
    var focusTopic by remember { mutableStateOf("") }
    var focusMinutes by remember { mutableStateOf("25") }
    var folderName by remember { mutableStateOf("") }
    var reflectionWins by remember { mutableStateOf("") }
    var reflectionChallenges by remember { mutableStateOf("") }
    var reflectionNext by remember { mutableStateOf("") }
    var notificationEnabled by remember { mutableStateOf(false) }
    var interval by remember { mutableIntStateOf(1) }
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("07:00") }
    var timerRunning by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(0) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            notificationEnabled = false
            store.setBool("notifications_enabled", false)
            mirrorWorkspaceRecord("study_workspace_notifications", "Android learning notifications",
                JSONObject().put("enabled", false).put("intervalHours", interval)
                    .put("quietHoursStart", quietStart).put("quietHoursEnd", quietEnd).toString())
            LearningTipScheduler.cancel(context)
        } else {
            LearningTipScheduler.update(context)
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val e = JSONArray(store.read("exams").ifBlank { "[]" })
            for (i in 0 until e.length()) {
                val o = e.getJSONObject(i); val tasksJson = o.optJSONArray("tasks") ?: JSONArray()
                val tasks = buildList { for (j in 0 until tasksJson.length()) { val t = tasksJson.getJSONObject(j); add(WorkspaceTask(t.optString("id"), t.optString("title"), t.optBoolean("done"), t.optString("notes"))) } }
                exams.add(WorkspaceExam(o.optString("id"), o.optString("title"), o.optString("subject"), o.optString("date"), o.optString("notes"), tasks))
            }
            val f = JSONArray(store.read("focus").ifBlank { "[]" })
            for (i in 0 until f.length()) { val o = f.getJSONObject(i); focus.add(WorkspaceFocus(o.optString("id"), o.optString("subject"), o.optString("topic"), o.optInt("minutes"), o.optInt("planned"), o.optInt("interruptions"), o.optString("date"))) }
            val foldersJson = JSONArray(store.read("folders").ifBlank { "[]" })
            for (i in 0 until foldersJson.length()) { val o = foldersJson.getJSONObject(i); folders.add(WorkspaceFolder(o.optString("id"), o.optString("name"))) }
            val links = JSONObject(store.read("bookmark_folder_links").ifBlank { "{}" })
            folderLinks = links.keys().asSequence().associate { it to links.optString(it) }
        }
        notificationEnabled = store.bool("notifications_enabled", false)
        interval = store.int("notification_interval", 1).coerceIn(1, 4)
        quietStart = store.read("quiet_start").ifBlank { "22:00" }
        quietEnd = store.read("quiet_end").ifBlank { "07:00" }
        reflectionWins = store.read("reflection_wins")
        reflectionChallenges = store.read("reflection_challenges")
        reflectionNext = store.read("reflection_next")
        loaded = true
        LearningTipScheduler.update(context)
    }

    LaunchedEffect(allItems, loaded) {
        if (!loaded) return@LaunchedEffect
        val examRecord = allItems.firstOrNull { it.kind == "study_workspace_exams" && it.deletedAt == null }
        if (store.read("exams").isBlank() && examRecord != null) {
            runCatching {
                val array = JSONArray(examRecord.body)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val tasksJson = o.optJSONArray("tasks") ?: JSONArray()
                    val tasks = buildList {
                        for (j in 0 until tasksJson.length()) {
                            val t = tasksJson.getJSONObject(j)
                            add(WorkspaceTask(t.optString("id"), t.optString("title"), t.optBoolean("done"), t.optString("notes")))
                        }
                    }
                    exams.add(WorkspaceExam(o.optString("id"), o.optString("title"), o.optString("subject"), o.optString("date"), o.optString("notes"), tasks))
                }
                store.write("exams", examRecord.body)
            }
        }
        val focusRecord = allItems.firstOrNull { it.kind == "study_workspace_focus" && it.deletedAt == null }
        if (store.read("focus").isBlank() && focusRecord != null) {
            runCatching {
                val array = JSONArray(focusRecord.body)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    focus.add(WorkspaceFocus(o.optString("id"), o.optString("subject"), o.optString("topic"), o.optInt("minutes"), o.optInt("planned"), o.optInt("interruptions"), o.optString("date")))
                }
                store.write("focus", focusRecord.body)
            }
        }
        val bookmarkRecord = allItems.firstOrNull { it.kind == "study_workspace_bookmarks" && it.deletedAt == null }
        if (store.read("folders").isBlank() && bookmarkRecord != null) {
            runCatching {
                val objectValue = JSONObject(bookmarkRecord.body)
                val folderArray = objectValue.optJSONArray("folders") ?: JSONArray()
                for (i in 0 until folderArray.length()) {
                    val folder = folderArray.getJSONObject(i)
                    folders.add(WorkspaceFolder(folder.optString("id"), folder.optString("name")))
                }
                store.write("folders", folderArray.toString())
                val links = objectValue.optJSONObject("links") ?: JSONObject()
                folderLinks = links.keys().asSequence().associate { it to links.optString(it) }
                store.write("bookmark_folder_links", links.toString())
            }
        }
        val reflectionRecord = allItems.firstOrNull { it.kind == "study_workspace_reflection" && it.deletedAt == null }
        if (reflectionRecord != null && store.read("reflection_wins").isBlank()) {
            runCatching {
                val value = JSONObject(reflectionRecord.body)
                reflectionWins = value.optString("wins")
                reflectionChallenges = value.optString("challenges")
                reflectionNext = value.optString("nextWeek")
                store.write("reflection_wins", reflectionWins)
                store.write("reflection_challenges", reflectionChallenges)
                store.write("reflection_next", reflectionNext)
            }
        }
    }

    fun persistExams() {
        val array = JSONArray()
        exams.forEach { e -> array.put(JSONObject().apply {
            put("id", e.id); put("title", e.title); put("subject", e.subject); put("date", e.date); put("notes", e.notes)
            put("tasks", JSONArray().also { a -> e.tasks.forEach { t -> a.put(JSONObject().put("id", t.id).put("title", t.title).put("done", t.done).put("notes", t.notes)) } })
        }) }
        store.write("exams", array.toString())
        mirrorWorkspaceRecord("study_workspace_exams", "Exam planner", array.toString())
    }
    fun persistFocus() {
        val array = JSONArray()
        focus.forEach { f -> array.put(JSONObject().put("id", f.id).put("subject", f.subject).put("topic", f.topic).put("minutes", f.minutes).put("planned", f.planned).put("interruptions", f.interruptions).put("date", f.date)) }
        store.write("focus", array.toString())
        mirrorWorkspaceRecord("study_workspace_focus", "Focus session log", array.toString())
    }
    fun persistFolders() {
        store.write("folders", JSONArray().also { a -> folders.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) } }.toString())
        val linksJson = JSONObject().also { o -> folderLinks.forEach { (id, folder) -> o.put(id.toString(), folder) } }
        store.write("bookmark_folder_links", linksJson.toString())
        val folderArray = JSONArray().also { a -> folders.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) } }
        mirrorWorkspaceRecord("study_workspace_bookmarks", "Bookmark folders", JSONObject().put("folders", folderArray).put("links", linksJson).toString())
    }
    fun persistReflection() {
        val body = JSONObject().put("wins", reflectionWins).put("challenges", reflectionChallenges).put("nextWeek", reflectionNext).toString()
        mirrorWorkspaceRecord("study_workspace_reflection", "Weekly reflection", body)
    }
    fun persistNotificationPreferences() {
        val body = JSONObject().put("enabled", notificationEnabled).put("intervalHours", interval)
            .put("quietHoursStart", quietStart).put("quietHoursEnd", quietEnd).toString()
        mirrorWorkspaceRecord("study_workspace_notifications", "Android learning notifications", body)
    }

    LaunchedEffect(timerRunning) {
        while (timerRunning) { delay(1000); timerSeconds += 1 }
    }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val minutesThisWeek = focus.filter { it.date >= SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 6L * 86_400_000L)) }.sumOf { it.minutes }
    val completedTopics = exams.sumOf { it.tasks.count(WorkspaceTask::done) }
    val totalTopics = exams.sumOf { it.tasks.size }
    val streak = remember(focus.toList()) {
        val days = focus.map { it.date }.toSet()
        var cursor = today; var count = 0
        while (cursor in days && count < 365) {
            count++
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cursor) ?: break
            cursor = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(parsed.time - 86_400_000L))
        }
        count
    }

    Column(Modifier.fillMaxSize()) {
        Text("STUDY WORKSPACE", color = WorkspaceGold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("Build a steadier study rhythm.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        ScrollableTabRow(selectedTabIndex = sections.indexOf(section), edgePadding = 0.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            sections.forEach { name -> Tab(selected = section == name, onClick = { section = name }, text = { Text(name) }) }
        }
        Spacer(Modifier.height(12.dp))
        when (section) {
            "Overview" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("STREAK", "$streak days", Modifier.weight(1f))
                        MetricTile("THIS WEEK", "$minutesThisWeek min", Modifier.weight(1f))
                        MetricTile("SYLLABUS", "$completedTopics/$totalTopics", Modifier.weight(1f))
                    }
                }
                item { WorkspaceSectionTitle("Upcoming exams") }
                if (exams.isEmpty()) item { WorkspaceEmpty("No exams planned", "Add an exam and turn its syllabus into a checklist.") }
                items(exams.sortedBy { it.date }.take(4), key = { it.id }) { exam ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(exam.title, fontWeight = FontWeight.SemiBold)
                            Text("${exam.subject} · ${exam.date}", color = WorkspaceMuted, fontSize = 12.sp)
                            Text("${exam.tasks.count { it.done }} of ${exam.tasks.size} syllabus items completed", color = WorkspaceMuted, fontSize = 12.sp)
                        }
                    }
                }
                item { WorkspaceSectionTitle("Recent focus") }
                if (focus.isEmpty()) item { WorkspaceEmpty("No focus sessions yet", "Log a session or use the timer in the Focus tab.") }
                items(focus.takeLast(5).reversed(), key = { it.id }) { FocusRow(it) }
            }
            "Exams" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Button(onClick = { showExamDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = WorkspaceGold, contentColor = androidx.compose.ui.graphics.Color.Black)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add exam") } }
                if (exams.isEmpty()) item { WorkspaceEmpty("Plan your next exam", "Add a date, then build a checklist from the syllabus.") }
                items(exams, key = { it.id }) { exam ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(exam.title, fontWeight = FontWeight.SemiBold); Text("${exam.subject} · ${exam.date}", color = WorkspaceMuted, fontSize = 12.sp) }
                                IconButton(onClick = { exams.remove(exam); persistExams() }) { Icon(Icons.Rounded.DeleteOutline, "Delete exam", tint = WorkspaceMuted) }
                            }
                            exam.tasks.forEach { task ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = task.done, onCheckedChange = { checked ->
                                        val index = exams.indexOfFirst { it.id == exam.id }
                                        if (index >= 0) exams[index] = exam.copy(tasks = exam.tasks.map { if (it.id == task.id) it.copy(done = checked) else it })
                                        persistExams()
                                    })
                                    Text(task.title, modifier = Modifier.weight(1f), color = if (task.done) WorkspaceMuted else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            var newTask by remember(exam.id) { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(newTask, { newTask = it }, modifier = Modifier.weight(1f), placeholder = { Text("Add syllabus topic") }, singleLine = true)
                                IconButton(onClick = { if (newTask.isNotBlank()) { val index = exams.indexOfFirst { it.id == exam.id }; if (index >= 0) exams[index] = exam.copy(tasks = exam.tasks + WorkspaceTask(UUID.randomUUID().toString(), newTask.trim())); newTask = ""; persistExams() } }) { Icon(Icons.Rounded.Add, "Add topic", tint = WorkspaceGold) }
                            }
                        }
                    }
                }
            }
            "Focus" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FOCUS TIMER", color = WorkspaceMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                            Text(String.format(Locale.US, "%02d:%02d", timerSeconds / 60, timerSeconds % 60), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { timerRunning = !timerRunning }, colors = ButtonDefaults.buttonColors(containerColor = WorkspaceGold, contentColor = androidx.compose.ui.graphics.Color.Black)) { Text(if (timerRunning) "Pause" else "Start") }
                                OutlinedButton(onClick = { timerRunning = false; if (timerSeconds >= 60) { focus.add(WorkspaceFocus(UUID.randomUUID().toString(), "Focus", "Timer session", timerSeconds / 60, timerSeconds / 60, 0, today)); persistFocus(); vm.add(null, "study_session", "Focused study", body = "Workspace timer session.", tags = "minutes:${timerSeconds / 60}") }; timerSeconds = 0 }) { Text("Finish") }
                            }
                        }
                    }
                }
                item { Button(onClick = { showFocusDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Log a completed session") } }
                item { WorkspaceSectionTitle("Session history") }
                if (focus.isEmpty()) item { WorkspaceEmpty("Your focus history will appear here", "Track time spent on each subject and topic.") }
                items(focus.reversed(), key = { it.id }) { FocusRow(it) }
            }
            "Bookmarks" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = { showFolderDialog = true }) { Icon(Icons.Rounded.CreateNewFolder, null); Text("New folder") } } }
                items(folders, key = { it.id }) { folder ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = WorkspaceGold)
                        Text(folder.name, Modifier.weight(1f))
                        TextButton(onClick = { folders.remove(folder); folderLinks = folderLinks.filterValues { it != folder.id }; persistFolders() }) { Text("Remove") }
                    }
                }
                item { WorkspaceSectionTitle("Saved notes") }
                if (bookmarks.isEmpty()) item { WorkspaceEmpty("No saved notes", "Bookmark notes in your library; they will appear here.") }
                items(bookmarks, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(item.title, fontWeight = FontWeight.Medium)
                            Text(item.kind.uppercase(Locale.ROOT), color = WorkspaceMuted, fontSize = 10.sp)
                            var expanded by remember(item.id) { mutableStateOf(false) }
                            TextButton(onClick = { expanded = true }) { Text("Organise in folder") }
                            if (expanded) Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { folderLinks = folderLinks - bookmarkKey(item); persistFolders(); expanded = false }) { Text("Unfiled") }
                                folders.forEach { folder -> TextButton(onClick = { folderLinks = folderLinks + (ensureBookmarkKey(item) to folder.id); persistFolders(); expanded = false }) { Text(folder.name) } }
                            }
                            folderLinks[bookmarkKey(item)]?.let { id -> Text(folders.firstOrNull { it.id == id }?.name ?: "Folder", color = WorkspaceGold, fontSize = 11.sp) }
                        }
                    }
                }
            }
            "Reflect" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("WEEKLY REFLECTION", color = WorkspaceGold, fontSize = 10.sp, letterSpacing = 2.sp)
                OutlinedTextField(reflectionWins, { reflectionWins = it; store.write("reflection_wins", it); persistReflection() }, modifier = Modifier.fillMaxWidth(), label = { Text("What went well?") }, minLines = 3)
                OutlinedTextField(reflectionChallenges, { reflectionChallenges = it; store.write("reflection_challenges", it); persistReflection() }, modifier = Modifier.fillMaxWidth(), label = { Text("What was challenging?") }, minLines = 3)
                OutlinedTextField(reflectionNext, { reflectionNext = it; store.write("reflection_next", it); persistReflection() }, modifier = Modifier.fillMaxWidth(), label = { Text("Priorities for next week") }, minLines = 3)
                Text("Saved automatically on this device.", color = WorkspaceMuted, fontSize = 12.sp)
            }
            "Reminders" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ANDROID LEARNING REMINDERS", color = WorkspaceGold, fontSize = 10.sp, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Learning notifications", fontWeight = FontWeight.SemiBold); Text("Android only · saved preferences", color = WorkspaceMuted, fontSize = 12.sp) }; Switch(notificationEnabled, {
                    notificationEnabled = it
                    store.setBool("notifications_enabled", it)
                    persistNotificationPreferences()
                    if (it && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else LearningTipScheduler.update(context)
                }) }
                Text("Reminder interval")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 2, 3, 4).forEach { h -> FilterChip(selected = interval == h, onClick = { interval = h; store.setInt("notification_interval", h); persistNotificationPreferences(); LearningTipScheduler.update(context) }, label = { Text("$h h") }) } }
                OutlinedTextField(quietStart, { quietStart = it; store.write("quiet_start", it); persistNotificationPreferences() }, label = { Text("Quiet hours start (HH:mm)") }, singleLine = true)
                OutlinedTextField(quietEnd, { quietEnd = it; store.write("quiet_end", it); persistNotificationPreferences() }, label = { Text("Quiet hours end (HH:mm)") }, singleLine = true)
                Text("Scribe uses your existing signed-in cloud session to generate short tips from notebook topics. WorkManager checks approximately every selected interval; Android may delay delivery because background work is not exact to the minute. Quiet hours are respected and a topic is not repeated on the same day.", color = WorkspaceMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }

    if (showExamDialog) AlertDialog(onDismissRequest = { showExamDialog = false }, title = { Text("Plan an exam") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(examTitle, { examTitle = it }, label = { Text("Exam name") }, singleLine = true)
            OutlinedTextField(examSubject, { examSubject = it }, label = { Text("Subject") }, singleLine = true)
            OutlinedTextField(examDate, { examDate = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
        } }, confirmButton = { TextButton(onClick = { if (examTitle.isNotBlank() && examSubject.isNotBlank() && examDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { exams.add(WorkspaceExam(UUID.randomUUID().toString(), examTitle.trim(), examSubject.trim(), examDate, "", emptyList())); persistExams(); examTitle = ""; examSubject = ""; examDate = ""; showExamDialog = false } }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showExamDialog = false }) { Text("Cancel") } })
    if (showFocusDialog) AlertDialog(onDismissRequest = { showFocusDialog = false }, title = { Text("Log focus session") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(focusSubject, { focusSubject = it }, label = { Text("Subject") }, singleLine = true)
            OutlinedTextField(focusTopic, { focusTopic = it }, label = { Text("Topic") }, singleLine = true)
            OutlinedTextField(focusMinutes, { focusMinutes = it.filter(Char::isDigit).take(3) }, label = { Text("Minutes") }, singleLine = true)
        } }, confirmButton = { TextButton(onClick = { val m = focusMinutes.toIntOrNull(); if (m != null && m in 1..240 && focusSubject.isNotBlank()) { focus.add(WorkspaceFocus(UUID.randomUUID().toString(), focusSubject.trim(), focusTopic.trim(), m, m, 0, today)); persistFocus(); vm.add(null, "study_session", focusSubject.trim(), body = "Workspace focus session.", tags = "minutes:$m;topic:${focusTopic.trim()}"); focusSubject = ""; focusTopic = ""; focusMinutes = "25"; showFocusDialog = false } }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showFocusDialog = false }) { Text("Cancel") } })
    if (showFolderDialog) AlertDialog(onDismissRequest = { showFolderDialog = false }, title = { Text("New bookmark folder") },
        text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("Folder name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (folderName.isNotBlank()) { folders.add(WorkspaceFolder(UUID.randomUUID().toString(), folderName.trim())); persistFolders(); folderName = ""; showFolderDialog = false } }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") } })
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) { Text(label, color = WorkspaceMuted, fontSize = 9.sp, letterSpacing = 1.sp); Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
    }
}
@Composable private fun WorkspaceSectionTitle(value: String) { Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
@Composable private fun WorkspaceEmpty(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.School, null, tint = WorkspaceMuted, modifier = Modifier.size(28.dp))
        Text(title, fontWeight = FontWeight.Medium)
        Text(detail, color = WorkspaceMuted, fontSize = 12.sp)
    }
}
@Composable private fun FocusRow(session: WorkspaceFocus) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Timer, null, tint = WorkspaceGold)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(session.subject, fontWeight = FontWeight.Medium); Text(session.topic.ifBlank { "Study session" }, color = WorkspaceMuted, fontSize = 12.sp) }
        Text("${session.minutes} min", color = WorkspaceGold, fontWeight = FontWeight.SemiBold)
    }
}
