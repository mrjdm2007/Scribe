package com.mrjdm.scribe

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjdm.scribe.data.*
import com.mrjdm.scribe.ui.ScribeTheme
import com.mrjdm.scribe.ui.scribeGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        setContent { ScribeTheme { ScribeApp() } }
    }
}

private val Gold = Color(0xFFD5B98C)
private val Muted = Color(0xFF96938D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScribeApp(vm: ScribeViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val scope = rememberCoroutineScope()
    var stack by remember { mutableStateOf(listOf<ScribeItem>()) }
    var savedStackIds by rememberSaveable { mutableStateOf("") }
    var recentItems by remember { mutableStateOf<List<ScribeItem>>(emptyList()) }
    fun recordRecent(item: ScribeItem) {
        if (item.kind !in listOf("course", "chapter", "topic", "page", "block")) return
        recentItems = (listOf(item) + recentItems.filterNot { it.id == item.id }).take(6)
        context.getSharedPreferences("scribe_navigation", Context.MODE_PRIVATE).edit()
            .putString("recent_ids", recentItems.joinToString(",") { it.id.toString() }).apply()
    }
    var navigationReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val restoredIds = savedStackIds.split(",").mapNotNull { it.toLongOrNull() }
        if (restoredIds.isNotEmpty()) stack = restoredIds.mapNotNull { vm.get(it) }
        val storedRecent = context.getSharedPreferences("scribe_navigation", Context.MODE_PRIVATE)
            .getString("recent_ids", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }
        recentItems = storedRecent.mapNotNull { vm.get(it) }.filter { it.deletedAt == null }
        navigationReady = true
    }
    LaunchedEffect(stack, navigationReady) {
        if (navigationReady) savedStackIds = stack.joinToString(",") { it.id.toString() }
    }
    var tab by remember { mutableStateOf("Library") }
    var showCreate by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf("course") }
    var editItem by remember { mutableStateOf<ScribeItem?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchKind by rememberSaveable { mutableStateOf("All") }
    var searchSavedOnly by rememberSaveable { mutableStateOf(false) }
    var searchSort by rememberSaveable { mutableStateOf("Recent") }
    var bulkSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    var bulkTagInput by remember { mutableStateOf("") }
    var showBulkMove by remember { mutableStateOf(false) }
    var quickCapture by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf<ScribeItem?>(null) }
    var showTrash by remember { mutableStateOf(false) }
    var showCloudSync by remember { mutableStateOf(false) }
    var firebaseApiKey by remember { mutableStateOf("") }
    var firebaseProjectId by remember { mutableStateOf("") }
    var createFirebaseAccount by remember { mutableStateOf(false) }
    var cloudEmail by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var cloudStatus by remember { mutableStateOf("") }
    var cloudBusy by remember { mutableStateOf(false) }
    var trashSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(Unit) {
        val firebasePrefs = context.getSharedPreferences("scribe_firebase_config", Context.MODE_PRIVATE)
        firebaseApiKey = firebasePrefs.getString("api_key", "").orEmpty()
        firebaseProjectId = firebasePrefs.getString("project_id", "").orEmpty()
    }
    var integrityReport by remember { mutableStateOf<String?>(null) }
    val current = stack.lastOrNull()
    val children by vm.children(current?.id).collectAsState(initial = emptyList())
    val bookmarks by vm.bookmarks.collectAsState(initial = emptyList())
    val trash by vm.trash.collectAsState(initial = emptyList())
    val searchResults by vm.search(searchQuery).collectAsState(initial = emptyList())
    val filteredSearchResults = remember(searchResults, searchKind, searchSavedOnly, searchSort) {
        val filtered = searchResults.filter { item ->
            (searchKind == "All" || item.kind.equals(searchKind, ignoreCase = true)) && (!searchSavedOnly || item.bookmarked)
        }
        when (searchSort) {
            "Title" -> filtered.sortedBy { it.title.lowercase() }
            "Oldest" -> filtered.sortedBy { it.updatedAt }
            "Created" -> filtered.sortedByDescending { it.createdAt }
            "Longest" -> filtered.sortedByDescending { it.body.length }
            "Shortest" -> filtered.sortedBy { it.body.length }
            else -> filtered.sortedByDescending { it.updatedAt }
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && current != null && current.kind in listOf("page", "topic")) {
            scope.launch(Dispatchers.IO) {
                val name = runCatching {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfter(':') ?: "Attachment"
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                val fileExtension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val officeExtensions = setOf("docx", "xlsx", "pptx", "doc", "xls", "ppt", "odt", "ods", "odp", "rtf", "csv")
                val officeMimeTypes = setOf(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
                    "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
                    "application/vnd.oasis.opendocument.presentation", "application/rtf", "text/rtf", "text/csv"
                )
                val file = File(context.filesDir, "attachments").apply { mkdirs() }
                val target = File(file, "${System.currentTimeMillis()}_${name.substringAfterLast(':').replace('/', '_')}")
                val copied = runCatching {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected file")
                    input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
                    target.isFile && target.length() > 0L
                }.getOrDefault(false)
                if (!copied) return@launch
                val kind = when {
                    mimeType.startsWith("image/") -> "image"
                    mimeType == "application/pdf" || fileExtension == "pdf" -> "pdf"
                    fileExtension in officeExtensions || mimeType in officeMimeTypes -> "office"
                    else -> "file"
                }
                val indexedText = if (kind == "pdf") runCatching { indexPdfText(target) }.getOrDefault("") else ""
                val attachmentBody = when (kind) {
                    "pdf" -> if (indexedText.isBlank()) "PDF attachment · no searchable text could be extracted"
                        else "PDF text index (on-device OCR)\n\n$indexedText"
                    "office" -> "Office document · ${fileExtension.uppercase(Locale.ROOT)}"
                    else -> "Attached file"
                }
                val attachmentTags = when (kind) {
                    "pdf" -> if (indexedText.isBlank()) "type:pdf;ocr:failed" else "type:pdf;ocr:indexed"
                    "office" -> "type:office;format:$fileExtension"
                    else -> "type:$kind"
                }
                vm.add(current.id, "block", name, body = attachmentBody, uri = target.absolutePath,
                    tags = attachmentTags)
            }
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val all = vm.all()
            val array = JSONArray()
            all.forEach { item -> array.put(JSONObject().apply {
                put("id", item.id); put("parentId", item.parentId ?: JSONObject.NULL); put("kind", item.kind)
                put("title", item.title); put("body", item.body); put("uri", item.uri); put("tags", item.tags)
                put("bookmarked", item.bookmarked); put("position", item.position); put("createdAt", item.createdAt); put("updatedAt", item.updatedAt)
                put("deletedAt", item.deletedAt ?: JSONObject.NULL)
                if (item.uri.isNotBlank()) {
                    val attachment = File(item.uri)
                    if (attachment.isFile) put("attachmentBase64", Base64.encodeToString(attachment.readBytes(), Base64.NO_WRAP))
                }
                val history = JSONArray()
                vm.snapshots(item.id).first().forEach { snapshot ->
                    history.put(JSONObject().apply {
                        put("title", snapshot.title)
                        put("body", snapshot.body)
                        put("savedAt", snapshot.savedAt)
                    })
                }
                put("history", history)
            }) }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(array.toString(2)) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val restoreResult = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to read the selected backup file.")
                val array = JSONArray(raw)
                val restoredSnapshots = mutableListOf<NoteSnapshot>()
                val restored = buildList {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val encodedAttachment = o.optString("attachmentBase64")
                        val restoredUri = if (encodedAttachment.isNotBlank()) {
                            val directory = File(context.filesDir, "attachments").apply { mkdirs() }
                            val target = File(directory, "restored_${System.currentTimeMillis()}_${o.optLong("id")}")
                            target.writeBytes(Base64.decode(encodedAttachment, Base64.DEFAULT))
                            target.absolutePath
                        } else o.optString("uri")
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
                validateBackupItems(restored)?.let { error(it) }
                vm.restore(restored, restoredSnapshots)
            }
            restoreResult.exceptionOrNull()?.let { restoreError ->
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Backup restore failed: " + (restoreError.localizedMessage ?: "Invalid backup"), android.widget.Toast.LENGTH_LONG).show()
                }
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScribeFloatingStudyTools(if (tab == "Library") current else null, vm)
                if (tab == "Library" && current?.kind != "page" && current?.kind != "topic") {
                    FloatingActionButton(onClick = {
                        createKind = when(current?.kind) { null -> "course"; "course" -> "chapter"; "chapter" -> "topic"; "topic" -> "page"; else -> "course" }
                        showCreate = true
                    }, containerColor = Gold, contentColor = Color(0xFF211B12)) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add")
                    }
                }
            }
        },
        bottomBar = {
            if (!isWide) NavigationBar(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).scribeGlass(RoundedCornerShape(22.dp)),
                containerColor = Color.Transparent, contentColor = Gold, tonalElevation = 0.dp
            ) {
                listOf("Library", "Saved", "Study", "AI", "Workspace", "Tools", "Settings").forEach { label ->
                    val icon = when(label) { "Library" -> Icons.Rounded.AutoStories; "Saved" -> Icons.Rounded.Bookmark; "Tools" -> Icons.Rounded.Functions; "Study" -> Icons.Rounded.School; "AI" -> Icons.Rounded.AutoAwesome; "Workspace" -> Icons.Rounded.Insights; else -> Icons.Rounded.Settings }
                    NavigationBarItem(selected = tab == label, onClick = { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap); tab = label; stack = emptyList() },
                        icon = { Icon(icon, null) }, label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Color(0xFF33302A), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).animateContentSize().background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.075f), Color.Transparent), radius = 1050f))) {
            if (isWide) {
                NavigationRail(
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp).scribeGlass(RoundedCornerShape(20.dp)),
                    containerColor = Color.Transparent, contentColor = Gold
                ) {
                    Spacer(Modifier.height(12.dp))
                    listOf("Library", "Saved", "Study", "AI", "Workspace", "Tools", "Settings").forEach { label ->
                        val icon = when(label) { "Library" -> Icons.Rounded.AutoStories; "Saved" -> Icons.Rounded.Bookmark; "Tools" -> Icons.Rounded.Functions; "Study" -> Icons.Rounded.School; "Workspace" -> Icons.Rounded.Insights; else -> Icons.Rounded.Settings }
                        NavigationRailItem(selected = tab == label, onClick = { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap); tab = label; stack = emptyList() }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = if (isWide) 1320.dp else Dp.Unspecified).fillMaxWidth().fillMaxHeight().padding(horizontal = if (isWide) 28.dp else 20.dp).animateContentSize()) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "Library" -> {
                    if (current?.kind == "page") {
                        PageEditor(current, vm, onBack = { stack = stack.dropLast(1) }, onHistory = { showHistory = current }, onOpenTopic = { linked -> recordRecent(linked); stack = listOf(linked) }, onAttach = {
                            attachmentPicker.launch(arrayOf(
                                "image/*", "application/pdf", "text/*",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
                                "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
                                "application/vnd.oasis.opendocument.presentation", "application/rtf", "text/csv"
                            ))
                        }, onExport = { pdfLauncher.launch("${current.title}.pdf") })
                    } else if (current?.kind == "topic") {
                        TopicNotebook(current, vm, onBack = { stack = stack.dropLast(1) },
                            onOpenPage = { page -> recordRecent(page); stack = stack + page },
                            onAttach = { attachmentPicker.launch(arrayOf(
                                "image/*", "application/pdf", "text/*",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
                                "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
                                "application/vnd.oasis.opendocument.presentation", "application/rtf", "text/csv"
                            )) })
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
                            Text("PAGES", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text("YOUR ${current.kind.uppercase()}S", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            Spacer(Modifier.height(10.dp))
                        }
                        ItemList(children, onClick = { recordRecent(it); stack = stack + it }, onBookmark = vm::toggleBookmark, onEdit = { editItem = it }, onDelete = vm::removeTree, onDuplicate = vm::duplicate, onMove = vm::move, onMergePage = vm::mergePageWithNext, onSplitPage = vm::splitPage)
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
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf("All", "course", "chapter", "topic", "page", "block").forEach { kind ->
                                    FilterChip(selected = searchKind == kind, onClick = { searchKind = kind }, label = { Text(kind.replaceFirstChar { it.uppercase() }) })
                                }
                                FilterChip(selected = searchSavedOnly, onClick = { searchSavedOnly = !searchSavedOnly }, label = { Text("Saved only") })
                                TextButton(onClick = { searchSort = when (searchSort) { "Recent" -> "Oldest"; "Oldest" -> "Created"; "Created" -> "Title"; "Title" -> "Longest"; "Longest" -> "Shortest"; else -> "Recent" } }) { Text("Sort: $searchSort") }
                                TextButton(onClick = { searchKind = "All"; searchSavedOnly = false; searchSort = "Recent" }) { Text("Reset") }
                            }
                            Spacer(Modifier.height(8.dp)); Text("SEARCH RESULTS · ${filteredSearchResults.size}", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            if (filteredSearchResults.isEmpty()) EmptyState("No matching notes", "Try another type, turn off Saved only, or change your search.")
                            if (bulkSelectedIds.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${bulkSelectedIds.size} selected", color = Gold, fontSize = 12.sp)
                                    TextButton(onClick = { bulkSelectedIds = filteredSearchResults.map { it.id }.toSet() }) { Text("All") }
                                    TextButton(onClick = { showBulkMove = true }) { Text("Move") }
                                    OutlinedTextField(bulkTagInput, { bulkTagInput = it }, modifier = Modifier.width(120.dp), singleLine = true, placeholder = { Text("Tag") })
                                    TextButton(onClick = {
                                        vm.bulkTag(filteredSearchResults.filter { it.id in bulkSelectedIds }, bulkTagInput)
                                        bulkTagInput = ""; bulkSelectedIds = emptySet()
                                    }, enabled = bulkTagInput.isNotBlank()) { Text("Tag") }
                                    TextButton(onClick = {
                                        vm.bulkArchive(filteredSearchResults.filter { it.id in bulkSelectedIds })
                                        bulkSelectedIds = emptySet()
                                    }) { Text("Archive", color = Color(0xFFE39A8F)) }
                                    TextButton(onClick = { bulkSelectedIds = emptySet() }) { Text("Clear") }
                                }
                                DropdownMenu(expanded = showBulkMove, onDismissRequest = { showBulkMove = false }) {
                                    children.filter { it.kind in listOf("course", "chapter", "topic") }.forEach { target ->
                                        DropdownMenuItem(text = { Text(target.title) }, onClick = {
                                            vm.bulkMove(filteredSearchResults.filter { it.id in bulkSelectedIds }, target.id)
                                            bulkSelectedIds = emptySet(); showBulkMove = false
                                        })
                                    }
                                }
                            }
                            LazyColumn { items(filteredSearchResults, key = { it.id }) { item ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = item.id in bulkSelectedIds, onCheckedChange = { checked ->
                                        bulkSelectedIds = if (checked) bulkSelectedIds + item.id else bulkSelectedIds - item.id
                                    })
                                    SearchRow(item) {
                                scope.launch {
                                    if (item.kind == "block" && item.parentId != null) {
                                        val parent = vm.get(item.parentId)
                                        stack = if (parent != null) listOf(parent) else listOf(item)
                                    } else stack = listOf(item)
                                }
                                    }
                                }
                            } }
                        } else {
                            if (recentItems.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("RECENTLY OPENED", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = {
                                        recentItems = emptyList()
                                        context.getSharedPreferences("scribe_navigation", Context.MODE_PRIVATE).edit().remove("recent_ids").apply()
                                    }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                        Text("Clear", color = Muted, fontSize = 11.sp)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    recentItems.forEach { recent ->
                                        Surface(onClick = { stack = listOf(recent) }, shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF202126), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))) {
                                            Column(Modifier.widthIn(min = 120.dp, max = 180.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.075f), Color.Transparent, Gold.copy(alpha = 0.025f))), RoundedCornerShape(12.dp)).border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.24f), Gold.copy(alpha = 0.12f), Color.White.copy(alpha = 0.035f))), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                                Text(recent.title, maxLines = 1, fontWeight = FontWeight.Medium)
                                                Text(recent.kind.replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(18.dp))
                            }
                            Spacer(Modifier.height(22.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Your courses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("${children.size} NOTEBOOKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            ItemList(children.filter { it.kind == "course" }, onClick = { recordRecent(it); stack = listOf(it) }, onBookmark = vm::toggleBookmark, onEdit = { editItem = it }, onDelete = vm::removeTree)
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
                "AI" -> ScribeIntelligenceScreen(vm)
                "Workspace" -> StudyWorkspaceScreen(bookmarks, vm)
                "Tools" -> AcademicToolsHub(vm)
                else -> {
                    ScreenHeading("Settings", "Your Scribe workspace")
                    SettingsRow(Icons.Rounded.CloudUpload, "Export full backup", "Save courses, notes and organisation as JSON") { backupLauncher.launch("Scribe-backup.json") }
                    SettingsRow(Icons.Rounded.CloudDownload, "Restore backup", "Replace this library from a Scribe backup") { restoreLauncher.launch(arrayOf("application/json", "text/*")) }
                    SettingsRow(Icons.Rounded.DeleteSweep, "Recently deleted", "Restore or permanently remove deleted notes") { showTrash = true }
                    SettingsRow(Icons.Rounded.CloudUpload, "Firebase cloud sync", "Sign in with Firebase to sync your Scribe library across devices") { showCloudSync = true }
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
                    Text("Notes remain available offline. Cloud sync is optional; keep regular backups as an additional safeguard.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 18.dp))
                }
            }
            }
            }
            }
        }
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
                    Text(snap.body, maxLines = 3, color = Muted)
                    TextButton(onClick = { vm.update(item, body = snap.body); showHistory = null }) { Text("Restore this version") }
                }
            } } }, confirmButton = { TextButton(onClick = { showHistory = null }) { Text("Close") } })
    }

    if (showCloudSync) AlertDialog(
        onDismissRequest = { if (!cloudBusy) showCloudSync = false },
        title = { Text("Firebase cloud sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("Use the Firebase Web API key and Project ID from Firebase Project settings. Firebase Authentication (Email/Password), Firestore and the deployed studyLibrary Cloud Function must be enabled. Your password is never saved; the refresh token is encrypted on this device.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(firebaseApiKey, { firebaseApiKey = it }, label = { Text("Firebase Web API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(firebaseProjectId, { firebaseProjectId = it }, label = { Text("Firebase project ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cloudEmail, { cloudEmail = it }, label = { Text("Account email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cloudPassword, { cloudPassword = it }, label = { Text("Account password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { createFirebaseAccount = !createFirebaseAccount }) { Text(if (createFirebaseAccount) "I already have an account · Sign in" else "New here? Create a Firebase account") }
                if (cloudStatus.isNotBlank()) Text(cloudStatus, color = if (cloudStatus.startsWith("Sync failed")) Color(0xFFE49A91) else Gold, fontSize = 12.sp)
                if (cloudBusy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !cloudBusy, onClick = {
                ScribeFirebaseSession.clear(context)
                cloudStatus = "Firebase automatic sync disabled. Your local notes are unchanged."
            }) { Text("Disable auto sync") }
            TextButton(enabled = !cloudBusy, onClick = {
                context.getSharedPreferences("scribe_firebase_config", Context.MODE_PRIVATE).edit()
                    .putString("api_key", firebaseApiKey.trim()).putString("project_id", firebaseProjectId.trim()).apply()
                cloudBusy = true
                cloudStatus = "Connecting and syncing…"
                scope.launch {
                    val result = runCatching { vm.syncFirebase(firebaseApiKey.trim(), firebaseProjectId.trim(), cloudEmail.trim(), cloudPassword, createFirebaseAccount) }
                    cloudStatus = result.fold({ it }, { "Sync failed: ${it.localizedMessage ?: "Unknown error"}" })
                    cloudPassword = ""
                    cloudBusy = false
                }
            }) { Text("Sync now", color = Gold) }
            }
        },
        dismissButton = { TextButton(enabled = !cloudBusy, onClick = { showCloudSync = false }) { Text("Close") } }
    )
    if (showTrash) AlertDialog(onDismissRequest = { showTrash = false }, title = { Text("Recently deleted") },
        text = {
            if (trash.isEmpty()) Text("Your trash is empty.")
            else Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { trashSelectedIds = if (trashSelectedIds.size == trash.size) emptySet() else trash.map { it.id }.toSet() }) {
                        Text(if (trashSelectedIds.size == trash.size) "Clear selection" else "Select all")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${trashSelectedIds.size} selected", color = Muted, fontSize = 11.sp)
                    TextButton(enabled = trashSelectedIds.isNotEmpty(), onClick = {
                        vm.bulkRestore(trash.filter { it.id in trashSelectedIds }); trashSelectedIds = emptySet()
                    }) { Text("Restore") }
                    TextButton(enabled = trashSelectedIds.isNotEmpty(), onClick = {
                        vm.bulkDeleteForever(trash.filter { it.id in trashSelectedIds }); trashSelectedIds = emptySet()
                    }) { Text("Delete", color = Color(0xFFE49A91)) }
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(trash, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.id in trashSelectedIds, onCheckedChange = { checked ->
                                trashSelectedIds = if (checked) trashSelectedIds + item.id else trashSelectedIds - item.id
                            })
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Medium)
                                Text(item.kind, color = Muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { vm.restoreTree(item); trashSelectedIds = trashSelectedIds - item.id }) { Text("Restore", color = Gold) }
                            IconButton(onClick = { vm.deleteForever(item); trashSelectedIds = trashSelectedIds - item.id }) { Icon(Icons.Rounded.DeleteForever, null, tint = Muted) }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showTrash = false; trashSelectedIds = emptySet() }) { Text("Close") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicNotebook(
    topic: ScribeItem,
    vm: ScribeViewModel,
    onBack: () -> Unit,
    onOpenPage: (ScribeItem) -> Unit,
    onAttach: () -> Unit
) {
    val observedTopic by vm.observe(topic.id).collectAsState(initial = topic)
    val liveTopic = observedTopic ?: topic
    val childItems by vm.children(topic.id).collectAsState(initial = emptyList())
    val pages = childItems.filter { it.kind == "page" }
    val blocks = childItems.filter { it.kind == "block" }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val compactHeight = LocalConfiguration.current.screenHeightDp < 520
    var noteValue by rememberSaveable(topic.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(topic.body)) }
    var searchQuery by rememberSaveable(topic.id) { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editBlock by remember { mutableStateOf<ScribeItem?>(null) }
    var showLatexPreview by rememberSaveable(topic.id) { mutableStateOf(false) }
    var showWordGoalDialog by remember { mutableStateOf(false) }
    var wordGoalDraft by remember(topic.id) { mutableStateOf(topic.tags.split(";").firstOrNull { it.startsWith("wordGoal:") }?.substringAfter(":") ?: "500") }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var showReadingMode by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("Saved") }

    LaunchedEffect(noteValue.text, topic.id) {
        saveStatus = "Unsaved"
        delay(500)
        saveStatus = "Saving…"
        runCatching { vm.saveNow(liveTopic, noteValue.text) }
            .onSuccess { saveStatus = "Saved" }
            .onFailure { saveStatus = "Save failed" }
    }

    val matchingLines = remember(searchQuery, noteValue.text) {
        if (searchQuery.isBlank()) emptyList()
        else noteValue.text.lines().mapIndexedNotNull { index, line ->
            if (line.contains(searchQuery, ignoreCase = true)) index to line else null
        }
    }
    val matchingBlocks = remember(searchQuery, blocks) {
        if (searchQuery.isBlank()) emptyList()
        else blocks.filter { it.title.contains(searchQuery, true) || it.body.contains(searchQuery, true) }
    }
    val totalMatches = matchingLines.size + matchingBlocks.size

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap); onBack() }) { Icon(Icons.Rounded.ArrowBack, null) }
            Column(Modifier.weight(1f)) {
                Text("TOPIC NOTEBOOK", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text(topic.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Text(saveStatus, color = Muted, fontSize = 10.sp)
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                scope.launch {
                    saveStatus = "Saving…"
                    runCatching { vm.saveNow(liveTopic, noteValue.text) }
                        .onSuccess { saveStatus = "Saved" }
                        .onFailure { saveStatus = "Save failed" }
                }
            }) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap); showReadingMode = true }) { Icon(Icons.Rounded.MenuBook, "Open reading mode", tint = Gold) }
            val noteWords = noteValue.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            Column(horizontalAlignment = Alignment.End) {
                Text("$noteWords words", color = Muted, fontSize = 10.sp)
                Text("${maxOf(1, (noteWords + 199) / 200)} min read · ${blocks.size} blocks", color = Muted, fontSize = 10.sp)
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Find a word in this topic…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, null) } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors()
        )
        if (searchQuery.isNotBlank()) {
            Text("$totalMatches matching lines or blocks", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            LazyColumn(Modifier.heightIn(max = 150.dp)) {
                items(matchingLines) { (lineIndex, line) ->
                    ListItem(
                        headlineContent = { Text(line, maxLines = 2) },
                        supportingContent = { Text("Notebook · line ${lineIndex + 1}", color = Muted) },
                        leadingContent = { Icon(Icons.Rounded.Search, null, tint = Gold) },
                        modifier = Modifier.clickable {
                            val lineStart = noteValue.text.lineSequence().take(lineIndex).sumOf { it.length + 1 }
                            val start = noteValue.text.indexOf(searchQuery, lineStart, ignoreCase = true).takeIf { it >= 0 } ?: lineStart
                            val end = (start + searchQuery.length).coerceAtMost(noteValue.text.length)
                            noteValue = TextFieldValue(noteValue.text, TextRange(start, end))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                items(matchingBlocks, key = { it.id }) { block ->
                    ListItem(
                        headlineContent = { Text(block.title) },
                        supportingContent = { Text(block.body.take(100), maxLines = 1) },
                        leadingContent = { Icon(Icons.Rounded.Notes, null, tint = Gold) },
                        modifier = Modifier.clickable { editBlock = block },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
        val checklistLines = noteValue.text.lines().filter { Regex("^\\s*[-*]\\s+\\[[ xX]\\]").containsMatchIn(it) }
        val checklistDone = checklistLines.count { Regex("^\\s*[-*]\\s+\\[[xX]\\]").containsMatchIn(it) }
        val currentWordCount = noteValue.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val wordGoal = liveTopic.tags.split(";").firstOrNull { it.startsWith("wordGoal:") }?.substringAfter(":")?.toIntOrNull() ?: 0
        if (checklistLines.isNotEmpty() || wordGoal > 0) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (checklistLines.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CHECKLIST", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("$checklistDone / ${checklistLines.size} completed", color = Muted, fontSize = 11.sp)
                    }
                    LinearProgressIndicator(progress = { checklistDone.toFloat() / checklistLines.size }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Gold, trackColor = Color.White.copy(alpha = 0.10f))
                }
                if (wordGoal > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("WRITING GOAL", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("$currentWordCount / $wordGoal words", color = Muted, fontSize = 11.sp)
                        TextButton(onClick = { showWordGoalDialog = true }) { Text("Edit", fontSize = 11.sp) }
                    }
                    LinearProgressIndicator(progress = { (currentWordCount.toFloat() / wordGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Gold, trackColor = Color.White.copy(alpha = 0.10f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { noteValue = noteValue.wrapSelection("**", "**"); vm.update(liveTopic, body = noteValue.text) }) { Text("B", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("*", "*"); vm.update(liveTopic, body = noteValue.text) }) { Text("I", fontStyle = FontStyle.Italic) }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("__", "__"); vm.update(liveTopic, body = noteValue.text) }) { Text("U", textDecoration = TextDecoration.Underline) }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("~~", "~~"); vm.update(liveTopic, body = noteValue.text) }) { Text("S", textDecoration = TextDecoration.LineThrough) }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("## ", ""); vm.update(liveTopic, body = noteValue.text) }) { Text("Heading") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("- ", ""); vm.update(liveTopic, body = noteValue.text) }) { Text("List") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("- [ ] ", ""); vm.update(liveTopic, body = noteValue.text) }) { Text("Task") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("1. ", ""); vm.update(liveTopic, body = noteValue.text) }) { Text("1. List") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("\$\$\n", "\n\$\$"); vm.update(liveTopic, body = noteValue.text) }) { Text("Display math") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("```\n", "\n```"); vm.update(liveTopic, body = noteValue.text) }) { Text("Code") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("\\(", "\\)"); vm.update(liveTopic, body = noteValue.text) }) { Text("Math") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("> ", ""); vm.update(liveTopic, body = noteValue.text) }) { Text("Quote") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("[", "](https://)") ; vm.update(liveTopic, body = noteValue.text) }) { Text("Link") }
            TextButton(onClick = { noteValue = noteValue.wrapSelection("| ", " |  |"); vm.update(liveTopic, body = noteValue.text) }) { Text("Table") }
            TextButton(onClick = { showLatexPreview = !showLatexPreview }) { Text(if (showLatexPreview) "Edit" else "Preview") }
            Box {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap); showTemplateMenu = true }) { Text("Templates") }
                DropdownMenu(expanded = showTemplateMenu, onDismissRequest = { showTemplateMenu = false }) {
                    listOf(
                        "Lecture notes" to "# Lecture notes\n\n## Key concepts\n- \n\n## Examples\n- \n\n## Questions\n- [ ] ",
                        "Textbook summary" to "# Summary\n\n## Main idea\n\n## Important terms\n- \n\n## Takeaways\n- \n",
                        "Problem solving" to "# Problem\n\n## Given\n\n## Find\n\n## Approach\n1. \n\n## Solution\n\n## Final answer\n"
                    ).forEach { (label, template) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            noteValue = TextFieldValue(if (noteValue.text.isBlank()) template else noteValue.text.trimEnd() + "\n\n" + template)
                            vm.update(liveTopic, body = noteValue.text)
                            showTemplateMenu = false
                        })
                    }
                }
            }
            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap); wordGoalDraft = if (wordGoal > 0) wordGoal.toString() else "500"; showWordGoalDialog = true }) { Text("Word goal") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().scribeGlass(RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.035f), Gold.copy(alpha = 0.18f))))
                ) {
                    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.085f), Color.White.copy(alpha = 0.018f), Color.Transparent, Color.Black.copy(alpha = 0.09f)))).padding(14.dp)) {
                        Text("YOUR NOTES", color = Gold, fontSize = 10.sp, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        if (showLatexPreview) {
                            LatexPreviewWebView(noteValue.text, Modifier.fillMaxWidth().heightIn(min = if (compactHeight) 180.dp else 280.dp, max = if (compactHeight) 380.dp else 620.dp))
                        } else {
                            RichMarkdownEditor(value = noteValue.text, onValueChange = { edited, start, length ->
                                val safeStart = start.coerceIn(0, edited.length)
                                noteValue = TextFieldValue(edited, TextRange(safeStart, (safeStart + length).coerceIn(safeStart, edited.length)))
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = if (compactHeight) 220.dp else 360.dp, max = if (compactHeight) 460.dp else 680.dp))
                        }
                    }
                }
            }
            if (blocks.isNotEmpty()) {
                item { Text("NOTE ELEMENTS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp) }
                items(blocks, key = { it.id }) { block ->
                    BlockCard(block, onClick = { editBlock = block }, onBookmark = { vm.toggleBookmark(block) },
                        onDelete = { vm.removeTree(block) }, onMove = { direction -> vm.move(block, direction) })
                }
            }
            if (pages.isNotEmpty()) {
                item { Text("LEGACY NOTES", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp) }
                items(pages, key = { it.id }) { page ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenPage(page) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E23)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                    ) {
                        ListItem(
                            headlineContent = { Text(page.title) },
                            supportingContent = { Text("Older imported note · tap to open", color = Muted) },
                            leadingContent = { Icon(Icons.Rounded.Article, null, tint = Gold) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add element")
                    }
                    OutlinedButton(onClick = onAttach, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Attach file")
                    }
                }
            }
        }
    }
    if (showReadingMode) NotesReadingMode(
        title = topic.title,
        markdown = noteValue.text + blocks.joinToString("\n\n") { block -> "\n## ${block.title}\n\n${block.body}" },
        onDismiss = { showReadingMode = false }
    )
    if (showAdd) AddBlockDialog(onDismiss = { showAdd = false }) { type, title, body, tags ->
        vm.add(topic.id, "block", title.ifBlank { type.replaceFirstChar { it.uppercase() } },
            body = body, tags = "type:$type,$tags")
        showAdd = false
    }
    editBlock?.let { block ->
        EditDialog(block, onDismiss = { editBlock = null }, onSave = { title, body, tags ->
            vm.update(block, title = title, body = body, tags = tags)
            editBlock = null
        })
    }
    if (showWordGoalDialog) {
        AlertDialog(
            onDismissRequest = { showWordGoalDialog = false },
            title = { Text("Set writing goal") },
            text = {
                OutlinedTextField(
                    value = wordGoalDraft,
                    onValueChange = { wordGoalDraft = it.filter(Char::isDigit).take(6) },
                    label = { Text("Target words (0 disables)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = liveTopic.tags.split(";").filter { it.isNotBlank() && !it.startsWith("wordGoal:") }
                    val goal = wordGoalDraft.toIntOrNull()?.coerceIn(0, 100000) ?: 0
                    vm.update(liveTopic, tags = (clean + if (goal > 0) listOf("wordGoal:$goal") else emptyList()).joinToString(";"))
                    showWordGoalDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showWordGoalDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PageEditor(page: ScribeItem, vm: ScribeViewModel, onBack: () -> Unit, onHistory: () -> Unit, onOpenTopic: (ScribeItem) -> Unit, onAttach: () -> Unit, onExport: () -> Unit) {
    val blocks by vm.children(page.id).collectAsState(initial = emptyList())
    val topics by vm.kind("topic").collectAsState(initial = emptyList())
    val allItems by vm.observeAllItems().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showLinkPicker by remember { mutableStateOf(false) }
    var editBlock by remember { mutableStateOf<ScribeItem?>(null) }
    var previewPdf by remember { mutableStateOf<ScribeItem?>(null) }
    var pageTitle by remember(page.id) { mutableStateOf(page.title) }
    var showReadingMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            Column(Modifier.weight(1f)) {
                Text("NOTE PAGE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Text(page.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            IconButton(onClick = { showReadingMode = true }) { Icon(Icons.Rounded.MenuBook, "Open reading mode", tint = Gold) }
            IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, null, tint = Muted) }
            IconButton(onClick = onExport) { Icon(Icons.Rounded.PictureAsPdf, null, tint = Muted) }
        }
        val backlinkBlocks = allItems.filter { block ->
            block.tags.contains("type:crosslink") &&
                block.tags.substringAfter("target:", "").substringBefore(";").substringBefore(",").toLongOrNull() == page.id
        }
        val backlinkSources = backlinkBlocks.mapNotNull { block -> allItems.firstOrNull { it.id == block.parentId } }.distinctBy { it.id }
        if (backlinkSources.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Text("LINKED FROM", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(5.dp))
                backlinkSources.forEach { source ->
                    TextButton(onClick = { onOpenTopic(source) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)) {
                        Icon(Icons.Rounded.Link, null, tint = Gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(source.title, maxLines = 1)
                    }
                }
            }
        }
        val outgoingLinkBlocks = allItems.filter { it.parentId == page.id && it.tags.contains("type:crosslink") }
        val outgoingTargets = outgoingLinkBlocks.mapNotNull { block ->
            block.tags.substringAfter("target:", "").substringBefore(";").substringBefore(",").toLongOrNull()
        }
        val resolvedTargets = outgoingTargets.distinct().mapNotNull { targetId -> allItems.firstOrNull { it.id == targetId } }
        val brokenLinkCount = outgoingTargets.count { targetId -> allItems.none { it.id == targetId } }
        if (outgoingLinkBlocks.isNotEmpty() || backlinkBlocks.isNotEmpty()) {
            Text("${outgoingLinkBlocks.size} outgoing · ${backlinkBlocks.size} incoming links", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        if (resolvedTargets.isNotEmpty() || brokenLinkCount > 0) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Text("LINKS IN THIS TOPIC", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                resolvedTargets.forEach { target ->
                    TextButton(onClick = { onOpenTopic(target) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)) {
                        Icon(Icons.Rounded.Link, null, tint = Gold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(target.title, maxLines = 1)
                    }
                }
                if (brokenLinkCount > 0) Text("$brokenLinkCount broken topic link(s) · target may have been deleted", color = Color(0xFFE0A45C), fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp))
            }
        }
        OutlinedTextField(value = pageTitle, onValueChange = { pageTitle = it; vm.update(page, title = it) },
            modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            placeholder = { Text("Page title") }, singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(12.dp))
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
                    val blockType = block.tags.substringAfter("type:", "").substringBefore(";").substringBefore(",")
                    if (blockType == "pdf") {
                        previewPdf = block
                    } else if (blockType == "office") {
                        openOfficeDocument(context, block)
                    } else if (block.tags.contains("type:crosslink")) {
                        val targetId = block.tags.substringAfter("target:", "").substringBefore(",").toLongOrNull()
                        scope.launch { targetId?.let { vm.get(it)?.let(onOpenTopic) } }
                    } else editBlock = block
                }, onBookmark = { vm.toggleBookmark(block) }, onDelete = { vm.removeTree(block) }, onMove = { direction -> vm.move(block, direction) })
            }
            if (blocks.isEmpty()) item { EmptyState("A blank page", "Add rich text, equations, diagrams, images, PDFs, tables and more.") }
            item {
                OutlinedButton(onClick = onAttach, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.AttachFile, null); Spacer(Modifier.width(8.dp)); Text("Attach image, PDF or document")
                }
            }
        }
    }
    if (showReadingMode) NotesReadingMode(
        title = page.title,
        markdown = blocks.filterNot { it.tags.contains("type:crosslink") }.joinToString("\n\n") { block -> "## ${block.title}\n\n${block.body}" },
        onDismiss = { showReadingMode = false }
    )
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
    previewPdf?.let { pdf -> PdfViewerDialog(pdf, vm, onDismiss = { previewPdf = null }) }


}

