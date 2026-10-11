package com.github.kafeyangasli.prism.feature.reservation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationDashboardView;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;

@Service
public class ReservationQueryService {
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ReservationQueryService(
            ReservationRepository reservationRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReservationDashboardView dashboard(String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        LocalDateTime now = LocalDateTime.now(clock);
        List<Reservation> reservations = reservationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId());

        List<Reservation> pending = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.PENDING)
                .sorted(Comparator.comparing(Reservation::getStartAt))
                .toList();
        List<Reservation> active = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.APPROVED)
                .filter(reservation -> reservation.getEndAt().isAfter(now))
                .sorted(Comparator.comparing(Reservation::getStartAt))
                .toList();

        return new ReservationDashboardView(
                user.getName(),
                pending.size(),
                active.size(),
                pending.stream().limit(5).toList(),
                active.stream().limit(5).toList()
        );
    }

    /*
     * FR-13
     *
     * Menampilkan seluruh reservation milik user:
     *
     * PENDING
     * APPROVED
     * REJECTED
     * CANCELLED
     * EXPIRED
     * COMPLETED
     */
    @Transactional(readOnly = true)
    public List<Reservation> findOwnReservations(
            String authenticatedEmail
    ) {

        User user =
                findUser(authenticatedEmail);

        return reservationRepository
                .findByUserIdOrderByCreatedAtDesc(
                        user.getId()
                );
    }

    /*
     * Detail reservation.
     *
     * Query menggunakan:
     *
     * reservation ID
     * +
     * user ID
     *
     * sehingga user tidak bisa melihat reservation
     * milik user lain.
     */
    @Transactional(readOnly = true)
    public Reservation findOwnReservation(
            String authenticatedEmail,
            long reservationId
    ) {

        User user =
                findUser(authenticatedEmail);

        return reservationRepository
                .findByIdAndUserId(
                        reservationId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Reservasi tidak ditemukan"
                        )
                );
    }

    private User findUser(String email) {

        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Pengguna tidak ditemukan"
                                )
                        );

        if (user.getRole() != Role.PENGGUNA) {
            throw new BusinessRuleException(
                    "Halaman ini hanya untuk Pengguna"
            );
        }

        return user;
    }
}
