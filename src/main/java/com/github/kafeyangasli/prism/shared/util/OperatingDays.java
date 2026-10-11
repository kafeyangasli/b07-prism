package com.github.kafeyangasli.prism.shared.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Monday-Friday operating days; public holidays are not excluded. */
public final class OperatingDays {
    private OperatingDays() {}

    public static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek().getValue() <= 5;
    }

    /** Availability intervals are half-open, so midnight at the end excludes the next day. */
    public static boolean includesWeekend(LocalDateTime start, LocalDateTime end) {
        LocalDate date = start.toLocalDate();
        LocalDate last = end.minusNanos(1).toLocalDate();
        while (true) {
            if (!isWeekday(date)) return true;
            if (!date.isBefore(last)) return false;
            date = date.plusDays(1);
        }
    }
}
