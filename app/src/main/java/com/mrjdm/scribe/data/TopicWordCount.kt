package com.mrjdm.scribe.data

object TopicWordCount {
    fun count(items: List<ScribeItem>, topicId: Long): Int {
        val pageIds = items.asSequence()
            .filter { it.parentId == topicId && it.kind == "page" && it.deletedAt == null }
            .map { it.id }
            .toSet()
        return items.asSequence()
            .filter {
                it.parentId != null && it.parentId in pageIds && it.deletedAt == null &&
                    it.uri.isBlank() && !it.tags.contains("type:image") && !it.tags.contains("type:pdf")
            }
            .sumOf { note ->
                note.body.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            }
    }
}
