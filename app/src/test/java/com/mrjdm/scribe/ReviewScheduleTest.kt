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


    @Test fun studyGoalCountsOnlySessionsOnTheSelectedLocalDay() {
        val day = java.time.LocalDate.of(2026, 11, 25)
        val zone = java.time.ZoneId.of("UTC")
        val sessions = listOf(
            com.mrjdm.scribe.data.ScribeItem(id = 1, kind = "study_session", title = "ECE",
                tags = "minutes:25", createdAt = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()),
            com.mrjdm.scribe.data.ScribeItem(id = 2, kind = "study_session", title = "Math",
                tags = "minutes:35", createdAt = day.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()),
            com.mrjdm.scribe.data.ScribeItem(id = 3, kind = "study_session", title = "Yesterday",
                tags = "minutes:60", createdAt = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        )
        assertEquals(60, StudyProgress.minutesStudied(sessions, day, zone))
    }

    @Test fun studyStreakAllowsYesterdayAsLatestStudyDay() {
        val today = java.time.LocalDate.of(2026, 11, 25)
        val zone = java.time.ZoneId.of("UTC")
        val sessions = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3)).mapIndexed { index, date ->
            com.mrjdm.scribe.data.ScribeItem(id = index.toLong() + 1, kind = "study_session",
                title = "Study", tags = "minutes:25",
                createdAt = date.atTime(10, 0).atZone(zone).toInstant().toEpochMilli())
        }
        assertEquals(3, StudyProgress.currentStreakDays(sessions, today, zone))
    }

    @Test fun studyStreakExpiresAfterTwoMissedDays() {
        val today = java.time.LocalDate.of(2026, 11, 25)
        val zone = java.time.ZoneId.of("UTC")
        val session = com.mrjdm.scribe.data.ScribeItem(id = 1, kind = "study_session",
            title = "Study", tags = "minutes:25",
            createdAt = today.minusDays(2).atTime(10, 0).atZone(zone).toInstant().toEpochMilli())
        assertEquals(0, StudyProgress.currentStreakDays(listOf(session), today, zone))
    }
