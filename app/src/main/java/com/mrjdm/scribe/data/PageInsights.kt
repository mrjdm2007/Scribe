package com.mrjdm.scribe.data

import kotlin.math.ceil

data class PageInsights(
    val wordCount: Int,
    val estimatedReadingMinutes: Int,
    val attachmentCount: Int
) {
    companion object {
        fun calculate(blocks: List<ScribeItem>): PageInsights {
            val visible = blocks.filter { it.deletedAt == null }
            val textBlocks = visible.filter { block ->
                val type = block.tags.split(';', ',').firstOrNull { it.trim().startsWith("type:") }
                    ?.trim()?.removePrefix("type:")
                type != "image" && type != "pdf" && block.uri.isBlank()
            }
            val words = textBlocks.sumOf { block ->
                block.body.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            }
            val attachments = visible.count { block ->
                val type = block.tags.split(';', ',').firstOrNull { it.trim().startsWith("type:") }
                    ?.trim()?.removePrefix("type:")
                type == "image" || type == "pdf"
            }
            return PageInsights(words, if (words == 0) 0 else ceil(words / 200.0).toInt(), attachments)
        }
    }
}
