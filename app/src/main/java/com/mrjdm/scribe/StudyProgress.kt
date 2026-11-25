package com.mrjdm.scribe

import com.mrjdm.scribe.data.ScribeItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Date-based summaries derived from completed focus sessions, using the device's local timezone. */
object StudyProgress {
    private fun minutes(session: ScribeItem): Int {
        val match = Regex("(?:^|;)minutes:(\\d+)(?:;|$)").find(session.tags) ?: return 0
        return match.groupValues[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    fun minutesStudied(
        sessions: List<ScribeItem>,
        day: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int = sessions.asSequence()
        .filter { it.kind == "study_session" && it.deletedAt == null && minutes(it) > 0 }
        .filter { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == day }
        .sumOf(::minutes)

    fun currentStreakDays(
        sessions: List<ScribeItem>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val studyDays = sessions.asSequence()
            .filter { it.kind == "study_session" && it.deletedAt == null && minutes(it) > 0 }
            .map { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .toSet()
        var cursor = when {
            today in studyDays -> today
            today.minusDays(1) in studyDays -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (cursor in studyDays) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
