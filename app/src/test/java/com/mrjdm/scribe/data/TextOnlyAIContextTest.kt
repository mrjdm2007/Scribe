package com.mrjdm.scribe.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOnlyAIContextTest {
    @Test fun includesNotebookTextButNeverAttachmentContentOrPaths() {
        val items = listOf(
            ScribeItem(id = 1, kind = "course", title = "Electronics", body = "Semester notes"),
            ScribeItem(id = 2, parentId = 1, kind = "block", title = "Ohm law", body = "V = IR", tags = "type:text"),
            ScribeItem(id = 3, parentId = 1, kind = "block", title = "Private PDF title", body = "secret pdf text", uri = "/private/notes.pdf", tags = "type:pdf"),
            ScribeItem(id = 4, parentId = 1, kind = "block", title = "Photo", body = "image OCR text", uri = "/private/photo.jpg", tags = "type:image"),
            ScribeItem(id = 5, parentId = 1, kind = "block", title = "Detached PDF", body = "must not appear", tags = "type:pdf"),
            ScribeItem(id = 6, parentId = 1, kind = "block", title = "Deleted", body = "deleted text", deletedAt = 10L)
        )
        val context = TextOnlyAIContext.build(items)
        assertTrue(context.contains("Electronics"))
        assertTrue(context.contains("V = IR"))
        assertFalse(context.contains("Private PDF"))
        assertFalse(context.contains("secret pdf text"))
        assertFalse(context.contains("photo.jpg"))
        assertFalse(context.contains("image OCR text"))
        assertFalse(context.contains("Detached PDF"))
        assertFalse(context.contains("deleted text"))
    }

    @Test fun malformedParentCycleDoesNotCrashContextBuild() {
        val first = ScribeItem(id = 10, parentId = 11, kind = "topic", title = "Cycle A", body = "A")
        val second = ScribeItem(id = 11, parentId = 10, kind = "page", title = "Cycle B", body = "B")
        val result = TextOnlyAIContext.build(listOf(first, second))
        assertTrue(result.contains("Cycle A"))
        assertTrue(result.contains("Cycle B"))
    }

    @Test fun respectsContextCharacterLimit() {
        val items = listOf(ScribeItem(id = 1, kind = "page", title = "Title", body = "A long note body"))
        assertTrue(TextOnlyAIContext.build(items, maxCharacters = 5).isEmpty())
    }
}