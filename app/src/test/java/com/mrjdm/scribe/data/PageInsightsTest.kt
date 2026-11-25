package com.mrjdm.scribe.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PageInsightsTest {
    @Test fun calculatesWordsReadingTimeAndAttachments() {
        val blocks = listOf(
            ScribeItem(id = 1, kind = "block", title = "Text", body = (1..201).joinToString(" ") { "word" }, tags = "type:text"),
            ScribeItem(id = 2, kind = "block", title = "Photo", body = "do not count OCR", uri = "/photo.jpg", tags = "type:image"),
            ScribeItem(id = 3, kind = "block", title = "PDF", body = "do not count extracted text", uri = "/notes.pdf", tags = "type:pdf"),
            ScribeItem(id = 4, kind = "block", title = "Deleted", body = "not counted", deletedAt = 123L)
        )
        assertEquals(PageInsights(201, 2, 2), PageInsights.calculate(blocks))
    }

    @Test fun emptyPageHasNoReadingTime() {
        assertEquals(PageInsights(0, 0, 0), PageInsights.calculate(emptyList()))
    }
}