@Composable
private fun BlockCard(item: ScribeItem, onClick: () -> Unit, onBookmark: () -> Unit, onDelete: () -> Unit, onMove: (Int) -> Unit = {}) {
    val type = item.tags.substringAfter("type:", "").substringBefore(",")
    var menuExpanded by remember(item.id) { mutableStateOf(false) }
    val preview = remember(item.uri, type) { if (type == "image" && item.uri.isNotBlank()) runCatching { BitmapFactory.decodeFile(item.uri)?.asImageBitmap() }.getOrNull() else null }
    val icon = when(type) { "equation" -> Icons.Rounded.Functions; "image" -> Icons.Rounded.Image; "pdf" -> Icons.Rounded.PictureAsPdf; "code" -> Icons.Rounded.Code; "table" -> Icons.Rounded.TableChart; "heading" -> Icons.Rounded.Title; "link" -> Icons.Rounded.Link; else -> Icons.Rounded.Notes }
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp), border = BorderStroke(0.dp, Color.Transparent), modifier = Modifier.fillMaxWidth().scribeGlass(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
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
                if (type == "equation" || type == "code") {
                    Row(Modifier.horizontalScroll(rememberScrollState())) { Text(item.body, color = Color(0xFFE0CDAA), fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp, maxLines = 8, softWrap = false) }
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
            Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp), border = BorderStroke(0.dp, Color.Transparent), modifier = Modifier.fillMaxWidth().scribeGlass(RoundedCornerShape(16.dp)).clickable { onClick(item) }) {
                Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 5.dp, top = 13.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun RichMarkdownEditor(value: String, onValueChange: (String, Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val latestCallback by rememberUpdatedState(onValueChange)
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val target = this
            addJavascriptInterface(object {
                @JavascriptInterface fun onMarkdownChanged(markdown: String, start: Int, length: Int) {
                    target.post { target.tag = markdown; latestCallback(markdown, start, length) }
                }
            }, "ScribeEditor")
            val initial = org.json.JSONObject.quote(value).replace("<", "\\u003C").replace(">", "\\u003E").replace("&", "\\u0026")
            val html = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<script src="https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js"></script>
<style>body{margin:0;padding:12px;background:transparent;color:#e8e3da;font:16px/1.7 sans-serif}#editor{min-height:320px;outline:none;overflow-wrap:anywhere}#editor:empty:before{content:"Start writing…";color:#888}h1,h2,h3{color:#f0d8ad}blockquote{border-left:3px solid #d5b98c;padding-left:12px}pre,code{background:#24272d;padding:4px;border-radius:5px}table{border-collapse:collapse}td,th{border:1px solid #555;padding:5px}</style>
</head><body><div id="editor" contenteditable="true" spellcheck="true"></div><script>
const e=document.getElementById("editor");let last="";
function escapeText(value){const special=String.fromCharCode(92)+"`*_{}[]()#+.!|>~-";const slash=String.fromCharCode(92);return Array.from(value).map(ch=>special.includes(ch)?slash+ch:ch).join("");}
function inlineCode(value){const runs=value.match(/\x60+/g)||[];const longest=runs.reduce((m,x)=>Math.max(m,x.length),0);const fence="\x60".repeat(longest+1);const pad=value.startsWith(" ")||value.endsWith(" ")?" ":"";return fence+pad+value+pad+fence;}
function restoreTaskLists(root){root.querySelectorAll("li").forEach(li=>{const container=li.querySelector(":scope > p")||li;const first=container.firstChild;if(first&&first.nodeType===3){const match=(first.nodeValue||"").match(/^\s*\[([ xX])\]\s+/);if(match){const box=document.createElement("input");box.type="checkbox";box.checked=match[1].toLowerCase()==="x";box.contentEditable="false";box.setAttribute("aria-label",box.checked?"Completed task":"Incomplete task");container.insertBefore(box,first);first.nodeValue=first.nodeValue.slice(match[0].length);}}});}
function md(n){if(n.nodeType===3)return escapeText(n.nodeValue||"");if(n.nodeType===11)return Array.from(n.childNodes).map(md).join("");if(n.nodeType!==1)return "";const t=n.tagName.toLowerCase(),c=()=>Array.from(n.childNodes).map(md).join("");
if(t==="br")return "\n";if(t==="b"||t==="strong")return "**"+c()+"**";if(t==="i"||t==="em")return "*"+c()+"*";if(t==="u")return "<u>"+c()+"</u>";if(t==="s"||t==="del")return "~~"+c()+"~~";
if(/^h[1-6]$/.test(t))return "#".repeat(+t[1])+" "+c()+"\n\n";if(t==="blockquote")return c().split("\n").map(x=>"> "+x).join("\n")+"\n\n";
if(t==="pre"){const code=n.querySelector("code");const cls=code?(code.className||""):"";const match=cls.match(/language-([\w+-]+)/);const lang=match?match[1]:"";const raw=(n.innerText||n.textContent||"").replace(/\n$/,"");const ticks=(raw.match(/`+/g)||[]).reduce((m,x)=>Math.max(m,x.length),0);const fence="`".repeat(Math.max(3,ticks+1));return fence+lang+"\n"+raw+"\n"+fence+"\n\n";}if(t==="a")return "["+c()+"]("+n.getAttribute("href")+")";
if(t==="code"&&(!n.parentElement||n.parentElement.tagName!=="PRE"))return inlineCode(n.textContent||"");if(t==="img")return "!["+((n.getAttribute("alt")||"").replace(/\]/g,"\\]"))+"]("+((n.getAttribute("src")||"").replace(/\)/g,"%29"))+")";
if(t==="input"&&n.type==="checkbox")return n.checked?"[x] ":"[ ] ";
if(t==="li"){const list=n.parentElement;const ordered=list&&list.tagName==="OL";const siblings=list?Array.from(list.children).filter(x=>x.tagName==="LI"):[];const index=siblings.indexOf(n);const marker=ordered?((Number(list.getAttribute("start"))||1)+index)+". ":"- ";const nested=Array.from(n.children).filter(x=>x.tagName==="UL"||x.tagName==="OL");const own=Array.from(n.childNodes).filter(x=>!(x.nodeType===1&&(x.tagName==="UL"||x.tagName==="OL"||(x.tagName==="INPUT"&&x.type==="checkbox")))).map(md).join("").trim();const checkbox=n.querySelector(":scope > input[type=checkbox]");const task=checkbox?(checkbox.checked?"[x] ":"[ ] "):"";const depth=(()=>{let d=0,p=list;while(p&&p!==e){if(p.tagName==="UL"||p.tagName==="OL")d++;p=p.parentElement;}return Math.max(0,d-1);})();const pad="  ".repeat(depth);return pad+marker+task+own+"\n"+nested.map(md).join("");}if(t==="ul"||t==="ol")return Array.from(n.children).filter(x=>x.tagName==="LI").map(md).join("")+"\n";
if(t==="table"){const rows=Array.from(n.querySelectorAll("tr"));if(!rows.length)return "";const cells=r=>Array.from(r.children).map(x=>md(x).trim().replace(/\|/g,"\\|").replace(/\n/g," "));const head=cells(rows[0]);const aligns=Array.from(rows[0].children).map(x=>{const a=x.getAttribute("align")||x.style.textAlign||"";return a==="left"?":---":a==="right"?"---:":a==="center"?":---:":"---";});return "| "+head.join(" | ")+" |\n| "+aligns.join(" | ")+" |\n"+rows.slice(1).map(r=>"| "+cells(r).join(" | ")+" |").join("\n")+"\n\n";}
if(t==="p"||t==="div")return c().trim()+"\n\n";if(t==="hr")return "\n---\n\n";return c();}
function offsets(){const sel=window.getSelection();if(!sel||!sel.rangeCount||!e.contains(sel.anchorNode))return {start:last.length,length:0};const before=document.createRange();before.selectNodeContents(e);before.setEnd(sel.anchorNode,sel.anchorOffset);return {start:md(before.cloneContents()).length,length:md(sel.getRangeAt(0).cloneContents()).length};}
function send(){const v=Array.from(e.childNodes).map(md).join("").replace(/\n{3,}/g,"\n\n").trim();const r=offsets();if(v!==last)last=v;if(window.ScribeEditor)window.ScribeEditor.onMarkdownChanged(v,r.start,r.length);}
window.setMarkdown=function(v){if(v===last)return;last=v;const p=window.markdownit?window.markdownit({html:false,linkify:false,typographer:false}).enable(["table","strikethrough"]):null;if(p){e.style.whiteSpace="normal";e.innerHTML=p.render(v).replace(/&lt;u&gt;([\s\S]*?)&lt;\/u&gt;/g,"<u>$1</u>");restoreTaskLists(e);}else{e.style.whiteSpace="pre-wrap";e.textContent=v;}};
e.addEventListener("input",send);e.addEventListener("keyup",send);e.addEventListener("mouseup",send);e.addEventListener("blur",send);document.addEventListener("selectionchange",()=>{if(document.activeElement===e)send();});window.setMarkdown($initial);
</script></body></html>
""".trimIndent()
            loadDataWithBaseURL("https://scribe.local/", html, "text/html", "UTF-8", null)
            tag = value
        }
    }, update = { webView ->
        if (webView.tag != value) {
            webView.tag = value
            webView.evaluateJavascript("window.setMarkdown(" + org.json.JSONObject.quote(value) + ");", null)
        }
    })
}

@Composable
private fun LatexPreviewWebView(source: String, modifier: Modifier = Modifier) {
    val html = remember(source) { latexPreviewHtml(source) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL("https://scribe.local/", html, "text/html", "UTF-8", null)
                tag = html
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("https://scribe.local/", html, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun latexPreviewHtml(source: String): String {
    val safe = source.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root{color-scheme:dark}body{margin:0;padding:16px;background:transparent;color:#ded8ce;font:16px/1.65 -apple-system,BlinkMacSystemFont,sans-serif}
#content{overflow-wrap:anywhere}#content p{margin:.55em 0}#content h1,#content h2,#content h3{line-height:1.25;margin:1em 0 .45em}#content blockquote{border-left:3px solid #d5b98c;padding-left:12px;color:#bdb5a8;margin-left:0}#content pre{overflow:auto;background:#24272d;padding:12px;border-radius:9px}#content code{font-family:ui-monospace,monospace;background:#24272d;padding:2px 4px;border-radius:4px}#content pre code{padding:0;background:transparent}#content table{border-collapse:collapse;width:100%;display:block;overflow:auto}#content th,#content td{border:1px solid #555;padding:6px 9px}.MathJax{color:#f0d8ad!important}
mjx-container[display="true"]{max-width:100%;overflow-x:auto;overflow-y:hidden;padding:8px 0}
#status{font-size:11px;color:#aaa;margin-top:14px}
</style>
<script src="https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js"></script>
<script>window.MathJax={tex:{inlineMath:[['\\\\(','\\\\)'],['$','$']],displayMath:[['\\\\[','\\\\]'],['$$','$$']],processEscapes:true,packages:{'[+]':['ams']}},options:{skipHtmlTags:['script','noscript','style','textarea','pre','code']}};</script>
<script defer src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js" onerror="document.getElementById('status').textContent='Math rendering needs an internet connection. Your LaTeX source is safe and unchanged.'"></script>
</head><body><div id="content">${safe}</div><div id="status">Rendering mathematics…</div>
<script>window.addEventListener('load',async()=>{const node=document.getElementById('content');if(window.markdownit){const raw=node.textContent;node.innerHTML=window.markdownit({html:false,linkify:false,typographer:true}).render(raw);}else{document.getElementById('status').textContent='Markdown renderer unavailable; showing source text.';}try{if(window.MathJax&&MathJax.startup){await MathJax.startup.promise;await MathJax.typesetPromise([node]);document.getElementById('status').textContent='Markdown and LaTeX preview';}}catch(e){document.getElementById('status').textContent='Some equations could not be rendered.';}});</script>
</body></html>""".trimIndent()
}

@Composable
private fun SearchRow(item: ScribeItem, onClick: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val wordCount = item.body.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val editedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(item.updatedAt))
    ListItem(headlineContent = { Text(item.title) }, supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.body.take(75).ifBlank { "No content yet" }, maxLines = 1)
            Text("$wordCount words · Edited $editedDate", color = Muted, fontSize = 10.sp)
        }
    },
        leadingContent = { Icon(Icons.Rounded.Search, null, tint = Gold) },
        trailingContent = {
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(item.title + if (item.body.isBlank()) "" else "\n\n" + item.body))
            }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy note", tint = Muted) }
        },
        modifier = Modifier.clickable(onClick = onClick),
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
            var preview by remember(item.id) { mutableStateOf(false) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("**", "**") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("B", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("*", "*") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("I", fontStyle = FontStyle.Italic) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("__", "__") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("U", textDecoration = TextDecoration.Underline) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("~~", "~~") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("S", textDecoration = TextDecoration.LineThrough) }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("## ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("H2") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("- ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("List") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("1. ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("1.List") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("- [ ] ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Task") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("> ", "") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Quote") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("[", "](https://)") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Link") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("~~~\n", "\n~~~") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Code") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("| ", " |  |") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Table") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("\\(", "\\)") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Math") }
                TextButton(onClick = { bodyValue = bodyValue.wrapSelection("$$\\n", "\\n$$") }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text("Display") }
                TextButton(onClick = { preview = !preview }, contentPadding = PaddingValues(horizontal = 7.dp)) { Text(if (preview) "Edit" else "Preview") }
            }
            if (preview) LatexPreviewWebView(bodyValue.text, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp))
            else OutlinedTextField(bodyValue, { bodyValue = it }, label = { Text("Description / content") }, minLines = 3, colors = fieldColors())
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

