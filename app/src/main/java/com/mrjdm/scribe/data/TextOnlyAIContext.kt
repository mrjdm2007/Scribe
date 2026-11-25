package com.mrjdm.scribe.data

/**
 * Builds the only notebook context that may be passed to an AI provider.
 * Attachment nodes and all file URIs are deliberately excluded; no PDF extraction,
 * OCR, image decoding, or attachment bytes are used here.
 */
object TextOnlyAIContext {
    private val supportedKinds = setOf("course", "chapter", "topic", "page", "block", "formula", "question", "flashcard", "diagram", "study_question")
    private val attachmentTypes = setOf("image", "pdf")

    fun build(items: List<ScribeItem>, maxCharacters: Int = 100_000): String {
        require(maxCharacters > 0)
        val visible = items.filter { item ->
            item.deletedAt == null && item.kind in supportedKinds && item.uri.isBlank() && !isAttachment(item)
        }.sortedWith(compareBy<ScribeItem> { it.position }.thenBy { it.createdAt })
        val byId = visible.associateBy { it.id }
        val depthCache = mutableMapOf<Long, Int>()
        fun depth(item: ScribeItem): Int {
            depthCache[item.id]?.let { return it }
            val parent = item.parentId?.let(byId::get)
            val value = if (parent == null) 0 else (depth(parent) + 1).coerceAtMost(8)
            depthCache[item.id] = value
            return value
        }
        val output = StringBuilder()
        for (item in visible) {
            val title = item.title.trim()
            val body = item.body.trim()
            if (title.isEmpty() && body.isEmpty()) continue
            val block = buildString {
                append("  ".repeat(depth(item)))
                append("[").append(item.kind).append("] ").append(title)
                if (body.isNotEmpty()) append("\n").append("  ".repeat(depth(item) + 1)).append(body)
            }
            val extra = block.length + if (output.isEmpty()) 0 else 2
            if (output.length + extra > maxCharacters) break
            if (output.isNotEmpty()) output.append("\n\n")
            output.append(block)
        }
        return output.toString()
    }

    private fun isAttachment(item: ScribeItem): Boolean {
        if (item.kind != "block") return false
        val type = item.tags.split(';', ',').firstOrNull { it.trim().startsWith("type:") }
            ?.trim()?.removePrefix("type:")
        return type != null && type in attachmentTypes
    }
}