package com.mrjdm.scribe

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjdm.scribe.data.*
import com.mrjdm.scribe.ui.ScribeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingDocument = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent { ScribeTheme { ScribeApp(initialDocumentUri = incomingDocument) } }
    }
}

private val Gold = Color(0xFFD5B98C)
private val Muted = Color(0xFF96938D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScribeApp(initialDocumentUri: Uri? = null, vm: ScribeViewModel = viewModel()) {
    val context = LocalContext.current
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val scope = rememberCoroutineScope()
    var stack by remember { mutableStateOf(listOf<ScribeItem>()) }
    var tab by remember { mutableStateOf("Library") }
    var showCreate by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf("course") }
    var editItem by remember { mutableStateOf<ScribeItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var quickCapture by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf<ScribeItem?>(null) }
    var showTrash by remember { mutableStateOf(false) }
    var integrityReport by remember { mutableStateOf<String?>(null) }
    var incomingPdf by remember { mutableStateOf<ScribeItem?>(null) }
    var incomingDocumentError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialDocumentUri) {
        val uri = initialDocumentUri ?: return@LaunchedEffect
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime !in setOf("application/pdf", "application/x-pdf")) {
            incomingDocumentError = "Scribe currently opens PDF documents from other apps. This file is $mime."
            return@LaunchedEffect
        }
        val copied = withContext(Dispatchers.IO) {
            runCatching {
                val directory = File(context.cacheDir, "incoming-documents").apply { mkdirs() }
                val target = File(directory, "shared_${System.currentTimeMillis()}.pdf")
                val input = context.contentResolver.openInputStream(uri) ?: error("The shared document is no longer accessible.")
                input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
                check(target.isFile && target.length() > 0L) { "The shared PDF was empty." }
                target
            }.getOrNull()
        }
        if (copied != null) {
            incomingPdf = ScribeItem(
                id = -1L, kind = "block",
                title = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.ifBlank { "Shared PDF" } ?: "Shared PDF",
                uri = copied.absolutePath, tags = "type:pdf"
            )
        } else {
            incomingDocumentError = "Scribe couldn't read this shared PDF. Try downloading it in WhatsApp first, then open it again."
        }
    }
    val current = stack.lastOrNull()
    val children by vm.children(current?.id).collectAsState(initial = emptyList())
    val bookmarks by vm.bookmarks.collectAsState(initial = emptyList())
    val trash by vm.trash.collectAsState(initial = emptyList())
    val searchResults by vm.search(searchQuery).collectAsState(initial = emptyList())

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && current?.kind == "page") {
            scope.launch(Dispatchers.IO) {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Attachment"
                val ext = context.contentResolver.getType(uri).orEmpty()
                if (!ext.startsWith("image/") && ext != "application/pdf") return@launch
                val file = File(context.filesDir, "attachments").apply { mkdirs() }
                val target = File(file, "${System.currentTimeMillis()}_${name.substringAfterLast(':').replace('/', '_')}")
                val copied = runCatching {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected file")
                    input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
                    target.isFile && target.length() > 0L
                }.getOrDefault(false)
                if (!copied) return@launch
                val kind = when {
                    ext.startsWith("image/") -> "image"
                    ext == "application/pdf" -> "pdf"
                    else -> "file"
                }
                vm.add(current.id, "block", name, body = "Attached file", uri = target.absolutePath, tags = "type:$kind")
            }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val all = vm.all()
                val array = JSONArray()
                all.forEach { item ->
                    val row = JSONObject().apply {
                        put("id", item.id); put("parentId", item.parentId ?: JSONObject.NULL); put("kind", item.kind)
                        put("title", item.title); put("body", item.body); put("uri", item.uri); put("tags", item.tags)
                        put("bookmarked", item.bookmarked); put("position", item.position); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
                        put("deletedAt", item.deletedAt ?: JSONObject.NULL)
                    }
                    if (item.uri.isNotBlank()) {
                        val attachment = File(item.uri)
                        check(attachment.isFile) { "Cannot export backup: missing attachment ${attachment.name}. Reattach it before exporting." }
                        val declaredType = item.tags.substringAfter("type:", "").substringBefore(';').substringBefore(',')
                        check(declaredType == "image" || declaredType == "pdf") { "Unsupported attachment type in ${attachment.name}." }
                        row.put("attachmentBase64", Base64.encodeToString(attachment.readBytes(), Base64.NO_WRAP))
                    }
                    val history = JSONArray()
                    vm.snapshots(item.id).first().forEach { snapshot ->
                        history.put(JSONObject().apply {
                            put("title", snapshot.title)
                            put("body", snapshot.body)
                            put("savedAt", snapshot.savedAt)
                        })
                    }
                    row.put("history", history)
                    array.put(row)
                }
                val output = context.contentResolver.openOutputStream(uri) ?: error("Could not open the backup destination.")
                output.bufferedWriter().use { it.write(array.toString(2)) }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { integrityReport = "Backup export failed. No backup was written.\n\n${error.localizedMessage ?: "Unknown error"}" }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read the selected backup.")
                val array = JSONArray(raw)
                val ids = (0 until array.length()).map { array.getJSONObject(it).optLong("id") }
                require(ids.all { it > 0 } && ids.toSet().size == ids.size) { "Backup contains missing or duplicate item IDs." }
                val idSet = ids.toSet()
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    require(row.isNull("parentId") || row.optLong("parentId") in idSet) { "Backup contains a broken notebook parent reference." }
                }
                val restoredSnapshots = mutableListOf<NoteSnapshot>()
                val restored = buildList {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val encodedAttachment = o.optString("attachmentBase64")
                        val originalUri = o.optString("uri")
                        val originalExtension = File(originalUri).extension.lowercase(Locale.ROOT)
                        val declaredType = o.optString("tags").split(';', ',').firstOrNull { it.trim().startsWith("type:") }
                            ?.substringAfter(':')?.trim().orEmpty()
                        val isPhoto = declaredType == "image" || originalExtension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tif", "tiff", "avif")
                        val isPdf = declaredType == "pdf" || originalExtension == "pdf"
                        val hasAttachmentMarker = originalUri.isNotBlank() || declaredType == "image" || declaredType == "pdf" || encodedAttachment.isNotBlank()
                        var restoredUri = ""
                        if (hasAttachmentMarker) {
                            require(encodedAttachment.isNotBlank() && (isPhoto || isPdf)) {
                                "Backup is missing valid photo/PDF data for item ${o.optLong("id")}. Export a fresh full backup from the source device."
                            }
                            val bytes = Base64.decode(encodedAttachment, Base64.DEFAULT)
                            require(bytes.isNotEmpty()) { "Backup contains an empty attachment." }
                            val directory = File(context.filesDir, "attachments").apply { mkdirs() }
                            val suffix = originalExtension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }?.let { ".$it" } ?: if (isPdf) ".pdf" else ".jpg"
                            val target = File(directory, "restored_${System.currentTimeMillis()}_${o.optLong("id")}$suffix")
                            target.writeBytes(bytes)
                            restoredUri = target.absolutePath
                        }
                        val itemId = o.optLong("id")
                        val history = o.optJSONArray("history") ?: JSONArray()
                        for (h in 0 until history.length()) {
                            val snap = history.optJSONObject(h) ?: continue
                            restoredSnapshots += NoteSnapshot(itemId = itemId, title = snap.optString("title"),
                                body = snap.optString("body"), savedAt = snap.optLong("savedAt", System.currentTimeMillis()))
                        }
                        add(ScribeItem(id = itemId, parentId = if (o.isNull("parentId")) null else o.optLong("parentId"),
                            kind = o.optString("kind"), title = o.optString("title"), body = o.optString("body"),
                            uri = restoredUri, tags = o.optString("tags"), bookmarked = o.optBoolean("bookmarked"),
                            position = o.optInt("position"), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
                            deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt")))
                    }
                }
                vm.restore(restored, restoredSnapshots)
                withContext(Dispatchers.Main) { integrityReport = "Backup restored successfully.\n\nImported ${restored.size} notebook items and ${restoredSnapshots.size} history entries." }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { integrityReport = "Backup restore failed. Your existing library was not replaced.\n\n${error.localizedMessage ?: "Unknown error"}" }
            }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && current != null) scope.launch(Dispatchers.IO) {
            val pageItems = vm.children(current.id).first()
            exportPdf(context, uri, current.title, pageItems)
        }
    }

    BackHandler(enabled = true) {
        when {
            stack.isNotEmpty() -> stack = stack.dropLast(1)
            tab != "Library" -> tab = "Library"
            searchQuery.isNotBlank() -> searchQuery = ""
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (tab != "Library" || current != null) {
                TopAppBar(
                    title = { Text(current?.title ?: tab, fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { tab = "Library"; stack = emptyList() }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search all notes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        floatingActionButton = {
            if (tab == "Library" && current?.kind != "page") {
                FloatingActionButton(onClick = {
                    createKind = when(current?.kind) { null -> "course"; "course" -> "chapter"; "chapter" -> "topic"; "topic" -> "page"; else -> "course" }
                    showCreate = true
                }, containerColor = Gold, contentColor = Color(0xFF211B12)) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add")
                }
            }
        },
        bottomBar = {
            if (!isWide) NavigationBar(containerColor = Color(0xFF17181B), contentColor = Gold) {
                listOf("Library", "Saved", "Study", "Ask Notes", "Tools", "Settings").forEach { label ->
                    val icon = when(label) { "Library" -> Icons.Rounded.AutoStories; "Saved" -> Icons.Rounded.Bookmark; "Tools" -> Icons.Rounded.Functions; "Study" -> Icons.Rounded.School; "Ask Notes" -> Icons.Rounded.Search; else -> Icons.Rounded.Settings }
                    NavigationBarItem(selected = tab == label, onClick = { tab = label; stack = emptyList() },
                        icon = { Icon(icon, null) }, label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Color(0xFF33302A), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).animateContentSize()) {
            if (isWide) {
                NavigationRail(containerColor = Color(0xFF17181B), contentColor = Gold) {
                    Spacer(Modifier.height(12.dp))
                    listOf("Library", "Saved", "Study", "Ask Notes", "Tools", "Settings").forEach { label ->
                        val icon = when(label) { "Library" -> Icons.Rounded.AutoStories; "Saved" -> Icons.Rounded.Bookmark; "Tools" -> Icons.Rounded.Functions; "Study" -> Icons.Rounded.School; "Ask Notes" -> Icons.Rounded.Search; else -> Icons.Rounded.Settings }
                        NavigationRailItem(selected = tab == label, onClick = { tab = label; stack = emptyList() }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (isWide) 28.dp else 20.dp).animateContentSize()) {
            Spacer(Modifier.height(18.dp))
            when (tab) {
                "Library" -> {
                    if (current?.kind == "page") {
                        PageEditor(current, vm, onBack = { stack = stack.dropLast(1) }, onHistory = { showHistory = current }, onOpenTopic = { linked -> stack = listOf(linked) }, onAttach = {
                            attachmentPicker.launch(arrayOf("image/*", "application/pdf"))
                        }, onExport = { pdfLauncher.launch("${current.title}.pdf") })
                    } else if (current != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { stack = stack.dropLast(1) }) { Icon(Icons.Rounded.ArrowBack, null) }
                            Column(Modifier.weight(1f)) {
                                Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Text(current.kind.uppercase(Locale.getDefault()), color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                            }
                            IconButton(onClick = { editItem = current }) { Icon(Icons.Rounded.Edit, null, tint = Muted) }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (current.kind == "topic") {
                            TopicWordGoalCard(current, children, vm)
                            Spacer(Modifier.height(12.dp))
                            Text("PAGES", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text("YOUR ${current.kind.uppercase()}S", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.height(10.dp))
                        }
                        ItemList(children, onClick = { stack = stack + it }, onBookmark = vm::toggleBookmark, onEdit = { editItem = it }, onDelete = vm::removeTree, onDuplicate = vm::duplicate, onMove = vm::move, onMergePage = vm::mergePageWithNext, onSplitPage = vm::splitPage)
                        if (children.isEmpty()) EmptyState(if (current.kind == "topic") "No pages yet" else "A quiet space to begin", "Use + to add your first ${if (current.kind == "topic") "page" else "item"}.")
                    } else {
                        Text("SCRIBE", color = Gold, fontSize = 11.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Your knowledge,\nbeautifully organised.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp)
                        Spacer(Modifier.height(18.dp))
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search everything…", color = Muted) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, null) } },
                            singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors())
                        if (searchQuery.isNotBlank()) {
                            Spacer(Modifier.height(16.dp)); Text("SEARCH RESULTS", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            LazyColumn { items(searchResults, key = { it.id }) { item -> SearchRow(item) {
                                scope.launch {
                                    if (item.kind == "block" && item.parentId != null) {
                                        val parent = vm.get(item.parentId)
                                        stack = if (parent != null) listOf(parent) else listOf(item)
                                    } else stack = listOf(item)
                                }
                            } } }
                        } else {
                            Spacer(Modifier.height(22.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Your courses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("${children.size} NOTEBOOKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            ItemList(children.filter { it.kind == "course" }, onClick = { stack = listOf(it) }, onBookmark = vm::toggleBookmark, onEdit = { editItem = it }, onDelete = vm::removeTree)
                            if (children.isEmpty()) EmptyState("Start your library", "Create a course to begin building your knowledge vault.")
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { quickCapture = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.width(8.dp)); Text("Quick capture a note")
                            }
                        }
                    }
                }
                "Saved" -> {
                    ScreenHeading("Saved", "Your bookmarked material")
                    ItemList(bookmarks, onClick = { stack = listOf(it) }, onBookmark = vm::toggleBookmark, onEdit = { editItem = it }, onDelete = vm::removeTree)
                    if (bookmarks.isEmpty()) EmptyState("Nothing saved yet", "Bookmark a page or note to keep it close.")
                }
                "Study" -> FlashcardsScreen(vm)
                "Ask Notes" -> LocalNotesAssistantScreen(vm)
                "Tools" -> AcademicToolsHub(vm)
                else -> {
                    ScreenHeading("Settings", "Your Scribe workspace")
                    SettingsRow(Icons.Rounded.CloudUpload, "Export full backup", "Save courses, notes and organisation as JSON") { backupLauncher.launch("Scribe-backup.json") }
                    SettingsRow(Icons.Rounded.CloudDownload, "Restore backup", "Replace this library from a Scribe backup") { restoreLauncher.launch(arrayOf("application/json", "text/*")) }
                    SettingsRow(Icons.Rounded.DeleteSweep, "Recently deleted", "Restore or permanently remove deleted notes") { showTrash = true }
                    SettingsRow(Icons.Rounded.VerifiedUser, "Check library integrity", "Find missing attachments and duplicate entries") {
                        scope.launch {
                            val all = vm.all()
                            val missing = all.count { it.uri.isNotBlank() && !File(it.uri).isFile }
                            val duplicates = all.filter { it.deletedAt == null && it.title.isNotBlank() }
                                .groupBy { "${it.parentId}|${it.kind}|${it.title.trim().lowercase(Locale.ROOT)}" }
                                .values.sumOf { (it.size - 1).coerceAtLeast(0) }
                            integrityReport = "Checked ${all.size} records.\n\nMissing attachment files: $missing\nPossible duplicate entries: $duplicates\n\nNo data was changed."
                        }
                    }
                    SettingsRow(Icons.Rounded.Info, "About Scribe", "Offline-first academic notebook · v1.1") {}
                    Text("All notes are stored locally on this device. Keep regular backups to protect your work.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 18.dp))
                }
            }
            }
        }
    }

    incomingPdf?.let { shared -> PdfViewerDialog(shared, onDismiss = { incomingPdf = null }) }
    incomingDocumentError?.let { message ->
        AlertDialog(
            onDismissRequest = { incomingDocumentError = null },
            title = { Text("Couldn't open document") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { incomingDocumentError = null }) { Text("OK", color = Gold) } },
            containerColor = Color(0xFF202125)
        )
    }

    integrityReport?.let { report ->
        AlertDialog(onDismissRequest = { integrityReport = null }, title = { Text("Library health check") },
            text = { Text(report, lineHeight = 21.sp) },
            confirmButton = { TextButton(onClick = { integrityReport = null }) { Text("Done", color = Gold) } },
            containerColor = Color(0xFF202125))
    }

    if (showCreate) CreateDialog(createKind, onDismiss = { showCreate = false }) { name, tags ->
        vm.add(current?.id, createKind, name, tags = tags) { id ->
            scope.launch { val item = vm.get(id); if (item != null) stack = stack + item }
        }
        showCreate = false
    }
    if (quickCapture) CreateDialog("topic", onDismiss = { quickCapture = false }, title = "Quick capture") { name, body ->
        val existingCourse = children.firstOrNull { it.kind == "course" && it.title == "Quick Notes" }
        fun addToInbox(courseId: Long) {
            scope.launch {
                val inbox = vm.children(courseId).first().firstOrNull { it.kind == "chapter" && it.title == "Inbox" }
                if (inbox != null) {
                    vm.add(inbox.id, "topic", name, body = body, tags = "quick-capture")
                } else {
                    vm.add(courseId, "chapter", "Inbox") { chapterId ->
                        vm.add(chapterId, "topic", name, body = body, tags = "quick-capture")
                    }
                }
            }
        }
        if (existingCourse != null) addToInbox(existingCourse.id)
        else vm.add(null, "course", "Quick Notes") { courseId -> addToInbox(courseId) }
        quickCapture = false
    }
    editItem?.let { item ->
        EditDialog(item, onDismiss = { editItem = null }, onSave = { title, body, tags ->
            vm.update(item, title = title, body = body, tags = tags); editItem = null
        })
    }
    showHistory?.let { item ->
        val history by vm.snapshots(item.id).collectAsState(initial = emptyList())
        AlertDialog(onDismissRequest = { showHistory = null }, title = { Text("Version history") },
            text = { if (history.isEmpty()) Text("Earlier versions appear here as you edit text blocks.") else LazyColumn { items(history) { snap ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(snap.savedAt)), color = Gold)
                    Text(snap.title, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(snap.body, maxLines = 3, color = Muted)
                    TextButton(onClick = { vm.update(item, title = snap.title, body = snap.body); showHistory = null }) { Text("Restore this version") }
                }
            } } }, confirmButton = { TextButton(onClick = { showHistory = null }) { Text("Close") } })
    }
    if (showTrash) AlertDialog(onDismissRequest = { showTrash = false }, title = { Text("Recently deleted") },
        text = {
            if (trash.isEmpty()) Text("Your trash is empty.")
            else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(trash, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Medium)
                            Text(item.kind, color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { vm.restoreTree(item) }) { Text("Restore", color = Gold) }
                        IconButton(onClick = { vm.deleteForever(item) }) { Icon(Icons.Rounded.DeleteForever, null, tint = Muted) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showTrash = false }) { Text("Close") } })
}

@Composable
private fun TopicWordGoalCard(topic: ScribeItem, pages: List<ScribeItem>, vm: ScribeViewModel) {
    var allItems by remember(topic.id) { mutableStateOf(emptyList<ScribeItem>()) }
    var goalDraft by remember(topic.id) {
        mutableStateOf(topic.tags.split(';', ',').firstOrNull { it.trim().startsWith("wordGoal:") }
            ?.substringAfter(':') ?: "500")
    }
    var savedGoal by remember(topic.id) {
        mutableIntStateOf(topic.tags.split(';', ',').firstOrNull { it.trim().startsWith("wordGoal:") }
            ?.substringAfter(':')?.toIntOrNull()?.coerceAtLeast(0) ?: 0)
    }
    LaunchedEffect(topic.id, pages.map { it.id }) {
        allItems = vm.all()
    }
    val words = TopicWordCount.count(allItems, topic.id)
    val goal = savedGoal
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C20)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TOPIC WORD GOAL", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(if (goal > 0) "$words / $goal words" else "$words words", color = Gold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(goalDraft, { goalDraft = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.width(105.dp), singleLine = true, label = { Text("Target") }, colors = fieldColors())
                TextButton(onClick = {
                    val target = goalDraft.toIntOrNull()?.coerceIn(0, 100_000) ?: 0
                    val tags = topic.tags.split(';', ',').map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("wordGoal:") }
                    vm.update(topic, tags = (tags + if (target > 0) listOf("wordGoal:$target") else emptyList()).joinToString(";"))
                    savedGoal = target
                    goalDraft = if (target > 0) target.toString() else "500"
                }) { Text("Save", color = Gold) }
            }
            if (goal > 0) {
                LinearProgressIndicator(
                    progress = { (words.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = Color(0xFF38352F)
                )
                Text(if (words >= goal) "Word goal completed" else "${goal - words} words remaining",
                    color = Muted, fontSize = 11.sp)
                TextButton(onClick = {
                    val tags = topic.tags.split(';', ',').map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("wordGoal:") }
                    vm.update(topic, tags = tags.joinToString(";"))
                    savedGoal = 0
                    goalDraft = "500"
                }) { Text("Disable goal", color = Muted, fontSize = 11.sp) }
            } else {
                Text("Track writing progress across every page in this topic.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PageEditor(page: ScribeItem, vm: ScribeViewModel, onBack: () -> Unit, onHistory: () -> Unit, onOpenTopic: (ScribeItem) -> Unit, onAttach: () -> Unit, onExport: () -> Unit) {
    val blocks by vm.children(page.id).collectAsState(initial = emptyList())
    val pageInsights = remember(blocks) { PageInsights.calculate(blocks) }
    val topics by vm.kind("topic").collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var showLinkPicker by remember { mutableStateOf(false) }
    var editBlock by remember { mutableStateOf<ScribeItem?>(null) }
    var previewPdf by remember { mutableStateOf<ScribeItem?>(null) }
    var pageTitle by remember(page.id) { mutableStateOf(page.title) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            Column(Modifier.weight(1f)) {
                Text("NOTE PAGE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text(page.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, null, tint = Muted) }
            IconButton(onClick = onExport) { Icon(Icons.Rounded.PictureAsPdf, null, tint = Muted) }
        }
        OutlinedTextField(value = pageTitle, onValueChange = { pageTitle = it; vm.update(page, title = it) },
            modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            placeholder = { Text("Page title") }, singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1C20)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${pageInsights.wordCount} words", color = Gold, fontWeight = FontWeight.SemiBold)
                    Text("Writing", color = Muted, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (pageInsights.estimatedReadingMinutes == 0) "—" else "${pageInsights.estimatedReadingMinutes} min", color = Gold, fontWeight = FontWeight.SemiBold)
                    Text("Read time", color = Muted, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(pageInsights.attachmentCount.toString(), color = Gold, fontWeight = FontWeight.SemiBold)
                    Text("Attachments", color = Muted, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("${blocks.size} CONTENT BLOCKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showLinkPicker = true }) { Icon(Icons.Rounded.Link, null); Text("Link topic") }
                TextButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, null); Text("Add content") }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(blocks, key = { it.id }) { block ->
                BlockCard(block, onClick = {
                    val blockType = block.tags.substringAfter("type:", "").substringBefore(",")
                    if (blockType == "pdf") {
                        previewPdf = block
                    } else if (block.tags.contains("type:crosslink")) {
                        val targetId = block.tags.substringAfter("target:", "").substringBefore(",").toLongOrNull()
                        scope.launch { targetId?.let { vm.get(it)?.let(onOpenTopic) } }
                    } else editBlock = block
                }, onBookmark = { vm.toggleBookmark(block) }, onDelete = { vm.removeTree(block) }, onMove = { direction -> vm.move(block, direction) })
            }
            if (blocks.isEmpty()) item { EmptyState("A blank page", "Add rich text, equations, diagrams, images, PDFs, tables and more.") }
            item {
                OutlinedButton(onClick = onAttach, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.AttachFile, null); Spacer(Modifier.width(8.dp)); Text("Attach photo or PDF")
                }
            }
        }
    }
    if (showLinkPicker) AlertDialog(onDismissRequest = { showLinkPicker = false }, title = { Text("Link another topic") },
        text = {
            if (topics.isEmpty()) Text("Create a topic first, then you can link it here.")
            else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(topics, key = { it.id }) { topic ->
                    ListItem(headlineContent = { Text(topic.title) }, supportingContent = { Text("Open linked topic", color = Muted) },
                        leadingContent = { Icon(Icons.Rounded.Description, null, tint = Gold) },
                        modifier = Modifier.clickable {
                            vm.add(page.id, "block", topic.title, body = "topic-ref:${topic.id}", tags = "type:crosslink;target:${topic.id}")
                            showLinkPicker = false
                        }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
            }
        }, confirmButton = { TextButton(onClick = { showLinkPicker = false }) { Text("Close") } })
    if (showAdd) AddBlockDialog(onDismiss = { showAdd = false }) { type, title, body, tags ->
        vm.add(page.id, "block", title.ifBlank { type.replaceFirstChar { it.uppercase() } }, body = body, tags = "type:$type,$tags")
        showAdd = false
    }
    editBlock?.let { block -> EditDialog(block, onDismiss = { editBlock = null }, onSave = { title, body, tags ->
        vm.update(block, title = title, body = body, tags = tags); editBlock = null
    }) }
    previewPdf?.let { pdf -> PdfViewerDialog(pdf, onDismiss = { previewPdf = null }) }
}

@Composable
private fun BlockCard(item: ScribeItem, onClick: () -> Unit, onBookmark: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit = {}) {
    val type = item.tags.substringAfter("type:", "").substringBefore(",")
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    val preview = remember(item.uri, type) { if (type == "image" && item.uri.isNotBlank()) runCatching { BitmapFactory.decodeFile(item.uri)?.asImageBitmap() }.getOrNull() else null }
    val icon = when(type) { "equation" -> Icons.Rounded.Functions; "image" -> Icons.Rounded.Image; "pdf" -> Icons.Rounded.PictureAsPdf; "code" -> Icons.Rounded.Code; "table" -> Icons.Rounded.TableChart; "heading" -> Icons.Rounded.Title; "link" -> Icons.Rounded.Link; else -> Icons.Rounded.Notes }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191A1D)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Gold, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text(item.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onBookmark) { Icon(if (item.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, tint = if (item.bookmarked) Gold else Muted) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Muted) }
                    DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Move up") }, onClick = { onMove(-1); menuExpanded = false })
                        DropdownMenuItem(text = { Text("Move down") }, onClick = { onMove(1); menuExpanded = false })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(); menuExpanded = false })
                    }
                }
            }
            if (preview != null) Image(bitmap = preview, contentDescription = item.title, modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp))
            if (item.uri.isNotBlank() && preview == null) Text(File(item.uri).name, color = Gold, fontSize = 12.sp)
            if (item.body.isNotBlank()) {
                if (type == "equation") {
                    MathJaxPreview(item.body, Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 150.dp))
                } else if (type == "code") {
                    Text(item.body, color = Color(0xFFE0CDAA), fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 8)
                } else {
                    Text(styledBody(item.body), color = Muted, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 8)
                }
            }
        }
    }
}

