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

    public ReservationLifecycleService(
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /*
     * Jalankan setiap 60 detik.
     */
    @Scheduled(
            fixedDelayString =
                    "${prism.scheduler.reservation-lifecycle-ms:60000}"
    )
    @Transactional
    public void processLifecycle() {

        expirePendingReservations();

        completeApprovedReservations();
    }

    /*
     * FR-11
     *
     * PENDING + expires_at <= now
     *             ↓
     *          EXPIRED
     */
    public int expirePendingReservations() {

        LocalDateTime now =
                LocalDateTime.now(clock);

        int changed = 0;

        for (Reservation reservation :
                reservationRepository.findExpiredPending(
                        ReservationStatus.PENDING,
                        now
                )) {

            /*
             * Double check untuk idempotency.
             */
            if (reservation.getStatus()
                    != ReservationStatus.PENDING) {
                continue;
            }

            reservation.setStatus(
                    ReservationStatus.EXPIRED
            );

            reservation.setReasonCode(
                    ReservationReasonCode.APPROVAL_DEADLINE_EXCEEDED
            );

            reservation.setReasonDetail(
                    "Reservasi melewati batas waktu persetujuan"
            );

            reservation.setProcessedAt(now);

            changed++;
        }

        return changed;
    }

    /*
     * FR-18
     *
     * APPROVED + end_at < now
     *             ↓
     *         COMPLETED
     */
    public int completeApprovedReservations() {

        LocalDateTime now =
                LocalDateTime.now(clock);

        int changed = 0;

        for (Reservation reservation :
                reservationRepository.findEndedApproved(
                        ReservationStatus.APPROVED,
                        now
                )) {

            /*
             * Pastikan hanya APPROVED yang diproses.
             */
            if (reservation.getStatus()
                    != ReservationStatus.APPROVED) {
                continue;
            }

            /*
             * SRS:
             * end_at sudah terlewati.
             */
            if (!reservation.getEndAt().isBefore(now)) {
                continue;
            }

            reservation.setStatus(
                    ReservationStatus.COMPLETED
            );

            changed++;
        }

        return changed;
    }

    /*
     * FR-12
     *
     * User hanya boleh membatalkan reservation miliknya sendiri.
     */
    @Transactional
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
