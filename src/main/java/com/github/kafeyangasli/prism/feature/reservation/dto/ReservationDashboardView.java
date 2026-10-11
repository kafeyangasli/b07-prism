package com.github.kafeyangasli.prism.feature.reservation.dto;

import java.util.List;

import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;

public record ReservationDashboardView(
        String requesterName,
        long pendingCount,
        long activeCount,
        List<Reservation> pendingReservations,
        List<Reservation> activeReservations
) {
}