@Composable
private fun ItemList(items: List<ScribeItem>, onClick: (ScribeItem) -> Unit, onBookmark: (ScribeItem) -> Unit, onEdit: (ScribeItem) -> Unit, onDelete: (ScribeItem) -> Unit, onDuplicate: (ScribeItem) -> Unit = {}, onMove: (ScribeItem, Int) -> Unit = { _, _ -> }, onMergePage: (ScribeItem) -> Unit = {}, onSplitPage: (ScribeItem) -> Unit = {}) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
        items(items, key = { it.id }) { item ->
            var menuExpanded by remember(item.id) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191A1D)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onClick(item) }) {
                Row(Modifier.padding(start = 15.dp, end = 5.dp, top = 13.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFF292721), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(when(item.kind) { "course" -> Icons.Rounded.MenuBook; "chapter" -> Icons.Rounded.Folder; "topic" -> Icons.Rounded.Description; "page" -> Icons.Rounded.Article; else -> Icons.Rounded.Notes }, null, tint = Gold)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${item.kind.replaceFirstChar { it.uppercase() }} · ${if (item.body.isNotBlank()) item.body.take(55) else "Open notebook"}", color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = { onBookmark(item) }) { Icon(if (item.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, tint = if (item.bookmarked) Gold else Muted) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Muted) }
                        DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { onDuplicate(item); menuExpanded = false })
                            DropdownMenuItem(text = { Text("Move up") }, onClick = { onMove(item, -1); menuExpanded = false })
                            DropdownMenuItem(text = { Text("Move down") }, onClick = { onMove(item, 1); menuExpanded = false })
                            DropdownMenuItem(text = { Text("Rename / edit") }, onClick = { onEdit(item); menuExpanded = false })
                            if (item.kind == "page") {
                                DropdownMenuItem(text = { Text("Split after middle block") }, onClick = { onSplitPage(item); menuExpanded = false })
                                DropdownMenuItem(text = { Text("Merge with next page") }, onClick = { onMergePage(item); menuExpanded = false })
                            }
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(item); menuExpanded = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(item: ScribeItem, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text(item.body.take(75), maxLines = 1) },
        leadingContent = { Icon(Icons.Rounded.Search, null, tint = Gold) }, modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Text("SCRIBE", color = Gold, fontSize = 10.sp, letterSpacing = 3.sp)
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
    Text(subtitle, color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.AutoStories, null, tint = Color(0xFF5D574C), modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, fontWeight = FontWeight.Medium) }, supportingContent = { Text(subtitle, color = Muted, fontSize = 11.sp) },
        leadingContent = { Icon(icon, null, tint = Gold) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = Muted) },
        modifier = Modifier.clickable(onClick = onClick), colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    HorizontalDivider(color = Color(0xFF292A2E))
}

