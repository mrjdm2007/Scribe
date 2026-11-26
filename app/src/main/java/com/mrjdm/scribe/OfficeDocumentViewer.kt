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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

private const val OfficeGold = 0xFFD5B98C
private const val OfficeMuted = 0xFF96938D

@Composable
internal fun OfficeDocumentViewer(item: ScribeItem, onDismiss: () -> Unit) {
    val result by produceState<Pair<OfficeDocument?, String>?>(null, item.uri, item.tags) {
        value = withContext(Dispatchers.IO) {
            runCatching { readOfficeDocument(File(item.uri), item.tags.substringAfter("type:", "").substringBefore(",")) }
                .fold({ it to "" }, { null to (it.localizedMessage ?: "This document could not be opened.") })
        }
    }
    var selectedSheet by remember(item.id) { mutableIntStateOf(0) }
    var selectedSlide by remember(item.id) { mutableIntStateOf(0) }
    val gold = Color(OfficeGold)
    val muted = Color(OfficeMuted)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17191D))
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            when (item.tags.substringAfter("type:", "").substringBefore(",")) {
                                "docx" -> "WORD DOCUMENT · READ ONLY"
                                "xlsx" -> "EXCEL WORKBOOK · READ ONLY"
                                else -> "POWERPOINT PRESENTATION · READ ONLY"
                            },
                            color = muted, fontSize = 10.sp, letterSpacing = 1.sp
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close document") }
                }
                HorizontalDivider(color = Color(0xFF33363B))
                when {
                    result == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = gold)
                    }
                    result?.first == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(result?.second ?: "This document could not be opened.", color = muted)
                    }
                    else -> {
                        val document = result!!.first!!
                        when (document.type) {
                            "docx" -> {
                                if (document.paragraphs.isEmpty()) {
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("No readable text was found in this document.", color = muted)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        items(document.paragraphs) { paragraph ->
                                            Text(paragraph, color = Color(0xFFE6E2DA), fontSize = 14.sp, lineHeight = 21.sp)
                                        }
                                    }
                                }
                            }
                            "xlsx" -> {
                                if (document.sheets.isEmpty()) {
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("No readable worksheets were found.", color = muted)
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        document.sheets.forEachIndexed { index, sheet ->
                                            FilterChip(
                                                selected = selectedSheet == index,
                                                onClick = { selectedSheet = index },
                                                label = { Text(sheet.name, maxLines = 1) }
                                            )
                                        }
                                    }
                                    val sheet = document.sheets.getOrNull(selectedSheet.coerceIn(document.sheets.indices))
                                    if (sheet != null) {
                                        Text("${sheet.name} · ${sheet.rows.size} rows", color = muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 7.dp))
                                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            items(sheet.rows) { row ->
                                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                                    row.forEachIndexed { index, cell ->
                                                        Surface(
                                                            modifier = Modifier.width(126.dp).heightIn(min = 38.dp),
                                                            color = if (index == 0) Color(0xFF292721) else Color(0xFF202226)
                                                        ) {
                                                            Text(cell, Modifier.padding(horizontal = 8.dp, vertical = 9.dp), color = Color(0xFFE6E2DA), fontSize = 12.sp, maxLines = 5)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                val slide = document.slides.getOrNull(selectedSlide)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    IconButton(enabled = selectedSlide > 0, onClick = { selectedSlide-- }) { Icon(Icons.Rounded.ChevronLeft, "Previous slide", tint = gold) }
                                    Text("SLIDE ${selectedSlide + 1} OF ${document.slides.size}", color = muted, fontSize = 11.sp, letterSpacing = 1.sp)
                                    IconButton(enabled = selectedSlide < document.slides.lastIndex, onClick = { selectedSlide++ }) { Icon(Icons.Rounded.ChevronRight, "Next slide", tint = gold) }
                                }
                                if (slide == null || slide.paragraphs.isEmpty()) {
                                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("No readable text on this slide.", color = muted)
                                    }
                                } else {
                                    Column(
                                        Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF4F1EA), RoundedCornerShape(10.dp)).padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(13.dp)
                                    ) {
                                        slide.paragraphs.forEachIndexed { index, paragraph ->
                                            Text(
                                                paragraph,
                                                color = Color(0xFF222222),
                                                fontSize = if (index == 0) 21.sp else 15.sp,
                                                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                lineHeight = 23.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Read-only preview · Files stay stored in Scribe", color = muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
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
        parserFor(zip, path) { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && rows.size < 500) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> cells = sortedMapOf()
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
                            val column = columnIndex(cellRef).coerceIn(0, 39)
                            val displayed = when (cellType) {
                                "s" -> sharedStrings.getOrNull(cellValue.toIntOrNull() ?: -1) ?: cellValue
                                "inlineStr" -> cellValue
                                else -> cellValue.ifBlank { if (formula.isNotBlank()) "=$formula" else "" }
                            }
                            cells[column] = displayed
                        }
                        "row" -> if (cells.isNotEmpty()) {
                            val max = minOf(cells.lastKey(), 39)
                            rows += (0..max).map { cells[it].orEmpty() }
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
