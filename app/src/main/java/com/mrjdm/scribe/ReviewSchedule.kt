package com.mrjdm.scribe

/**
 * Simple SM-2-inspired review interval policy.
 * Ratings: 0=Again, 1=Hard, 2=Good, 3=Easy.
 */
fun nextReviewIntervalDays(currentDays: Long, rating: Int): Long {
    val current = currentDays.coerceIn(1L, 365L)
    val next = when (rating) {
        0 -> 1L
        1 -> kotlin.math.ceil(current * 1.2).toLong().coerceAtLeast(current)
        2 -> kotlin.math.ceil(current * 2.0).toLong()
        3 -> kotlin.math.ceil(current * 3.0).toLong()
        else -> current
    }
    return next.coerceIn(1L, 365L)
}


private const val FLASHCARD_DAY_MILLIS = 86_400_000L

fun flashcardReviewTags(tags: String, rating: Int, nowMillis: Long): String {
    val oldInterval = tags.split(";").firstOrNull { it.startsWith("interval:") }?.substringAfter(":")?.toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceIn(1.0, 365.0)?.let { kotlin.math.ceil(it).toLong() } ?: 1L
    val days = nextReviewIntervalDays(oldInterval, rating)
    val offset = days * FLASHCARD_DAY_MILLIS
    val dueAt = if (nowMillis > Long.MAX_VALUE - offset) Long.MAX_VALUE else nowMillis + offset
    return tags.split(";").filter { it.isNotBlank() && !it.startsWith("interval:") && !it.startsWith("dueAt:") && !it.startsWith("ease:") && !it.startsWith("lastReviewedAt:") }.plus("interval:$days").plus("dueAt:$dueAt").plus("lastReviewedAt:$nowMillis").joinToString(";")
}