private fun openOfficeDocument(context: Context, item: ScribeItem) {
    val extension = item.tags.substringAfter("format:", "").substringBefore(";").lowercase(Locale.ROOT)
    val mimeType = when (extension) {
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "doc" -> "application/msword"
        "xls" -> "application/vnd.ms-excel"
        "ppt" -> "application/vnd.ms-powerpoint"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
        "odp" -> "application/vnd.oasis.opendocument.presentation"
        "rtf" -> "application/rtf"
        "csv" -> "text/csv"
        else -> "*/*"
    }
    val file = File(item.uri)
    if (!file.isFile) {
        android.widget.Toast.makeText(context, "The original document is no longer available.", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    runCatching {
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(contentUri, mimeType).apply {
            clipData = ClipData.newRawUri(item.title, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(viewIntent, "Open document"))
    }.onFailure {
        android.widget.Toast.makeText(context, "No compatible document viewer was found. Install an Office-compatible viewer to open this file.", android.widget.Toast.LENGTH_LONG).show()
    }
}

private data class PdfHandle(val descriptor: ParcelFileDescriptor, val renderer: PdfRenderer)

@Composable
private fun PdfViewerDialog(item: ScribeItem, vm: ScribeViewModel, onDismiss: () -> Unit) {
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
    var annotationKind by remember(item.id) { mutableStateOf("Highlight") }
    var annotationText by remember(item.id) { mutableStateOf("") }
    var annotationStart by remember(item.id) { mutableStateOf<Offset?>(null) }
    var annotationEnd by remember(item.id) { mutableStateOf<Offset?>(null) }
    var editingAnnotationId by remember(item.id) { mutableStateOf<Long?>(null) }
    var editingAnnotationText by remember(item.id) { mutableStateOf("") }
    val annotations by vm.children(item.id).collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportAnnotatedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
        if (destination != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { exportAnnotatedPdf(context, item, annotations, destination) } }
            android.widget.Toast.makeText(context, if (result.isSuccess) "Annotated PDF exported" else "PDF export failed: ${result.exceptionOrNull()?.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
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
                    val pageBitmap = bitmap!!
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val viewportHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                        val fitScale = minOf(viewportWidth / pageBitmap.width, viewportHeight / pageBitmap.height)
                        val renderedWidth = pageBitmap.width * fitScale
                        val renderedHeight = pageBitmap.height * fitScale
                        val imageLeft = (viewportWidth - renderedWidth) / 2f
                        val imageTop = (viewportHeight - renderedHeight) / 2f
                        fun normalized(point: Offset): Offset = Offset(
                            ((point.x - imageLeft) / renderedWidth).coerceIn(0f, 1f),
                            ((point.y - imageTop) / renderedHeight).coerceIn(0f, 1f)
                        )
                        Image(pageBitmap.asImageBitmap(), contentDescription = "PDF page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        Canvas(Modifier.fillMaxSize().pointerInput(pageIndex, renderedWidth, renderedHeight) {
                            detectDragGestures(
                                onDragStart = { point -> annotationStart = normalized(point); annotationEnd = normalized(point) },
                                onDragEnd = { },
                                onDragCancel = { annotationStart = null; annotationEnd = null }
                            ) { change, _ ->
                                annotationEnd = normalized(change.position)
                                change.consume()
                            }
                        }) {
                            fun drawAnnotation(rect: Offset, end: Offset, color: Color, filled: Boolean) {
                                val left = imageLeft + minOf(rect.x, end.x) * renderedWidth
                                val top = imageTop + minOf(rect.y, end.y) * renderedHeight
                                val width = kotlin.math.abs(end.x - rect.x) * renderedWidth
                                val height = kotlin.math.abs(end.y - rect.y) * renderedHeight
                                if (width > 1f && height > 1f) {
                                    drawRect(color = color, topLeft = Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(width, height),
                                        style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = 3f))
                                }
                            }
                            annotations.filter { it.tags.contains("type:pdf_annotation") && it.tags.contains("page:${pageIndex + 1}") }.forEach { saved ->
                                val values = saved.tags.split(";").firstOrNull { it.startsWith("rect:") }
                                    ?.removePrefix("rect:")?.split(",")?.mapNotNull { it.toFloatOrNull() }
                                if (values != null && values.size == 4) {
                                    val a = Offset(values[0], values[1]); val z = Offset(values[0] + values[2], values[1] + values[3])
                                    when {
                                        saved.tags.contains("annotation:underline") -> {
                                            val x1 = imageLeft + a.x * renderedWidth
                                            val x2 = imageLeft + z.x * renderedWidth
                                            val y = imageTop + z.y * renderedHeight
                                            drawLine(Color(0xFF55A8FF), Offset(x1, y), Offset(x2, y), strokeWidth = 3f)
                                        }
                                        saved.tags.contains("annotation:markup") -> drawAnnotation(a, z, Color(0xFFFF7777), false)
                                        else -> drawAnnotation(a, z, Color(0xFFFFD54F).copy(alpha = 0.35f), true)
                                    }
                                }
                            }
                            if (annotationStart != null && annotationEnd != null) {
                                drawAnnotation(annotationStart!!, annotationEnd!!, Gold.copy(alpha = 0.9f), false)
                            }
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Annotate", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    listOf("Highlight", "Underline", "Note", "Markup").forEach { kind ->
                        FilterChip(selected = annotationKind == kind, onClick = { annotationKind = kind },
                            label = { Text(kind, fontSize = 10.sp) })
                    }
                }
                OutlinedTextField(annotationText, { annotationText = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Annotation for page ${pageIndex + 1}") }, minLines = 1, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = annotationText.isNotBlank() && annotationStart != null && annotationEnd != null, onClick = {
                        val start = annotationStart ?: return@Button
                        val end = annotationEnd ?: return@Button
                        val x = minOf(start.x, end.x); val y = minOf(start.y, end.y)
                        val w = kotlin.math.abs(end.x - start.x); val h = kotlin.math.abs(end.y - start.y)
                        if (w < 0.003f || h < 0.003f) return@Button
                        val rect = "rect:${x},${y},${w},${h}"
                        vm.add(item.id, "block", "${annotationKind} · Page ${pageIndex + 1}",
                            body = annotationText.trim(), tags = "type:pdf_annotation;annotation:${annotationKind.lowercase()};page:${pageIndex + 1};${rect}")
                        annotationText = ""
                        annotationStart = null
                        annotationEnd = null
                    }) { Text("Save page mark") }
                    Text("${annotations.count { it.tags.contains("type:pdf_annotation") }} saved", color = Muted, fontSize = 11.sp)
                    OutlinedButton(onClick = { exportAnnotatedLauncher.launch(item.title.substringBeforeLast(".") + "-annotated.pdf") }) {
                        Icon(Icons.Rounded.FileDownload, null); Text("Export PDF")
                    }
                }
                annotations.filter { it.tags.contains("type:pdf_annotation") && it.tags.contains("page:${pageIndex + 1}") }.forEach { note ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF24272D))) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(9.dp)) {
                            Text(note.title, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            if (editingAnnotationId == note.id) {
                                OutlinedTextField(
                                    value = editingAnnotationText,
                                    onValueChange = { editingAnnotationText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 1, maxLines = 4,
                                    label = { Text("Annotation text") },
                                    colors = fieldColors()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = {
                                        vm.update(note, body = editingAnnotationText)
                                        editingAnnotationId = null
                                    }) { Text("Save") }
                                    TextButton(onClick = { editingAnnotationId = null }) { Text("Cancel") }
                                }
                            } else {
                                Text(note.body, fontSize = 12.sp, maxLines = 3)
                            }
                        }
                        if (editingAnnotationId != note.id) {
                            IconButton(onClick = {
                                editingAnnotationId = note.id
                                editingAnnotationText = note.body
                            }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit annotation", tint = Muted) }
                        }
                        IconButton(onClick = { vm.removeTree(note) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete annotation", tint = Muted)
                        }
                        }
                    }
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


private fun exportAnnotatedPdf(context: Context, item: ScribeItem, annotations: List<ScribeItem>, destination: Uri) {
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
    val source = File(item.uri)
    require(source.isFile) { "The original PDF file is missing." }
    val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(source)
    try {
        annotations.filter { it.tags.contains("type:pdf_annotation") }.forEach { annotation ->
            val pageNumber = annotation.tags.split(";").firstOrNull { it.startsWith("page:") }
                ?.substringAfter("page:")?.toIntOrNull() ?: return@forEach
            val page = document.getPage(pageNumber - 1)
            val values = annotation.tags.split(";").firstOrNull { it.startsWith("rect:") }
                ?.removePrefix("rect:")?.split(",")?.mapNotNull { it.toFloatOrNull() }
            if (values == null || values.size != 4) return@forEach
            val media = page.mediaBox
            val pageWidth = media.width
            val pageHeight = media.height
            val left = media.lowerLeftX + values[0].coerceIn(0f, 1f) * pageWidth
            val top = media.lowerLeftY + pageHeight - values[1].coerceIn(0f, 1f) * pageHeight
            val right = media.lowerLeftX + (values[0] + values[2]).coerceIn(0f, 1f) * pageWidth
            val bottom = media.lowerLeftY + pageHeight - (values[1] + values[3]).coerceIn(0f, 1f) * pageHeight
            val rect = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(
                minOf(left, right), minOf(bottom, top),
                maxOf(1f, kotlin.math.abs(right - left)), maxOf(1f, kotlin.math.abs(top - bottom))
            )
            val kind = annotation.tags.split(";").firstOrNull { it.startsWith("annotation:") }
                ?.substringAfter(":").orEmpty()
            val color = when (kind) {
                "underline" -> com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                    floatArrayOf(0.25f, 0.57f, 0.95f),
                    com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
                )
                "markup" -> com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                    floatArrayOf(0.88f, 0.25f, 0.25f),
                    com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
                )
                else -> com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                    floatArrayOf(1f, 0.82f, 0.18f),
                    com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
                )
            }
            when (kind) {
                "highlight", "underline" -> {
                    val mark = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(
                        if (kind == "underline")
                            com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
                        else
                            com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                    )
                    mark.rectangle = rect
                    mark.color = color
                    mark.quadPoints = floatArrayOf(left, top, right, top, left, bottom, right, bottom)
                    mark.contents = annotation.body
                    page.annotations.add(mark)
                }
                "note" -> {
                    val note = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
                    note.rectangle = rect
                    note.contents = annotation.body
                    note.color = color
                    note.titlePopup = "Scribe"
                    page.annotations.add(note)
                }
                else -> {
                    val square = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle(com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle.SUB_TYPE_SQUARE)
                    square.rectangle = rect
                    square.color = color
                    square.contents = annotation.body
                    page.annotations.add(square)
                }
            }
        }
        val temporary = File(context.cacheDir, "scribe-annotated-" + System.currentTimeMillis() + ".pdf")
        document.save(temporary)
        context.contentResolver.openOutputStream(destination)?.use { output ->
            temporary.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not open the destination file")
        temporary.delete()
    } finally {
        document.close()
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
