package com.github.kafeyangasli.prism.feature.administration.dto;

import java.util.List;

public record StaffDashboard(List<PendingReservationRow> pendingReservations,
                             List<UnresolvedReportRow> unresolvedReports,
                             String reservationSort) {
}
