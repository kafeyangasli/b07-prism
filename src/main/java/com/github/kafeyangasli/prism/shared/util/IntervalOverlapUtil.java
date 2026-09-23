package com.github.kafeyangasli.prism.shared.util;

import java.time.LocalDateTime;

/**
 * Utility class for centralizing interval overlap calculation across Reservation and Blockage domains.
 * <p>
 * Overlap Rule:
 * start1 < end2 AND start2 < end1
 * Adjacent intervals (e.g. 09:00-10:00 and 10:00-11:00) do NOT overlap.
 * Null end times represent open-ended intervals (infinity).
 */
public final class IntervalOverlapUtil {

    private IntervalOverlapUtil() {
        // utility class
    }

    /**
     * Checks if two time intervals overlap.
     *
     * @param start1 start time of interval 1 (required)
     * @param end1   end time of interval 1 (optional, null means open-ended)
     * @param start2 start time of interval 2 (required)
     * @param end2   end time of interval 2 (optional, null means open-ended)
     * @return true if the two intervals overlap, false otherwise
     */
    public static boolean isOverlapping(LocalDateTime start1, LocalDateTime end1,
                                        LocalDateTime start2, LocalDateTime end2) {
        if (start1 == null || start2 == null) {
            return false;
        }

        boolean start1BeforeEnd2 = (end2 == null) || start1.isBefore(end2);
        boolean start2BeforeEnd1 = (end1 == null) || start2.isBefore(end1);

        return start1BeforeEnd2 && start2BeforeEnd1;
    }
}
