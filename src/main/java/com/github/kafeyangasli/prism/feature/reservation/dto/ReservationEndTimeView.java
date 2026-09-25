package com.github.kafeyangasli.prism.feature.reservation.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record ReservationEndTimeView(
        LocalDateTime endAt,
        boolean selected,
        long durationMinutes
) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public String getLabel() {
        return endAt.format(TIME);
    }

    public String getValue() {
        return endAt.toString();
    }
}
