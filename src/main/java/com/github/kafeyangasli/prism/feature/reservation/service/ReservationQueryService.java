package com.github.kafeyangasli.prism.feature.reservation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
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

    public ReservationQueryService(
            ReservationRepository reservationRepository,
            UserRepository userRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
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
