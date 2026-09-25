package com.github.kafeyangasli.prism.feature.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationAvailabilityView;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationForm;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationSlotState;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationAvailabilityService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationSubmissionService;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:availability;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ReservationAvailabilityIntegrationTest.FixedClockConfiguration.class)
@Transactional
class ReservationAvailabilityIntegrationTest {
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 10, 12);

    @Autowired ReservationAvailabilityService availabilityService;
    @Autowired ReservationSubmissionService submissionService;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeRepository blockageTypes;
    @Autowired UserRepository users;

    private User requester;
    private User staff;
    private Facility room;

    @BeforeEach
    void setUp() {
        requester = users.save(new User("Pemohon", "availability@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        staff = users.save(new User("Petugas", "availability-staff@example.test", "hash", Role.PETUGAS, AccountStatus.ACTIVE));
        room = facilities.save(new Facility("AV-101", "Ruang Uji", "Kelas", "Gedung A", 30, null, AdministrativeStatus.ACTIVE));
    }

    @Test
    void operatingDayContainsHalfHourSlotsAndDistinguishesPastTooSoon() {
        ReservationAvailabilityView view = availabilityService.availability(room.getId(), NOW.toLocalDate(), null, null);

        assertThat(view.slots()).hasSize(26);
        assertThat(view.slots().getFirst().startAt().toLocalTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(view.slots().getLast().endAt().toLocalTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(stateAt(view, "10:00")).isEqualTo(ReservationSlotState.PAST);
        assertThat(stateAt(view, "10:30")).isEqualTo(ReservationSlotState.TOO_SOON);
        assertThat(stateAt(view, "11:00")).isEqualTo(ReservationSlotState.AVAILABLE);
    }

    @Test
    void approvedAndBlockageDisableSlotsPendingDoesNotAndEndsStayContiguous() {
        LocalDate date = NOW.toLocalDate().plusDays(1);
        LocalDateTime at1030 = date.atTime(10, 30);
        saveReservation(at1030, at1030.plusMinutes(30), ReservationStatus.APPROVED);
        saveReservation(date.atTime(11, 0), date.atTime(11, 30), ReservationStatus.PENDING);
        BlockageType type = blockageTypes.save(new BlockageType("MAINTENANCE", "Pemeliharaan", null));
        blockages.save(new FacilityBlockage(room, type, null, date.atTime(11, 30), date.atTime(12, 30),
                BlockageStatus.SCHEDULED, "Pembersihan lantai", "Catatan internal", staff));

        ReservationAvailabilityView view = availabilityService.availability(
                room.getId(), date, date.atTime(9, 0), null);

        assertThat(stateAt(view, "10:30")).isEqualTo(ReservationSlotState.RESERVED);
        assertThat(stateAt(view, "11:00")).isEqualTo(ReservationSlotState.AVAILABLE);
        assertThat(stateAt(view, "11:30")).isEqualTo(ReservationSlotState.BLOCKED);
        assertThat(view.slots().stream().filter(slot -> slot.startAt().toLocalTime().equals(LocalTime.of(11, 30))).findFirst().orElseThrow().reason())
                .isEqualTo("Pembersihan lantai");
        assertThat(view.endTimes()).extracting(end -> end.endAt().toLocalTime())
                .containsExactly(LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30));
    }

    @Test
    void aulaUsesFixedFullDayAndAnyConflictMakesDayUnavailable() {
        Facility aula = facilities.save(new Facility("AULA-1", "Aula Utama", "Aula", "Gedung Pusat", 500, null, AdministrativeStatus.ACTIVE));
        LocalDate date = NOW.toLocalDate().plusDays(2);

        ReservationAvailabilityView available = availabilityService.availability(aula.getId(), date, null, null);
        assertThat(available.aula()).isTrue();
        assertThat(available.aulaAvailable()).isTrue();
        assertThat(available.selectedStart().toLocalTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(available.selectedEnd().toLocalTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(available.getDurationMinutes()).isEqualTo(780);
        assertThat(available.isProposalRequired()).isTrue();

        reservations.save(new Reservation(requester, aula, date.atTime(12, 0), date.atTime(12, 30),
                "Kegiatan", null, ReservationStatus.APPROVED, date.atTime(7, 0)));
        assertThat(availabilityService.availability(aula.getId(), date, null, null).aulaAvailable()).isFalse();
    }

    @Test
    void horizonEndsOnLastDayOfSixthMonth() {
        assertThat(availabilityService.maximumDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        availabilityService.availability(room.getId(), LocalDate.of(2027, 3, 31), null, null);
        assertThatThrownBy(() -> availabilityService.availability(room.getId(), LocalDate.of(2027, 4, 1), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("rentang");
    }

    @Test
    void submissionRevalidatesApprovedAndBlockedButAllowsPendingOverlap() {
        LocalDate date = NOW.toLocalDate().plusDays(1);
        saveReservation(date.atTime(9, 0), date.atTime(10, 0), ReservationStatus.APPROVED);
        assertThatThrownBy(() -> submit(room, date.atTime(9, 30), date.atTime(10, 30), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tidak lagi tersedia");

        saveReservation(date.atTime(13, 0), date.atTime(14, 0), ReservationStatus.PENDING);
        assertThat(submit(room, date.atTime(13, 30), date.atTime(14, 30), null).getStatus())
                .isEqualTo(ReservationStatus.PENDING);

        BlockageType type = blockageTypes.save(new BlockageType("EVENT", "Agenda kampus", null));
        blockages.save(new FacilityBlockage(room, type, null, date.atTime(15, 0), date.atTime(16, 0),
                BlockageStatus.SCHEDULED, "Agenda kampus", null, staff));
        assertThatThrownBy(() -> submit(room, date.atTime(15, 30), date.atTime(16, 30), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tidak lagi tersedia");
    }

    @Test
    void proposalThresholdAndAulaTimesRemainServerAuthoritative() {
        LocalDate date = NOW.toLocalDate().plusDays(3);
        assertThat(submit(room, date.atTime(9, 0), date.atTime(14, 30), null)).isNotNull();
        assertThatThrownBy(() -> submit(room, date.atTime(9, 0), date.atTime(15, 0), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Proposal");

        Facility aula = facilities.save(new Facility("AULA-2", "Aula Timur", "Aula", "Gedung Timur", 250, null, AdministrativeStatus.ACTIVE));
        assertThatThrownBy(() -> submit(aula, date.atTime(8, 0), date.atTime(20, 0), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("07:00 sampai 20:00");
    }

    private ReservationSlotState stateAt(ReservationAvailabilityView view, String time) {
        LocalTime expected = LocalTime.parse(time);
        return view.slots().stream()
                .filter(slot -> slot.startAt().toLocalTime().equals(expected))
                .findFirst().orElseThrow().state();
    }

    private Reservation saveReservation(LocalDateTime start, LocalDateTime end, ReservationStatus status) {
        return reservations.save(new Reservation(requester, room, start, end, "Kegiatan", null, status, start.minusHours(1)));
    }

    private Reservation submit(Facility facility, LocalDateTime start, LocalDateTime end,
                               org.springframework.web.multipart.MultipartFile proposal) {
        ReservationForm form = new ReservationForm();
        form.setFacilityId(facility.getId());
        form.setStartAt(start.toString());
        form.setEndAt(end.toString());
        form.setPurpose("Kegiatan akademik");
        return submissionService.submit(requester.getEmail(), form, proposal);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(WIB).toInstant(), WIB);
        }
    }
}
