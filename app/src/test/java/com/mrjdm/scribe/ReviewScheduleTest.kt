package com.mrjdm.scribe

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewScheduleTest {
    @Test fun againResetsToOneDay() {
        assertEquals(1L, nextReviewIntervalDays(30, 0))
    }
    @Test fun hardKeepsOrSlightlyIncreasesInterval() {
        assertEquals(12L, nextReviewIntervalDays(10, 1))
    }
    @Test fun goodDoublesInterval() {
        assertEquals(20L, nextReviewIntervalDays(10, 2))
    }
    @Test fun easyTriplesInterval() {
        assertEquals(30L, nextReviewIntervalDays(10, 3))
    }
    @Test fun intervalsAreBounded() {
        assertEquals(365L, nextReviewIntervalDays(200, 3))
        assertEquals(1L, nextReviewIntervalDays(0, 0))
    }
    @Test fun snoozeUpdatesExistingDueTagAndKeepsOtherTags() {
        val original = com.mrjdm.scribe.data.ScribeItem(
            id = 4, kind = "study_reminder", title = "Revise", tags = "subject:ECE;dueAt:1000;priority:high"
        )
        val updated = snoozedReminder(original, 7000)
        assertEquals("subject:ECE;dueAt:7000;priority:high", updated.tags)
        assertEquals(7000L, updated.updatedAt)
    }

    @Test fun snoozeAddsDueTagWhenMissing() {
        val original = com.mrjdm.scribe.data.ScribeItem(
            id = 5, kind = "study_reminder", title = "Revise", tags = "subject:ECE"
        )
        assertEquals("subject:ECE;dueAt:9000", snoozedReminder(original, 9000).tags)
    }

    @Test fun unknownRatingDoesNotChangeInterval() {
        assertEquals(7L, nextReviewIntervalDays(7, 99))
    }
}
