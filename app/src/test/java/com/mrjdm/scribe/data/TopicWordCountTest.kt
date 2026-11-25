package com.mrjdm.scribe.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicWordCountTest {
    @Test fun countsTextAcrossEveryPageInTopic() {
        val topic = ScribeItem(id = 1, kind = "topic", title = "Topic")
        val pageA = ScribeItem(id = 2, parentId = 1, kind = "page", title = "A")
        val pageB = ScribeItem(id = 3, parentId = 1, kind = "page", title = "B")
        val blockA = ScribeItem(id = 4, parentId = 2, kind = "block", title = "Text", body = "one two three")
        val blockB = ScribeItem(id = 5, parentId = 3, kind = "block", title = "Text", body = "four five")
        assertEquals(5, TopicWordCount.count(listOf(topic, pageA, pageB, blockA, blockB), 1))
    }

    @Test fun excludesAttachmentsAndDeletedContent() {
        val page = ScribeItem(id = 2, parentId = 1, kind = "page", title = "A")
        val text = ScribeItem(id = 3, parentId = 2, kind = "block", title = "Text", body = "keep these")
        val photo = ScribeItem(id = 4, parentId = 2, kind = "block", title = "Photo", body = "not counted", tags = "type:image")
        val pdf = ScribeItem(id = 5, parentId = 2, kind = "block", title = "PDF", body = "not counted", tags = "type:pdf")
        val deleted = ScribeItem(id = 6, parentId = 2, kind = "block", title = "Deleted", body = "not counted", deletedAt = 1L)
        assertEquals(2, TopicWordCount.count(listOf(page, text, photo, pdf, deleted), 1))
    }

    @Test fun doesNotCountOtherTopicsOrOrphanPages() {
        val page = ScribeItem(id = 2, parentId = 9, kind = "page", title = "Other")
        val block = ScribeItem(id = 3, parentId = 2, kind = "block", title = "Text", body = "not included")
        assertEquals(0, TopicWordCount.count(listOf(page, block), 1))
    }
}
