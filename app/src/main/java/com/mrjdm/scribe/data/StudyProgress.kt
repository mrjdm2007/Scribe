package com.mrjdm.scribe.data

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Shared, deterministic study-history calculations used by the Android UI and tests. */
object StudyProgress {
    fun sessionMinutes(tags: String): Int =
        tags.split(';', ',').firstOrNull { it.trim().startsWith("minutes:") }
            ?.substringAfter(':')?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun todayMinutes(items: List<ScribeItem>, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val today = calendar(nowMillis, timeZone).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return items.filter { sameDay(it.createdAt, today, timeZone) }.sumOf { sessionMinutes(it.tags) }
    }

    fun sessionsThisWeek(items: List<ScribeItem>, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): List<ScribeItem> {
        val start = calendar(nowMillis, timeZone).apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return items.filter { it.createdAt >= start && it.createdAt <= nowMillis }
    }

    fun streakDays(items: List<ScribeItem>, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val formatter = java.text.SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { this.timeZone = timeZone }
        val studied = items.filter { sessionMinutes(it.tags) > 0 }.map { formatter.format(Date(it.createdAt)) }.toSet()
        val cursor = calendar(nowMillis, timeZone).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (formatter.format(cursor.time) !in studied) cursor.add(Calendar.DAY_OF_YEAR, -1)
        var count = 0
        while (formatter.format(cursor.time) in studied) {
            count++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }

    private fun calendar(time: Long, zone: TimeZone) = Calendar.getInstance(zone).apply { timeInMillis = time }
    private fun sameDay(time: Long, day: Calendar, zone: TimeZone): Boolean {
        val candidate = calendar(time, zone)
        return candidate.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
            candidate.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    }
}
