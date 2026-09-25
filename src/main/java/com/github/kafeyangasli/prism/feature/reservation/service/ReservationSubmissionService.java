package com.github.kafeyangasli.prism.feature.reservation.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationForm;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;

@Service
public class ReservationSubmissionService {

    private static final LocalTime OPENING_TIME = LocalTime.of(7, 0);
    private static final LocalTime CLOSING_TIME = LocalTime.of(20, 0);

    private static final int MINUTES_PER_SLOT = 30;
    private static final long MAX_DURATION_HOURS = 13;

    private static final int EXPIRY_WORKDAYS = 7;

    private final ReservationRepository reservationRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final ProposalStorageService proposalStorageService;
    private final ReservationAvailabilityService availabilityService;
    private final Clock clock;

    public ReservationSubmissionService(
            ReservationRepository reservationRepository,
            FacilityRepository facilityRepository,
            UserRepository userRepository,
            ProposalStorageService proposalStorageService,
            ReservationAvailabilityService availabilityService,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
        this.proposalStorageService = proposalStorageService;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    @Transactional
    public Reservation submit(
            String authenticatedEmail,
            ReservationForm form,
            MultipartFile proposal
    ) {

        /*
         * Jangan pernah mengambil userId dari form.
         * User ditentukan berdasarkan akun yang sedang login.
         */
        User user = userRepository.findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Pengguna tidak ditemukan")
                );

        if (user.getRole() != Role.PENGGUNA) {
            throw rule("Hanya Pengguna yang dapat mengajukan reservasi");
        }

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw rule("Akun pengguna belum aktif");
        }

        if (form == null || form.getFacilityId() == null) {
            throw rule("Fasilitas wajib dipilih");
        }

        /*
         * Facility harus dicari ulang dari database.
         * Jangan percaya status facility yang berasal dari UI.
         */
        Facility facility = facilityRepository.findById(form.getFacilityId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Fasilitas tidak ditemukan")
                );

        if (facility.getAdministrativeStatus() != AdministrativeStatus.ACTIVE) {
            throw rule(
                    "Fasilitas tidak aktif dan tidak dapat dipesan"
            );
        }

        LocalDateTime now = LocalDateTime
                .now(clock)
                .truncatedTo(ChronoUnit.SECONDS);

        LocalDateTime startAt =
                parseDateTime(
                        form.getStartAt(),
                        "Waktu mulai tidak valid"
                );

        LocalDateTime endAt =
                parseDateTime(
                        form.getEndAt(),
                        "Waktu selesai tidak valid"
                );

        /*
         * FR-09
         */
        validateTimeRules(
                startAt,
                endAt,
                facility,
                now
        );

        /*
         * FR-08 / BR-03
         */
        validateHorizon(
                startAt.toLocalDate(),
                now.toLocalDate()
        );

        String purpose = normalizePurpose(form.getPurpose());

        /*
         * Availability shown in the browser is advisory. Re-check immediately
         * before accepting the request so stale forms cannot reserve an
         * approved or blocked interval. Pending requests intentionally do not
         * participate in this check.
         */
        availabilityService.ensureIntervalAvailable(
                facility.getId(),
                startAt,
                endAt
        );

        /*
         * FR-10
         */
        boolean proposalRequired =
                requiresProposal(
                        facility,
                        startAt,
                        endAt
                );

        String proposalPath = null;

        if (proposalRequired) {

            if (proposal == null || proposal.isEmpty()) {
                throw rule(
                        "Proposal bertanda tangan wajib diunggah untuk reservasi ini"
                );
            }

            try {
                proposalPath =
                        proposalStorageService.store(proposal);

            } catch (IOException exception) {
                throw new BusinessRuleException(
                        "Proposal gagal disimpan",
                        exception
                );
            }

        } else if (proposal != null && !proposal.isEmpty()) {

            /*
             * Proposal boleh di-upload walaupun tidak diwajibkan.
             */
            try {
                proposalPath =
                        proposalStorageService.store(proposal);

            } catch (IOException exception) {
                throw new BusinessRuleException(
                        "Proposal gagal disimpan",
                        exception
                );
            }
        }

        /*
         * FR-11
         */
        LocalDateTime expiresAt =
                calculateExpiresAt(
                        now,
                        startAt
                );

        /*
         * Reservation baru selalu PENDING.
         */
        Reservation reservation = new Reservation(
                user,
                facility,
                startAt,
                endAt,
                purpose,
                proposalPath,
                ReservationStatus.PENDING,
                expiresAt
        );

