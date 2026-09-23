package com.github.kafeyangasli.prism.feature.reservation.service;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationReasonCode;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
public class ReservationProcessingService {

    private static final long MAX_ACTIVE_APPROVED = 7;
    private static final EnumSet<BlockageStatus> EFFECTIVE_BLOCKAGE_STATUSES =
            EnumSet.of(BlockageStatus.SCHEDULED, BlockageStatus.ACTIVE);

    private final ReservationRepository reservationRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final FacilityBlockageRepository blockageRepository;
    private final Clock clock;

    public ReservationProcessingService(ReservationRepository reservationRepository,
                                        FacilityRepository facilityRepository,
                                        UserRepository userRepository,
                                        FacilityBlockageRepository blockageRepository,
                                        Clock clock) {
        this.reservationRepository = reservationRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.blockageRepository = blockageRepository;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PETUGAS','ADMIN')")
    public Reservation approve(long reservationId, long actorId) {
        LocalDateTime now = LocalDateTime.now(clock);

        // This scalar projection intentionally avoids putting the Reservation entity in the
        // persistence context before the SRS-mandated Facility -> User locks are acquired.
        ReservationRepository.ReservationLockContext context = reservationRepository
                .findLockContextById(reservationId)
                .orElseThrow(() -> notFound(reservationId));

        facilityRepository.findByIdForUpdate(context.getFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas tidak ditemukan"));
        userRepository.findByIdForUpdate(context.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna pemohon tidak ditemukan"));

        // Re-read and lock after both controlling rows are locked. Every decision below uses
        // this post-lock state, never the pre-lock preview.
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> notFound(reservationId));
        User actor = requireStaffActor(actorId);

        validateProcessable(reservation, now);
        if (reservation.getFacility().getAdministrativeStatus() != AdministrativeStatus.ACTIVE) {
            throw rule("FACILITY_INACTIVE", "Fasilitas sudah tidak aktif");
        }
        if (requiresProposal(reservation)
                && (reservation.getProposalValidatedAt() == null || reservation.getProposalValidatedBy() == null)) {
            throw rule(ReservationReasonCode.INVALID_PROPOSAL,
                    "Proposal wajib divalidasi Petugas atau Admin sebelum persetujuan");
        }
        if (reservationRepository.existsOverlapping(reservation.getFacility().getId(),
                reservation.getStartAt(), reservation.getEndAt(),
                EnumSet.of(ReservationStatus.APPROVED), reservation.getId())) {
            throw rule(ReservationReasonCode.SCHEDULE_CONFLICT,
                    "Sudah ada reservasi disetujui yang bertumpang tindih");
        }
        if (blockageRepository.existsOverlapping(reservation.getFacility().getId(),
                reservation.getStartAt(), reservation.getEndAt(),
                EFFECTIVE_BLOCKAGE_STATUSES, null)) {
            throw rule("FACILITY_BLOCKED", "Fasilitas diblokir pada interval yang diminta");
        }
        long activeApproved = reservationRepository.countActiveApproved(
                reservation.getUser().getId(), ReservationStatus.APPROVED, now);
        if (activeApproved >= MAX_ACTIVE_APPROVED) {
            // The reservation deliberately remains PENDING, per FR-16.
            throw rule("ACTIVE_RESERVATION_LIMIT", "Pengguna telah memiliki tujuh reservasi aktif");
        }

        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setProcessedBy(actor);
        reservation.setProcessedAt(now);
        reservation.setReasonCode(null);
        reservation.setReasonDetail(null);

        for (Reservation conflicting : reservationRepository.findOverlapping(
                reservation.getFacility().getId(), reservation.getStartAt(), reservation.getEndAt(),
                EnumSet.of(ReservationStatus.PENDING))) {
            if (!conflicting.getId().equals(reservation.getId())) {
                conflicting.setStatus(ReservationStatus.REJECTED);
                conflicting.setProcessedBy(actor);
                conflicting.setProcessedAt(now);
                conflicting.setReasonCode(ReservationReasonCode.SCHEDULE_CONFLICT);
                conflicting.setReasonDetail("Ditolak otomatis karena reservasi lain pada slot yang sama disetujui");
            }
        }
        return reservation;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PETUGAS','ADMIN')")
    public Reservation reject(long reservationId, long actorId, String reasonDetail) {
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> notFound(reservationId));
        User actor = requireStaffActor(actorId);
        validateProcessable(reservation, now);

        reservation.setStatus(ReservationStatus.REJECTED);
        reservation.setProcessedBy(actor);
        reservation.setProcessedAt(now);
        reservation.setReasonCode(ReservationReasonCode.MANUAL_REJECTION);
        reservation.setReasonDetail(normalizeOptional(reasonDetail));
        return reservation;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PETUGAS','ADMIN')")
    public Reservation cancelApproved(long reservationId, long actorId, String reason) {
        String normalizedReason = normalizeRequired(reason,
                "CANCELLATION_REASON_REQUIRED", "Alasan pembatalan wajib diisi");
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> notFound(reservationId));
        User actor = requireStaffActor(actorId);
        if (reservation.getStatus() != ReservationStatus.APPROVED) {
            throw rule("RESERVATION_NOT_APPROVED", "Hanya reservasi disetujui yang dapat dibatalkan");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledBy(actor);
        reservation.setCancelledAt(now);
        reservation.setReasonCode(ReservationReasonCode.CANCELLED_BY_STAFF);
        reservation.setReasonDetail(normalizedReason);
        return reservation;
    }

    private void validateProcessable(Reservation reservation, LocalDateTime now) {
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw rule("RESERVATION_NOT_PENDING", "Reservasi tidak lagi berstatus menunggu");
        }
        if (reservation.getExpiresAt() != null && !reservation.getExpiresAt().isAfter(now)) {
            throw rule(ReservationReasonCode.APPROVAL_DEADLINE_EXCEEDED,
                    "Batas waktu pemrosesan reservasi telah lewat");
        }
        if (!reservation.getStartAt().isAfter(now)) {
            throw rule(ReservationReasonCode.RESERVATION_START_REACHED,
                    "Waktu mulai reservasi telah tercapai");
        }
    }

    private boolean requiresProposal(Reservation reservation) {
        return Duration.between(reservation.getStartAt(), reservation.getEndAt()).toHours() >= 6
                || "Aula".equalsIgnoreCase(reservation.getFacility().getType());
    }

    private User requireStaffActor(long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Aktor tidak ditemukan"));
        if (actor.getRole() != Role.PETUGAS && actor.getRole() != Role.ADMIN) {
            throw rule("STAFF_ROLE_REQUIRED", "Operasi ini hanya dapat dilakukan Petugas atau Admin");
        }
        return actor;
    }

    private ResourceNotFoundException notFound(long reservationId) {
        return new ResourceNotFoundException("Reservasi " + reservationId + " tidak ditemukan");
    }

    private BusinessRuleException rule(String code, String message) {
        return new BusinessRuleException(code, message);
    }

    private String normalizeRequired(String value, String code, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw rule(code, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
