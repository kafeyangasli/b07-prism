package com.github.kafeyangasli.prism.feature.reservation.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record ReservationSlotView(
        LocalDateTime startAt,
        LocalDateTime endAt,
        ReservationSlotState state,
        String reason
) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public String getLabel() {
        return startAt.format(TIME);
    }

    public String getValue() {
        return startAt.toString();
    }

    public boolean isSelectable() {
        return state == ReservationSlotState.AVAILABLE || state == ReservationSlotState.SELECTED;
    }

    public String getStateLabel() {
        return switch (state) {
            case AVAILABLE -> "Tersedia";
            case SELECTED -> "Dipilih";
            case RESERVED -> "Sudah dipesan";
            case BLOCKED -> "Diblokir";
            case PAST -> "Sudah lewat";
            case TOO_SOON -> "Terlalu dekat";
        };
    }
}