@Composable
private fun CreateDialog(kind: String, onDismiss: () -> Unit, title: String = "Create ${kind.replaceFirstChar { it.uppercase() }}", onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, colors = fieldColors())
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags / description (optional)") }, colors = fieldColors())
        }
    }, confirmButton = { TextButton(onClick = { onCreate(name, tags) }) { Text("Create", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = Color(0xFF202125))
}

@Composable
private fun AddBlockDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var type by remember { mutableStateOf("text") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    val types = listOf("text", "heading", "equation", "code", "table", "list", "checklist", "quote", "link", "diagram", "question", "flashcard")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add to page") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("CONTENT TYPE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                types.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { value ->
                            FilterChip(selected = type == value, onClick = { type = value }, label = { Text(value.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) })
                        }
                    }
                }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Heading / label") }, singleLine = true, colors = fieldColors())
            OutlinedTextField(body, { body = it }, label = { Text(if (type == "equation") "Equation (LaTeX source)" else if (type == "link") "URL" else "Write your content") },
                minLines = 3, maxLines = 7, colors = fieldColors())
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (optional)") }, singleLine = true, colors = fieldColors())
        }
    }, confirmButton = { TextButton(onClick = { onAdd(type, title, body, tags) }) { Text("Add block", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = Color(0xFF202125))
}

@Composable
private fun EditDialog(item: ScribeItem, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var bodyValue by remember(item.id) { mutableStateOf(TextFieldValue(item.body)) }
    var tags by remember(item.id) { mutableStateOf(item.tags) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit ${item.kind}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, colors = fieldColors())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("**", "**") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("B", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("*", "*") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("I", fontStyle = FontStyle.Italic) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("__", "__") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("U", textDecoration = TextDecoration.Underline) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("~~", "~~") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("S", textDecoration = TextDecoration.LineThrough) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("`", "`") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Code", fontFamily = FontFamily.Monospace) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("## ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("H2") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("- ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("List") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("> ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Quote") }
            }
            OutlinedTextField(bodyValue, { bodyValue = it }, label = { Text("Description / content") }, minLines = 3, colors = fieldColors())
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, colors = fieldColors())
        }
    }, confirmButton = { TextButton(onClick = { onSave(title, bodyValue.text, tags) }) { Text("Save", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, containerColor = Color(0xFF202125))
}

private fun TextFieldValue.wrapSelection(prefix: String, suffix: String): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(start, text.length)
    val selected = text.substring(start, end)
    val placeholder = if (selected.isEmpty()) "text" else selected
    val replacement = prefix + placeholder + suffix
    val updated = text.replaceRange(start, end, replacement)
    val newSelection = if (selected.isEmpty()) TextRange(start + prefix.length, start + prefix.length + placeholder.length)
        else TextRange(start + replacement.length)
    return TextFieldValue(updated, newSelection)
}

