package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationReasonCode;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:processing;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ReservationProcessingServiceIntegrationTest.FixedClockConfiguration.class)
@Transactional
@WithMockUser(username = "staff@example.test", roles = "PETUGAS")
class ReservationProcessingServiceIntegrationTest {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 10, 0);

    @Autowired ReservationProcessingService service;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityRepository facilities;
    @Autowired UserRepository users;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeRepository blockageTypes;

    private User requester;
    private User staff;
    private Facility facility;

    @BeforeEach
    void setUp() {
        requester = users.save(new User("Pemohon", "user@example.test", "hash",
                Role.PENGGUNA, AccountStatus.ACTIVE));
        staff = users.save(new User("Petugas", "staff@example.test", "hash",
                Role.PETUGAS, AccountStatus.ACTIVE));
        facility = facilities.save(new Facility("R-101", "Ruang 101", "Kelas", "Gedung A",
                30, null, AdministrativeStatus.ACTIVE));
    }

    @Test
    void approvesValidPendingReservationAndRecordsAudit() {
        Reservation pending = saveReservation(requester, facility, NOW.plusDays(1).withHour(9),
                NOW.plusDays(1).withHour(10), ReservationStatus.PENDING, NOW.plusHours(12));

        Reservation approved = service.approve(pending.getId(), staff.getId());

        assertThat(approved.getStatus()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(approved.getProcessedBy().getId()).isEqualTo(staff.getId());
        assertThat(approved.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsNonPendingExpiredAndReachedStart() {
        Reservation approved = saveReservation(requester, facility, NOW.plusDays(1).withHour(9),
                NOW.plusDays(1).withHour(10), ReservationStatus.APPROVED, NOW.plusHours(12));
        Reservation expired = saveReservation(requester, facility, NOW.plusDays(1).withHour(11),
                NOW.plusDays(1).withHour(12), ReservationStatus.PENDING, NOW.minusMinutes(1));
        Reservation started = saveReservation(requester, facility, NOW.minusHours(1),
                NOW.plusHours(1), ReservationStatus.PENDING, NOW.plusHours(1));

        assertThatThrownBy(() -> service.approve(approved.getId(), staff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("menunggu");
        assertThatThrownBy(() -> service.approve(expired.getId(), staff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("lewat");
        assertThatThrownBy(() -> service.approve(started.getId(), staff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tercapai");
    }

    @Test
    void proposalRequiredReservationNeedsStaffValidation() {
        Reservation pending = saveReservation(requester, facility, NOW.plusDays(1).withHour(7),
                NOW.plusDays(1).withHour(13), ReservationStatus.PENDING, NOW.plusHours(12));
        assertThatThrownBy(() -> service.approve(pending.getId(), staff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Proposal");

        pending.setProposalValidatedAt(NOW.minusMinutes(1));
        pending.setProposalValidatedBy(staff);
        reservations.saveAndFlush(pending);
        assertThat(service.approve(pending.getId(), staff.getId()).getStatus())
                .isEqualTo(ReservationStatus.APPROVED);
    }

    @Test
    void overlapFailsButAdjacentIntervalIsAllowed() {
        LocalDateTime day = NOW.plusDays(1).withHour(9).withMinute(0);
        saveReservation(requester, facility, day, day.plusHours(1),
                ReservationStatus.APPROVED, NOW.plusHours(12));
        Reservation overlapping = saveReservation(requester, facility, day.plusMinutes(30), day.plusHours(2),
                ReservationStatus.PENDING, NOW.plusHours(12));
        Reservation adjacent = saveReservation(requester, facility, day.plusHours(1), day.plusHours(2),
                ReservationStatus.PENDING, NOW.plusHours(12));

        assertThatThrownBy(() -> service.approve(overlapping.getId(), staff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("bertumpang tindih");
        assertThat(service.approve(adjacent.getId(), staff.getId()).getStatus())
                .isEqualTo(ReservationStatus.APPROVED);
    }

    @Test
    void effectiveBlockagePreventsApproval() {
        BlockageType type = blockageTypes.save(new BlockageType("MAINTENANCE", "Pemeliharaan", null));
        LocalDateTime start = NOW.plusDays(1).withHour(9);
        blockages.save(new FacilityBlockage(facility, type, null, start, start.plusHours(1),
                BlockageStatus.SCHEDULED, "Pemeliharaan", null, staff));
        Reservation pending = saveReservation(requester, facility, start, start.plusHours(1),
                ReservationStatus.PENDING, NOW.plusHours(12));

        assertThatThrownBy(() -> service.approve(pending.getId(), staff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("diblokir");
    }

    @Test
    void approvalAutomaticallyRejectsOverlappingPendingOnly() {
        LocalDateTime start = NOW.plusDays(1).withHour(9);
        Reservation chosen = saveReservation(requester, facility, start, start.plusHours(1),
                ReservationStatus.PENDING, NOW.plusHours(12));
        Reservation conflict = saveReservation(requester, facility, start.plusMinutes(30), start.plusHours(2),
                ReservationStatus.PENDING, NOW.plusHours(12));
        Reservation adjacent = saveReservation(requester, facility, start.plusHours(1), start.plusHours(2),
                ReservationStatus.PENDING, NOW.plusHours(12));

        service.approve(chosen.getId(), staff.getId());

        Reservation rejected = reservations.findById(conflict.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(rejected.getReasonCode()).isEqualTo(ReservationReasonCode.SCHEDULE_CONFLICT);
        assertThat(rejected.getProcessedBy().getId()).isEqualTo(staff.getId());
        assertThat(reservations.findById(adjacent.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void manualRejectionRecordsReasonAndAudit() {
        Reservation pending = saveReservation(requester, facility, NOW.plusDays(1).withHour(9),
                NOW.plusDays(1).withHour(10), ReservationStatus.PENDING, NOW.plusHours(12));

        Reservation rejected = service.reject(pending.getId(), staff.getId(), "Dokumen tidak sesuai");

        assertThat(rejected.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(rejected.getReasonCode()).isEqualTo(ReservationReasonCode.MANUAL_REJECTION);
        assertThat(rejected.getReasonDetail()).isEqualTo("Dokumen tidak sesuai");
        assertThat(rejected.getProcessedBy().getId()).isEqualTo(staff.getId());
        assertThat(rejected.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void sevenActiveApprovedLimitLeavesEighthPendingAndIgnoresOtherStatesOrPastApproval() {
        for (int index = 0; index < 7; index++) {
            saveReservation(requester, facility, NOW.plusDays(index + 1).withHour(9),
                    NOW.plusDays(index + 1).withHour(10), ReservationStatus.APPROVED, NOW.plusHours(1));
        }
        saveReservation(requester, facility, NOW.minusDays(2), NOW.minusDays(1),
                ReservationStatus.APPROVED, NOW.minusDays(3));
        saveReservation(requester, facility, NOW.plusDays(9).withHour(9), NOW.plusDays(9).withHour(10),
                ReservationStatus.CANCELLED, NOW.plusHours(1));
        saveReservation(requester, facility, NOW.plusDays(11).withHour(9), NOW.plusDays(11).withHour(10),
                ReservationStatus.REJECTED, NOW.plusHours(1));
        saveReservation(requester, facility, NOW.plusDays(12).withHour(9), NOW.plusDays(12).withHour(10),
                ReservationStatus.EXPIRED, NOW.plusHours(1));
        saveReservation(requester, facility, NOW.minusDays(4), NOW.minusDays(3),
                ReservationStatus.COMPLETED, NOW.minusDays(5));
        Reservation eighth = saveReservation(requester, facility, NOW.plusDays(10).withHour(9),
                NOW.plusDays(10).withHour(10), ReservationStatus.PENDING, NOW.plusHours(12));

        assertThatThrownBy(() -> service.approve(eighth.getId(), staff.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tujuh");
        assertThat(reservations.findById(eighth.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void zeroThroughSixActiveApprovalsAllowAnother() {
        for (int index = 0; index < 6; index++) {
            saveReservation(requester, facility, NOW.plusDays(index + 1).withHour(9),
                    NOW.plusDays(index + 1).withHour(10), ReservationStatus.APPROVED, NOW.plusHours(1));
        }
        Reservation seventh = saveReservation(requester, facility, NOW.plusDays(8).withHour(9),
                NOW.plusDays(8).withHour(10), ReservationStatus.PENDING, NOW.plusHours(12));
        assertThat(service.approve(seventh.getId(), staff.getId()).getStatus())
                .isEqualTo(ReservationStatus.APPROVED);
    }

    @Test
    void staffAndAdminCanCancelApprovedWithMandatoryReason() {
        Reservation first = saveReservation(requester, facility, NOW.plusDays(1).withHour(9),
                NOW.plusDays(1).withHour(10), ReservationStatus.APPROVED, NOW.plusHours(12));
        assertThatThrownBy(() -> service.cancelApproved(first.getId(), staff.getId(), " "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("wajib");

        Reservation cancelled = service.cancelApproved(first.getId(), staff.getId(), "Keperluan darurat");
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.getCancelledBy().getId()).isEqualTo(staff.getId());
        assertThat(cancelled.getCancelledAt()).isEqualTo(NOW);
        assertThat(cancelled.getReasonCode()).isEqualTo(ReservationReasonCode.CANCELLED_BY_STAFF);

        User admin = users.save(new User("Admin", "admin@example.test", "hash",
                Role.ADMIN, AccountStatus.ACTIVE));
        Reservation second = saveReservation(requester, facility, NOW.plusDays(2).withHour(9),
                NOW.plusDays(2).withHour(10), ReservationStatus.APPROVED, NOW.plusHours(12));
        assertThat(service.cancelApproved(second.getId(), admin.getId(), "Perintah admin").getCancelledBy().getId())
                .isEqualTo(admin.getId());
    }

    private Reservation saveReservation(User user, Facility selectedFacility,
                                        LocalDateTime start, LocalDateTime end,
                                        ReservationStatus status, LocalDateTime expiresAt) {
        return reservations.save(new Reservation(user, selectedFacility, start, end,
                "Kegiatan", null, status, expiresAt));
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
