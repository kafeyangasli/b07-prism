package com.github.kafeyangasli.prism.shared.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class IntervalOverlapUtilTest {

    private final LocalDateTime t0900 = LocalDateTime.of(2026, 9, 23, 9, 0);
    private final LocalDateTime t1000 = LocalDateTime.of(2026, 9, 23, 10, 0);
    private final LocalDateTime t1030 = LocalDateTime.of(2026, 9, 23, 10, 30);
    private final LocalDateTime t1100 = LocalDateTime.of(2026, 9, 23, 11, 0);
    private final LocalDateTime t1200 = LocalDateTime.of(2026, 9, 23, 12, 0);

    @Test
    void isOverlapping_OverlappingIntervals_ReturnsTrue() {
        // [09:00, 10:30) and [10:00, 11:00)
        assertTrue(IntervalOverlapUtil.isOverlapping(t0900, t1030, t1000, t1100));
        assertTrue(IntervalOverlapUtil.isOverlapping(t1000, t1100, t0900, t1030));
    }

    @Test
    void isOverlapping_AdjacentIntervals_ReturnsFalse() {
        // [09:00, 10:00) and [10:00, 11:00)
        assertFalse(IntervalOverlapUtil.isOverlapping(t0900, t1000, t1000, t1100));
        assertFalse(IntervalOverlapUtil.isOverlapping(t1000, t1100, t0900, t1000));
    }

    @Test
    void isOverlapping_DisjointIntervals_ReturnsFalse() {
        // [09:00, 10:00) and [11:00, 12:00)
        assertFalse(IntervalOverlapUtil.isOverlapping(t0900, t1000, t1100, t1200));
    }

    @Test
    void isOverlapping_OpenEndedInterval_ReturnsTrue() {
        // [09:00, null) and [10:00, 11:00)
        assertTrue(IntervalOverlapUtil.isOverlapping(t0900, null, t1000, t1100));

        // [10:00, 11:00) and [09:00, null)
        assertTrue(IntervalOverlapUtil.isOverlapping(t1000, t1100, t0900, null));
    }

    @Test
    void isOverlapping_BothOpenEnded_ReturnsTrue() {
        // [09:00, null) and [10:00, null)
        assertTrue(IntervalOverlapUtil.isOverlapping(t0900, null, t1000, null));
    }

    @Test
    void isOverlapping_OpenEndedBeforeStart_ReturnsFalse() {
        // Open-ended starting at 11:00 [11:00, null) vs reservation ending at 10:00 [09:00, 10:00)
        assertFalse(IntervalOverlapUtil.isOverlapping(t1100, null, t0900, t1000));
    }
}
