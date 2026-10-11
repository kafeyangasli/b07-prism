package com.github.kafeyangasli.prism.feature.reservation.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationReasonCode;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;

@Service
public class ReservationLifecycleService {
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository facilities;
    private final org.springframework.transaction.support.TransactionTemplate lifecycleTransaction;

    public ReservationLifecycleService(
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            Clock clock,
            com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository facilities,
            org.springframework.transaction.PlatformTransactionManager transactions
    ) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.facilities = facilities;
        this.lifecycleTransaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.lifecycleTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lifecycleTransaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /*
     * Jalankan setiap 60 detik.
     */
    @Scheduled(
            fixedDelayString =
                    "${prism.scheduler.reservation-lifecycle-ms:60000}"
    )
    public void processLifecycle() {
        transitionCandidates(null);
    }

    public int expirePendingReservations() {
        return transitionCandidates(ReservationStatus.PENDING);
    }

    public int completeApprovedReservations() {
        return transitionCandidates(ReservationStatus.APPROVED);
    }

    private int transitionCandidates(ReservationStatus onlyStatus) {
        LocalDateTime observed = LocalDateTime.now(clock);
        int changed = 0;
        // Scalar candidates; each transaction locks only one facility, avoiding cross-facility deadlocks.
        for (var candidate : reservationRepository.findLifecycleCandidates(observed)) {
            Boolean transitioned = lifecycleTransaction.execute(tx -> {
                facilities.findByIdForUpdate(candidate.getFacilityId()).orElseThrow();
                userRepository.findByIdForUpdate(candidate.getUserId()).orElseThrow();
                Reservation reservation = reservationRepository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (reservation == null || (onlyStatus != null && reservation.getStatus() != onlyStatus)) return false;
                LocalDateTime now = LocalDateTime.now(clock);
                if (reservation.getStatus() == ReservationStatus.PENDING && reservation.getExpiresAt() != null
                        && !reservation.getExpiresAt().isAfter(now)) {
                    reservation.setStatus(ReservationStatus.EXPIRED);
                    reservation.setReasonCode(ReservationReasonCode.APPROVAL_DEADLINE_EXCEEDED);
                    reservation.setReasonDetail("Reservasi melewati batas waktu persetujuan");
                    reservation.setProcessedAt(now);
                    return true;
                }
                if (reservation.getStatus() == ReservationStatus.APPROVED && reservation.getEndAt().isBefore(now)) {
                    reservation.setStatus(ReservationStatus.COMPLETED);
                    return true;
                }
                return false;
            });
            if (Boolean.TRUE.equals(transitioned)) changed++;
        }
        return changed;
    }

    /*
     * FR-12
     *
     * User hanya boleh membatalkan reservation miliknya sendiri.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Reservation cancelOwnReservation(
            String authenticatedEmail,
            long reservationId
    ) {

        User user =
                userRepository.findByEmailIgnoreCase(
                                authenticatedEmail
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Pengguna tidak ditemukan"
                                )
                        );

        if (user.getRole() != Role.PENGGUNA) {
            throw rule(
                    "Hanya Pengguna yang dapat membatalkan reservasi sendiri"
            );
        }

        /*
         * Query sudah sekaligus memastikan ownership.
         *
         * Pessimistic lock mencegah perubahan status
         * bersamaan ketika cancellation dilakukan.
         */
        var lockContext = reservationRepository.findLockContextById(reservationId)
                .filter(context -> context.getUserId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Reservasi tidak ditemukan"));
        facilities.findByIdForUpdate(lockContext.getFacilityId()).orElseThrow();
        userRepository.findByIdForUpdate(lockContext.getUserId()).orElseThrow();
        Reservation reservation =
                reservationRepository
                        .findByIdAndUserIdForUpdate(
                                reservationId,
                                user.getId()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Reservasi tidak ditemukan"
                                )
                        );

        LocalDateTime now =
                LocalDateTime.now(clock);

        /*
         * PENDING:
         * boleh dibatalkan kapan saja sebelum status berubah.
         */
        if (reservation.getStatus()
                == ReservationStatus.PENDING) {

            cancel(
                    reservation,
                    user,
                    now
            );

            return reservation;
        }

        /*
         * APPROVED:
         *
         * sekarang harus strictly before
         * startAt - 24 jam.
         */
        if (reservation.getStatus()
                == ReservationStatus.APPROVED) {

            LocalDateTime cutoff =
                    reservation.getStartAt()
                            .minusHours(24);

            if (!now.isBefore(cutoff)) {

                throw rule(
                        "Reservasi hanya dapat dibatalkan lebih dari 24 jam sebelum waktu mulai"
                );
            }

            cancel(
                    reservation,
                    user,
                    now
            );

            return reservation;
        }

        /*
         * REJECTED / CANCELLED / EXPIRED / COMPLETED
         */
        throw rule(
                "Reservasi dengan status tersebut tidak dapat dibatalkan"
        );
    }

    private void cancel(
            Reservation reservation,
            User user,
            LocalDateTime now
    ) {

        reservation.setStatus(
                ReservationStatus.CANCELLED
        );

        reservation.setCancelledBy(user);

        reservation.setCancelledAt(now);

        reservation.setReasonCode(
                ReservationReasonCode.CANCELLED_BY_USER
        );

        reservation.setReasonDetail(
                "Dibatalkan oleh pengguna"
        );
    }

    private BusinessRuleException rule(
            String message
    ) {
        return new BusinessRuleException(message);
    }
}
