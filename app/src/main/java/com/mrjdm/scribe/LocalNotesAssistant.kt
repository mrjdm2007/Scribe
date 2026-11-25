package com.mrjdm.scribe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrjdm.scribe.data.ScribeItem
import com.mrjdm.scribe.data.ScribeViewModel

@Composable
fun LocalNotesAssistantScreen(vm: ScribeViewModel) {
    var allItems by remember { mutableStateOf<List<ScribeItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selectedCourses by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(vm) { allItems = runCatching { vm.all() }.getOrDefault(emptyList()) }

    val visible = allItems.filter { it.deletedAt == null }
    val courses = visible.filter { it.kind == "course" }.sortedBy { it.position }
    val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }

    fun rootCourseId(item: ScribeItem): Long {
        var current = item
        val visited = mutableSetOf<Long>()
        while (current.parentId != null && visited.add(current.parentId!!)) {
            val parent = visible.firstOrNull { it.id == current.parentId } ?: break
            current = parent
        }
        return current.id
    }
    fun citation(item: ScribeItem): String {
        val labels = mutableListOf(item.title.ifBlank { item.kind.replaceFirstChar { it.uppercase() } })
        var current = item
        val visited = mutableSetOf(current.id)
        while (current.parentId != null && visited.add(current.parentId!!)) {
            val parent = visible.firstOrNull { it.id == current.parentId } ?: break
            labels += parent.title.ifBlank { parent.kind.replaceFirstChar { it.uppercase() } }
            current = parent
        }
        return labels.asReversed().joinToString(" › ")
    }
    val results = remember(visible, words, selectedCourses) {
        visible.asSequence()
            .filter { it.uri.isBlank() }
            .filter { it.kind != "block" || (!it.tags.contains("type:image") && !it.tags.contains("type:pdf")) }
            .filter { selectedCourses.isEmpty() || rootCourseId(it) in selectedCourses }
            .map { item ->
                val title = item.title.lowercase()
                val body = item.body.lowercase()
                val tags = item.tags.lowercase()
                val score = words.sumOf { word ->
                    (if (title.contains(word)) 5 else 0) +
                        (if (body.contains(word)) 2 else 0) +
                        (if (tags.contains(word)) 1 else 0)
                }
                item to score
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<ScribeItem, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .take(20).toList()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ask my notes", style = MaterialTheme.typography.headlineMedium)
        Text("Search saved text locally, choose which notebooks to include, and trace every result to its source. No AI API key or internet connection required.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Question or topic") }, placeholder = { Text("e.g. Kirchhoff's voltage law, semiconductor doping…") })
        Text("NOTEBOOK CONTEXT", style = MaterialTheme.typography.labelLarge)
        Text("Choose courses to narrow the search. Leave all unselected to search every notebook.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        courses.forEach { course ->
            FilterChip(selected = course.id in selectedCourses, onClick = {
                selectedCourses = if (course.id in selectedCourses) selectedCourses - course.id else selectedCourses + course.id
            }, label = { Text(course.title) })
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("NOTE SOURCES", style = MaterialTheme.typography.labelLarge)
            Text("${results.size} matches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            words.isEmpty() -> Text("Enter a topic or question to find relevant saved text.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            results.isEmpty() -> Text("No matching notes. Try different keywords or expand the selected notebook context.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> results.forEach { (item, score) ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title.ifBlank { item.kind }, style = MaterialTheme.typography.titleMedium)
                            Text("Relevance $score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.body.isNotBlank()) Text(item.body, maxLines = 5)
                        Text("Source: ${citation(item)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Text("This is local retrieval, not a generated AI answer. Photos, PDFs, attachment previews, and OCR text are excluded.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
