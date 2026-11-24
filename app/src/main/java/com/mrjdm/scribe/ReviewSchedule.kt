package com.mrjdm.scribe

/**
 * Simple SM-2-inspired review interval policy.
 * Ratings: 0=Again, 1=Hard, 2=Good, 3=Easy.
 */
fun nextReviewIntervalDays(currentDays: Long, rating: Int): Long {
    val current = currentDays.coerceAtLeast(1L)
    val next = when (rating) {
        0 -> 1L
        1 -> kotlin.math.ceil(current * 1.2).toLong().coerceAtLeast(current)
        2 -> kotlin.math.ceil(current * 2.0).toLong()
        3 -> kotlin.math.ceil(current * 3.0).toLong()
        else -> current
    }
    return next.coerceIn(1L, 365L)
}