private fun styledBody(value: String): AnnotatedString {
    val pattern = Regex("""(\*\*.+?\*\*|__.+?__|~~.+?~~|\*.+?\*)""")
    return buildAnnotatedString {
        var cursor = 0
        pattern.findAll(value).forEach { match ->
            append(value.substring(cursor, match.range.first))
            val token = match.value
            when {
                token.startsWith("**") -> { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(token.removeSurrounding("**")); pop() }
                token.startsWith("__") -> { pushStyle(SpanStyle(textDecoration = TextDecoration.Underline)); append(token.removeSurrounding("__")); pop() }
                token.startsWith("~~") -> { pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)); append(token.removeSurrounding("~~")); pop() }
                else -> { pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(token.removeSurrounding("*")); pop() }
            }
            cursor = match.range.last + 1
        }
        append(value.substring(cursor))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF45464A),
    focusedLabelColor = Gold, cursorColor = Gold,
    focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

private data class PdfHandle(val descriptor: ParcelFileDescriptor, val renderer: PdfRenderer)

@Composable
private fun PdfViewerDialog(item: ScribeItem, onDismiss: () -> Unit) {
    val handle = remember(item.uri) {
        runCatching {
            val descriptor = ParcelFileDescriptor.open(File(item.uri), ParcelFileDescriptor.MODE_READ_ONLY)
            PdfHandle(descriptor, PdfRenderer(descriptor))
        }.getOrNull()
    }
    DisposableEffect(handle) {
        onDispose {
            runCatching { handle?.renderer?.close() }
            runCatching { handle?.descriptor?.close() }
        }
    }
    var pageIndex by remember(item.id) { mutableIntStateOf(0) }
    var ocrText by remember(item.id) { mutableStateOf("") }
    var ocrBusy by remember(item.id) { mutableStateOf(false) }
    var ocrError by remember(item.id) { mutableStateOf("") }
    var ocrQuery by remember(item.id) { mutableStateOf("") }
    LaunchedEffect(pageIndex) {
        ocrText = ""
        ocrError = ""
        ocrQuery = ""
    }
    val pageCount = handle?.renderer?.pageCount ?: 0
    val bitmap by produceState<android.graphics.Bitmap?>(null, handle, pageIndex) {
        value = null
        val renderer = handle?.renderer ?: return@produceState
        if (pageIndex !in 0 until renderer.pageCount) return@produceState
        value = runCatching {
            withContext(Dispatchers.IO) {
                val pdfPage = renderer.openPage(pageIndex)
                try {
                    val width = pdfPage.width.coerceAtMost(1400)
                    val scale = width.toFloat() / pdfPage.width.toFloat()
                    val height = (pdfPage.height * scale).toInt().coerceAtLeast(1)
                    android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { image ->
                        pdfPage.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                } finally { pdfPage.close() }
            }
        }.getOrNull()
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().fillMaxHeight(0.92f), shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17191D))) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(if (pageCount > 0) "PDF · Page ${pageIndex + 1} of $pageCount" else "PDF preview", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close PDF") }
                }
                HorizontalDivider(color = Color(0xFF33363B))
                if (handle == null || pageCount == 0) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("This PDF could not be opened.", color = Muted) }
                } else if (bitmap != null) {
                    Image(bitmap!!.asImageBitmap(), contentDescription = "PDF page ${pageIndex + 1}",
                        modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                }
                HorizontalDivider(color = Color(0xFF33363B))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = bitmap != null && !ocrBusy, onClick = {
                        val currentBitmap = bitmap ?: return@OutlinedButton
                        ocrBusy = true
                        ocrError = ""
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        recognizer.process(InputImage.fromBitmap(currentBitmap, 0))
                            .addOnSuccessListener { result -> ocrText = result.text; ocrBusy = false }
                            .addOnFailureListener { error -> ocrError = error.localizedMessage ?: "Text extraction failed."; ocrBusy = false }
                            .addOnCompleteListener { recognizer.close() }
                    }) {
                        if (ocrBusy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.DocumentScanner, null)
                        Spacer(Modifier.width(6.dp)); Text(if (ocrBusy) "Reading..." else "Extract text")
                    }
                    if (ocrText.isNotBlank()) Text(ocrText.length.toString() + " characters", color = Muted, fontSize = 11.sp)
                }
                if (ocrError.isNotBlank()) Text(ocrError, color = Color(0xFFE49A91), fontSize = 11.sp)
                if (ocrText.isNotBlank()) {
                    OutlinedTextField(ocrQuery, { ocrQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Find in extracted text") })
                    Card(Modifier.fillMaxWidth().heightIn(max = 140.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF202125))) {
                        val visibleText = if (ocrQuery.isBlank()) ocrText else ocrText.split(" ").filter { it.contains(ocrQuery, true) }.joinToString(" ").ifBlank { "No matching text on this page." }
                        Text(visibleText, Modifier.verticalScroll(rememberScrollState()).padding(10.dp), color = Color(0xFFE0DDD6), fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Color(0xFF33363B))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) {
                        Icon(Icons.Rounded.ChevronLeft, null); Text("Previous")
                    }
                    Text(if (pageCount > 0) "${pageIndex + 1} / $pageCount" else "—", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, enabled = pageIndex < pageCount - 1) {
                        Text("Next"); Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
    }
}

private fun exportPdf(context: Context, uri: Uri, title: String, blocks: List<ScribeItem>) {
    val document = PdfDocument()
    val paint = Paint().apply { textSize = 12f; color = android.graphics.Color.BLACK; isAntiAlias = true }
    val heading = Paint(paint).apply { textSize = 22f; typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD) }
    val sub = Paint(paint).apply { textSize = 10f; color = android.graphics.Color.DKGRAY }
    var pageNo = 1
    var y = 58f
    var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
    page.canvas.drawText(title, 42f, y, heading); y += 28f
    page.canvas.drawText("Exported from Scribe · ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}", 42f, y, sub); y += 28f
    fun newPage() { document.finishPage(page); pageNo++; page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 48f }
    blocks.forEach { block ->
        if (y > 770f) newPage()
        page.canvas.drawText(block.title.take(85), 42f, y, heading.apply { textSize = 14f }); y += 20f
        val text = block.body.ifBlank { block.uri }
        text.chunked(88).forEach { line ->
            if (y > 790f) newPage()
            page.canvas.drawText(line, 42f, y, paint); y += 17f
        }
        y += 12f
    }
    document.finishPage(page)
    context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
    document.close()
}
