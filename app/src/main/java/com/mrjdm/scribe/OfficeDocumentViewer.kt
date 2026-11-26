package com.mrjdm.scribe

import com.mrjdm.scribe.data.ScribeItem
import android.util.Xml
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

private data class OfficeSheet(val name: String, val rows: List<List<String>>)
private data class OfficeSlide(val paragraphs: List<String>)
private data class OfficeDocument(
    val type: String,
    val paragraphs: List<String> = emptyList(),
    val sheets: List<OfficeSheet> = emptyList(),
    val slides: List<OfficeSlide> = emptyList()
)

private val ReaderBackground = Color(0xFF111216)
private val ReaderBar = Color(0xFF191A1F)
private val ReaderMuted = Color(0xFF9B9DA5)
private val ReaderAccent = Color(0xFFD5B98C)
private val Paper = Color(0xFFFAF9F6)
private val Ink = Color(0xFF242424)

@Composable
internal fun OfficeDocumentViewer(item: ScribeItem, onDismiss: () -> Unit) {
    val type = item.tags.substringAfter("type:", "").substringBefore(",")
    val result by produceState<Pair<OfficeDocument?, String>?>(null, item.uri, type) {
        value = withContext(Dispatchers.IO) {
            runCatching { readOfficeDocument(File(item.uri), type) }
                .fold({ it to "" }, { null to (it.localizedMessage ?: "This document could not be opened.") })
        }
    }
    var selectedSheet by remember(item.id) { mutableIntStateOf(0) }
    var selectedSlide by remember(item.id) { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ReaderBackground) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(ReaderBar).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color(0xFFF3F1EC), fontSize = 15.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            when (type) {
                                "docx" -> "WORD DOCUMENT"
                                "xlsx" -> "EXCEL WORKBOOK"
                                "pptx" -> "POWERPOINT PRESENTATION"
                                else -> "DOCUMENT"
                            },
                            color = ReaderMuted, fontSize = 9.sp, letterSpacing = 1.25.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close document", tint = Color(0xFFE8E6E1))
                    }
                }
                HorizontalDivider(color = Color(0xFF2A2B30))
                when {
                    result == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ReaderAccent)
                    }
                    result?.first == null -> Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text(result?.second ?: "This document could not be opened.", color = ReaderMuted, textAlign = TextAlign.Center)
                    }
                    else -> {
                        val document = result!!.first!!
                        when (document.type) {
                            "docx" -> WordReadingSurface(document.paragraphs)
                            "xlsx" -> SpreadsheetReadingSurface(document.sheets, selectedSheet) { selectedSheet = it }
                            "pptx" -> PresentationReadingSurface(document.slides, selectedSlide,
                                onSelectSlide = { selectedSlide = it },
                                onPrevious = { selectedSlide = (selectedSlide - 1).coerceAtLeast(0) },
                                onNext = { selectedSlide = (selectedSlide + 1).coerceAtMost(document.slides.lastIndex) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordReadingSurface(paragraphs: List<String>) {
    var fontSize by remember { mutableIntStateOf(16) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val visibleParagraphs = remember(paragraphs, searchText) {
        if (searchText.isBlank()) paragraphs
        else paragraphs.filter { it.contains(searchText, ignoreCase = true) }
    }
    if (paragraphs.isEmpty()) {
        EmptyDocumentMessage("No readable text was found in this document.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(ReaderBar).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${paragraphs.size} paragraphs", color = ReaderMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchText = "" }) {
                Icon(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchVisible) "Close search" else "Find in document",
                    tint = Color(0xFFE8E6E1))
            }
            IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(13) }, enabled = fontSize > 13) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease text size", tint = ReaderAccent)
            }
            Text("A", color = Color(0xFFE8E6E1), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 2.dp))
            IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(23) }, enabled = fontSize < 23) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase text size", tint = ReaderAccent)
            }
        }
        if (searchVisible) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                singleLine = true,
                placeholder = { Text("Find text in this document") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ReaderAccent, unfocusedBorderColor = Color(0xFF45464A),
                    focusedTextColor = Color(0xFFF3F1EC), unfocusedTextColor = Color(0xFFF3F1EC),
                    cursorColor = ReaderAccent
                )
            )
        }
        if (searchText.isNotBlank()) {
            Text("${visibleParagraphs.size} matching paragraphs", color = ReaderMuted, fontSize = 10.sp,
                modifier = Modifier.padding(start = 18.dp, bottom = 5.dp))
        }
        if (visibleParagraphs.isEmpty()) {
            EmptyDocumentMessage("No matching text was found.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(visibleParagraphs) { paragraph ->
                    Column(
                        Modifier.fillMaxWidth().background(Paper).padding(horizontal = 24.dp, vertical = 9.dp)
                    ) {
                        Text(paragraph, color = Ink, fontFamily = FontFamily.Serif,
                            fontSize = fontSize.sp, lineHeight = (fontSize + 11).sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetReadingSurface(sheets: List<OfficeSheet>, selectedSheet: Int, onSheetSelected: (Int) -> Unit) {
    var searchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    if (sheets.isEmpty()) {
        EmptyDocumentMessage("No readable worksheets were found.")
        return
    }
    val sheet = sheets.getOrNull(selectedSheet.coerceIn(0, sheets.lastIndex))
    val matchingRows = remember(sheet, searchText) {
        val rows = sheet?.rows.orEmpty()
        if (searchText.isBlank()) rows.mapIndexed { index, row -> index to row }
        else rows.mapIndexedNotNull { index, row ->
            if (row.any { it.contains(searchText, ignoreCase = true) }) index to row else null
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(ReaderBar).horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            sheets.forEachIndexed { index, item ->
                FilterChip(
                    selected = selectedSheet == index,
                    onClick = { onSheetSelected(index); searchText = "" },
                    label = { Text(item.name, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF34302A),
                        selectedLabelColor = ReaderAccent,
                        labelColor = Color(0xFFE2E0DA)
                    )
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(ReaderBar).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${sheet?.rows?.size ?: 0} rows", color = ReaderMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (searchText.isNotBlank()) {
                Text("${matchingRows.size} matches", color = ReaderAccent, fontSize = 10.sp)
            }
            IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchText = "" }) {
                Icon(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchVisible) "Close worksheet search" else "Search worksheet",
                    tint = Color(0xFFE8E6E1))
            }
        }
        if (searchVisible) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                singleLine = true,
                placeholder = { Text("Find in this worksheet") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ReaderAccent, unfocusedBorderColor = Color(0xFF45464A),
                    focusedTextColor = Color(0xFFF3F1EC), unfocusedTextColor = Color(0xFFF3F1EC),
                    cursorColor = ReaderAccent
                )
            )
        }
        if (sheet != null) {
            if (matchingRows.isEmpty()) {
                EmptyDocumentMessage("No matching cells were found in this worksheet.")
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF1F0EC)),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(matchingRows, key = { it.first }) { (rowIndex, row) ->
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Surface(
                                Modifier.width(48.dp).heightIn(min = 42.dp),
                                color = Color(0xFFE0DED8), shape = RoundedCornerShape(1.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text((rowIndex + 1).toString(), color = ReaderMuted, fontSize = 11.sp)
                                }
                            }
                            row.forEachIndexed { columnIndex, cell ->
                                Surface(
                                    Modifier.width(132.dp).heightIn(min = 42.dp),
                                    color = if (rowIndex == 0) Color(0xFFE7E4DD) else Paper,
                                    shape = RoundedCornerShape(1.dp)
                                ) {
                                    Text(
                                        cell,
                                        Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
                                        color = Ink, fontSize = 12.sp, lineHeight = 17.sp,
                                        fontWeight = if (rowIndex == 0 || columnIndex == 0) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresentationReadingSurface(
    slides: List<OfficeSlide>,
    selectedSlide: Int,
    onSelectSlide: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var textSize by remember { mutableIntStateOf(16) }
    if (slides.isEmpty()) {
        EmptyDocumentMessage("No readable slides were found.")
        return
    }
    val slide = slides.getOrNull(selectedSlide)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(ReaderBar).horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slides.forEachIndexed { index, _ ->
                FilterChip(
                    selected = selectedSlide == index,
                    onClick = { onSelectSlide(index) },
                    label = { Text((index + 1).toString()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF34302A),
                        selectedLabelColor = ReaderAccent,
                        labelColor = Color(0xFFE2E0DA)
                    )
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(ReaderBar).padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Slide ${selectedSlide + 1} of ${slides.size}", color = ReaderMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { textSize = (textSize - 1).coerceAtLeast(12) }, enabled = textSize > 12) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease slide text size", tint = ReaderAccent)
            }
            Text("A", color = Color(0xFFE8E6E1), fontSize = 13.sp)
            IconButton(onClick = { textSize = (textSize + 1).coerceAtMost(30) }, enabled = textSize < 30) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase slide text size", tint = ReaderAccent)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
            Surface(
                Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                color = Paper, shape = RoundedCornerShape(3.dp), shadowElevation = 3.dp
            ) {
                if (slide == null || slide.paragraphs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No readable text on this slide.", color = ReaderMuted)
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        slide.paragraphs.forEachIndexed { index, paragraph ->
                            Text(
                                paragraph, color = Ink,
                                fontSize = if (index == 0) (textSize + 6).sp else textSize.sp,
                                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                                lineHeight = (textSize + 7).sp,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(ReaderBar).padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onPrevious, enabled = selectedSlide > 0) {
                Icon(Icons.Rounded.ChevronLeft, null); Text("Previous")
            }
            TextButton(onClick = onNext, enabled = selectedSlide < slides.lastIndex) {
                Text("Next"); Icon(Icons.Rounded.ChevronRight, null)
            }
        }
    }
}

@Composable
private fun EmptyDocumentMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(message, color = ReaderMuted, textAlign = TextAlign.Center)
    }
}

private fun readOfficeDocument(file: File, type: String): OfficeDocument {
    require(file.isFile && file.length() > 0L) { "The saved document is missing or empty." }
    ZipFile(file).use { zip ->
        return when (type) {
            "docx" -> OfficeDocument(type = type, paragraphs = readDocx(zip))
            "xlsx" -> OfficeDocument(type = type, sheets = readXlsx(zip))
            "pptx" -> OfficeDocument(type = type, slides = readPptx(zip))
            else -> error("Unsupported Office format.")
        }
    }
}

private fun parserFor(zip: ZipFile, path: String, consume: (XmlPullParser) -> Unit) {
    val entry = zip.getEntry(path) ?: return
    zip.getInputStream(entry).use { input ->
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        consume(parser)
    }
}

private fun readDocx(zip: ZipFile): List<String> {
    val paragraphs = mutableListOf<String>()
    var current = StringBuilder()
    var inParagraph = false
    parserFor(zip, "word/document.xml") { parser ->
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> { current = StringBuilder(); inParagraph = true }
                    "t" -> if (inParagraph) current.append(parser.nextText())
                    "tab" -> if (inParagraph) current.append('\t')
                    "br" -> if (inParagraph) current.append('\n')
                }
                XmlPullParser.END_TAG -> if (parser.name == "p" && inParagraph) {
                    val text = current.toString().trim()
                    if (text.isNotEmpty()) paragraphs += text
                    inParagraph = false
                }
            }
            event = parser.next()
        }
    }
    return paragraphs
}

private fun readXlsx(zip: ZipFile): List<OfficeSheet> {
    val sharedStrings = mutableListOf<String>()
    parserFor(zip, "xl/sharedStrings.xml") { parser ->
        var inString = false
        var value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inString = true; value = StringBuilder() }
                    "t" -> if (inString) value.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si" && inString) {
                    sharedStrings += value.toString()
                    inString = false
                }
            }
            event = parser.next()
        }
    }

    val relationships = mutableMapOf<String, String>()
    parserFor(zip, "xl/_rels/workbook.xml.rels") { parser ->
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    relationships[id] = when {
                        target.startsWith("/") -> target.removePrefix("/")
                        target.startsWith("xl/") -> target
                        else -> "xl/" + target.removePrefix("./")
                    }
                }
            }
            event = parser.next()
        }
    }

    val sheetRefs = mutableListOf<Pair<String, String>>()
    parserFor(zip, "xl/workbook.xml") { parser ->
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet ${sheetRefs.size + 1}"
                val relId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: parser.getAttributeValue(null, "r:id")
                if (relId != null) relationships[relId]?.let { sheetRefs += name to it }
            }
            event = parser.next()
        }
    }

    return sheetRefs.map { (name, path) ->
        val rows = mutableListOf<List<String>>()
        var cells = sortedMapOf<Int, String>()
        var cellRef = ""
        var cellType = ""
        var cellValue = ""
        var formula = ""
        var sourceRow = 0
        parserFor(zip, path) { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && rows.size < 500) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> {
                            cells = sortedMapOf()
                            sourceRow = parser.getAttributeValue(null, "r")?.toIntOrNull()?.coerceAtLeast(1)
                                ?: (rows.size + 1)
                        }
                        "c" -> {
                            cellRef = parser.getAttributeValue(null, "r") ?: ""
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue = ""
                            formula = ""
                        }
                        "v" -> cellValue = parser.nextText()
                        "f" -> formula = parser.nextText()
                        "t" -> if (cellType == "inlineStr") cellValue += parser.nextText()
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "c" -> {
                            val column = columnIndex(cellRef)
                            val displayed = when (cellType) {
                                "s" -> sharedStrings.getOrNull(cellValue.toIntOrNull() ?: -1) ?: cellValue
                                "inlineStr" -> cellValue
                                "b" -> if (cellValue == "1") "TRUE" else if (cellValue == "0") "FALSE" else cellValue
                                else -> cellValue.ifBlank { if (formula.isNotBlank()) "=$formula" else "" }
                            }
                            // The grid is intentionally bounded to A–CV. Never shift a
                            // later column into the visible range because that changes meaning.
                            if (column in 0..99) cells[column] = displayed
                        }
                        "row" -> {
                            // Keep original worksheet row positions. Sparse sheets often contain
                            // blank rows; dropping them makes every later value appear on the wrong row.
                            val targetIndex = (sourceRow - 1).coerceAtMost(499)
                            while (rows.size < targetIndex) rows += emptyList()
                            if (rows.size < 500) {
                                val max = cells.keys.lastOrNull()
                                rows += if (max == null) emptyList() else (0..max).map { cells[it].orEmpty() }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }
        OfficeSheet(name, rows)
    }
}

