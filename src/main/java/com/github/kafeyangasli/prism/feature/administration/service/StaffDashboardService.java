package com.github.kafeyangasli.prism.feature.administration.service;

import com.github.kafeyangasli.prism.feature.administration.dto.PendingReservationRow;
import com.github.kafeyangasli.prism.feature.administration.dto.StaffDashboard;
import com.github.kafeyangasli.prism.feature.administration.dto.UnresolvedReportRow;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
public class StaffDashboardService {

    private final ReservationRepository reservationRepository;
    private final ReportRepository reportRepository;
    private final Clock clock;

    public StaffDashboardService(ReservationRepository reservationRepository,
                                 ReportRepository reportRepository,
                                 Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reportRepository = reportRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PETUGAS','ADMIN')")
    public StaffDashboard load(String requestedSort) {
        String sort = "start".equalsIgnoreCase(requestedSort) ? "start" : "created";
        LocalDateTime now = LocalDateTime.now(clock);
        var reservations = reservationRepository
                .findProcessableQueue(ReservationStatus.PENDING, now, sort)
                .stream()
                .map(r -> new PendingReservationRow(
                        r.getId(), r.getUser().getName(), r.getFacility().getName(),
                        r.getStartAt(), r.getEndAt(), r.getCreatedAt(), r.getExpiresAt(),
                        Duration.between(r.getStartAt(), r.getEndAt()).toHours() >= 6
                                || "Aula".equalsIgnoreCase(r.getFacility().getType()),
                        r.getProposalValidatedAt() != null && r.getProposalValidatedBy() != null))
                .toList();
        var reports = reportRepository.findUnresolvedQueue(
                        EnumSet.of(ReportStatus.NEW, ReportStatus.IN_PROGRESS))
                .stream()
                .map(r -> new UnresolvedReportRow(r.getId(), r.getFacility().getName(),
                        r.getCategory(), r.getStatus(), r.getCreatedAt()))
                .toList();
        return new StaffDashboard(reservations, reports, sort);
    }
}
