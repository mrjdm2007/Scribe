package com.mrjdm.scribe.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StudyProgressTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun session(day: Int, minutes: Int) = ScribeItem(
        kind = "study_session",
        title = "Study",
        tags = "minutes:$minutes",
        createdAt = at(2026, 11, day)
    )

    @Test fun sumsOnlyTodaySessions() {
        val rows = listOf(session(26, 25), session(26, 45), session(25, 60))
        assertEquals(70, StudyProgress.todayMinutes(rows, at(2026, 11, 26), utc))
    }

    @Test fun weeklySessionsStartOnConfiguredWeekBoundary() {
        val rows = listOf(session(23, 20), session(24, 25), session(26, 45))
        val result = StudyProgress.sessionsThisWeek(rows, at(2026, 11, 26), utc)
        assertEquals(3, result.size)
    }

    @Test fun streakIncludesTodayOrStartsFromYesterday() {
        val rows = listOf(session(24, 20), session(25, 25), session(26, 45))
        assertEquals(3, StudyProgress.streakDays(rows, at(2026, 11, 26), utc))
        assertEquals(2, StudyProgress.streakDays(rows, at(2026, 11, 27), utc))
    }

    @Test fun missingMinutesMetadataDoesNotCount() {
        val row = ScribeItem(kind = "study_session", title = "Legacy", createdAt = at(2026, 11, 26))
        assertEquals(0, StudyProgress.todayMinutes(listOf(row), at(2026, 11, 26), utc))
        assertEquals(0, StudyProgress.streakDays(listOf(row), at(2026, 11, 26), utc))
    }
}