private fun columnIndex(reference: String): Int {
    val letters = reference.takeWhile { it.isLetter() }.uppercase()
    if (letters.isEmpty()) return 0
    var result = 0
    letters.forEach { result = result * 26 + (it - 'A' + 1) }
    return (result - 1).coerceAtLeast(0)
}

private fun readPptx(zip: ZipFile): List<OfficeSlide> {
    val relationships = mutableMapOf<String, String>()
    parserFor(zip, "ppt/_rels/presentation.xml.rels") { parser ->
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    relationships[id] = if (target.startsWith("/")) target.removePrefix("/") else "ppt/" + target.removePrefix("./")
                }
            }
            event = parser.next()
        }
    }
    val slidePaths = mutableListOf<String>()
    parserFor(zip, "ppt/presentation.xml") { parser ->
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sldId") {
                val id = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: parser.getAttributeValue(null, "r:id")
                if (id != null) relationships[id]?.let { slidePaths += it }
            }
            event = parser.next()
        }
    }

    return slidePaths.take(300).map { path ->
        val paragraphs = mutableListOf<String>()
        var current = StringBuilder()
        parserFor(zip, path) { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "p" -> current = StringBuilder()
                        "t" -> current.append(parser.nextText())
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "p") {
                        val text = current.toString().trim()
                        if (text.isNotEmpty()) paragraphs += text
                    }
                }
                event = parser.next()
            }
        }
        OfficeSlide(paragraphs)
    }
}
