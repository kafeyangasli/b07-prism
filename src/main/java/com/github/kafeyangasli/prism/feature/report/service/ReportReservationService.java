package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReportReservationService {
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Reservation eligibleReservation(Long reservationId, Long ownerId) {
        if (reservationId == null || reservationId <= 0) throw new BusinessRuleException("Reservasi tidak valid.");
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservasi tidak ditemukan."));
        if (!withinReportingWindow(reservation)) {
            throw new BusinessRuleException("Laporan dari reservasi hanya dapat dikirim setelah reservasi selesai hingga 24 jam setelah waktu selesai.");
        }
        return reservation;
    }

    /** Used by reservation views; also checks ownership before displaying an action. */
    public boolean canReport(Reservation reservation) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && reservation != null && reservation.getUser() != null
                && authentication.getName().equalsIgnoreCase(reservation.getUser().getEmail())
                && withinReportingWindow(reservation);
    }

    private boolean withinReportingWindow(Reservation reservation) {
        if (reservation.getEndAt() == null || (reservation.getStatus() != ReservationStatus.COMPLETED
                && reservation.getStatus() != ReservationStatus.APPROVED)) return false;
        // APPROVED reservations may have just ended before the lifecycle scheduler marks them COMPLETED.
        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isBefore(reservation.getEndAt()) && !now.isAfter(reservation.getEndAt().plusDays(1));
    }
}
