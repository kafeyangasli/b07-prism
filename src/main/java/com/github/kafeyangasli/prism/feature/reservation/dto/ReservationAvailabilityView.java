package com.github.kafeyangasli.prism.feature.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;

public record ReservationAvailabilityView(
        Facility facility,
        LocalDate date,
        boolean aula,
        boolean aulaAvailable,
        List<ReservationSlotView> slots,
        List<ReservationEndTimeView> endTimes,
        LocalDateTime selectedStart,
        LocalDateTime selectedEnd
) {
    public boolean hasSelectedStart() {
        return selectedStart != null;
    }

    public boolean hasSelectedEnd() {
        return selectedEnd != null;
    }

    public long getDurationMinutes() {
        return selectedStart == null || selectedEnd == null
                ? 0
                : java.time.Duration.between(selectedStart, selectedEnd).toMinutes();
    }

    public boolean isProposalRequired() {
        return aula || getDurationMinutes() >= 360;
    }
}