        return reservationRepository.save(reservation);
    }

    private void validateTimeRules(
            LocalDateTime startAt,
            LocalDateTime endAt,
            Facility facility,
            LocalDateTime now
    ) {

        /*
         * start < end
         */
        if (!startAt.isBefore(endAt)) {
            throw rule(
                    "Waktu mulai harus lebih awal dari waktu selesai"
            );
        }

        /*
         * Harus berada di tanggal yang sama.
         */
        if (!startAt.toLocalDate()
                .equals(endAt.toLocalDate())) {

            throw rule(
                    "Reservasi harus berada pada satu tanggal kalender"
            );
        }

        /*
         * Minimal 30 menit dari waktu submit.
         */
        if (startAt.isBefore(now.plusMinutes(30))) {
            throw rule(
                    "Pengajuan harus dilakukan minimal 30 menit sebelum waktu mulai"
            );
        }

        /*
         * Jam operasional:
         * 07:00 - 20:00
         */
        if (startAt.toLocalTime().isBefore(OPENING_TIME)
                || endAt.toLocalTime().isAfter(CLOSING_TIME)) {

            throw rule(
                    "Jam operasional fasilitas adalah 07:00 sampai 20:00 WIB"
            );
        }

        /*
         * Harus berada pada slot 30 menit.
         */
        if (!isHalfHour(startAt)
                || !isHalfHour(endAt)) {

            throw rule(
                    "Waktu mulai dan selesai harus berada pada kelipatan 30 menit"
            );
        }

        Duration duration =
                Duration.between(startAt, endAt);

        /*
         * Minimal 30 menit.
         */
        if (duration.toMinutes() < MINUTES_PER_SLOT) {
            throw rule(
                    "Durasi minimum reservasi adalah 30 menit"
            );
        }

        /*
         * Maksimal 13 jam.
         */
        if (duration.toHours() > MAX_DURATION_HOURS) {
            throw rule(
                    "Durasi maksimum reservasi adalah 13 jam"
            );
        }

        /*
         * Aula hanya boleh 07:00 - 20:00 penuh.
         */
        if (isAula(facility)
                && !(
                startAt.toLocalTime().equals(OPENING_TIME)
                        && endAt.toLocalTime().equals(CLOSING_TIME)
        )) {

            throw rule(
                    "Aula hanya dapat dipesan tepat dari 07:00 sampai 20:00"
            );
        }
    }

    private void validateHorizon(
            LocalDate requestedDate,
            LocalDate today
    ) {

        /*
         * Contoh:
         *
         * sekarang September
         * +6 bulan = Maret
         * maka tanggal maksimum adalah
         * 31 Maret.
         */
        YearMonth sixthMonth =
                YearMonth.from(today).plusMonths(6);

        LocalDate lastAllowedDate =
                sixthMonth.atEndOfMonth();

        if (requestedDate.isAfter(lastAllowedDate)) {

            throw rule(
                    "Reservasi hanya dapat diajukan sampai hari terakhir bulan keenam setelah bulan berjalan"
            );
        }
    }

    private LocalDateTime calculateExpiresAt(
            LocalDateTime submittedAt,
            LocalDateTime startAt
    ) {

        LocalDate deadlineDate =
                submittedAt.toLocalDate();

        /*
         * Hari submit dihitung jika Senin-Jumat.
         */
        int counted =
                isWorkday(deadlineDate) ? 1 : 0;

        while (counted < EXPIRY_WORKDAYS) {

            deadlineDate =
                    deadlineDate.plusDays(1);

            if (isWorkday(deadlineDate)) {
                counted++;
            }
        }

        /*
         * Deadline:
         * 07:00 pada workday ke-7
         */
        LocalDateTime workdayDeadline =
                deadlineDate.atTime(OPENING_TIME);

        /*
         * expires_at =
         * yang lebih awal antara:
         *
         * 1. deadline 7 hari kerja
         * 2. start_at
         */
        return workdayDeadline.isBefore(startAt)
                ? workdayDeadline
                : startAt;
    }

    private boolean isWorkday(LocalDate date) {
        return date.getDayOfWeek().getValue() <= 5;
    }

    private boolean isHalfHour(LocalDateTime value) {

        return value.getMinute() % MINUTES_PER_SLOT == 0
                && value.getSecond() == 0
                && value.getNano() == 0;
    }

    private boolean requiresProposal(
            Facility facility,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {

        Duration duration =
                Duration.between(startAt, endAt);

        /*
         * Aula → wajib proposal.
         * Durasi >= 6 jam → wajib proposal.
         */
        return isAula(facility)
                || duration.toMinutes() >= 360;
    }

    private boolean isAula(Facility facility) {

        return facility.getType() != null
                && facility.getType().equalsIgnoreCase("Aula");
    }

    private LocalDateTime parseDateTime(
            String value,
            String message
    ) {

        if (value == null || value.isBlank()) {
            throw rule(message);
        }

        try {
            return LocalDateTime.parse(value.trim());

        } catch (RuntimeException exception) {
            throw rule(message);
        }
    }

    private String normalizePurpose(String purpose) {

        if (purpose == null || purpose.isBlank()) {
            throw rule("Tujuan penggunaan wajib diisi");
        }

        String normalized =
                purpose.trim();

        if (normalized.length() > 1000) {
            throw rule(
                    "Tujuan penggunaan maksimal 1000 karakter"
            );
        }

        return normalized;
    }

    private BusinessRuleException rule(String message) {
        return new BusinessRuleException(message);
    }
}
