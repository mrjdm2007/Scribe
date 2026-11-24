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
    @Test fun unknownRatingDoesNotChangeInterval() {
        assertEquals(7L, nextReviewIntervalDays(7, 99))
    }
}
