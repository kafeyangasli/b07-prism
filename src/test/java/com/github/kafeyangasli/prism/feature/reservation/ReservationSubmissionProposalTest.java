package com.github.kafeyangasli.prism.feature.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationForm;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationSubmissionService;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:submission-proposal;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ReservationSubmissionProposalTest.FixedClockConfiguration.class)
@Transactional
class ReservationSubmissionProposalTest {
    private static final String EMAIL = "proposal@example.test";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 10, 0);
    @TempDir static Path storageRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("prism.storage.proposals", () -> storageRoot.toString());
    }

    @Autowired ReservationSubmissionService submissions;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository facilityTypes;
    @Autowired UserRepository users;
    private Facility room;
    private Facility aula;

    @BeforeEach
    void setUp() {
        users.save(new User("Pemohon", EMAIL, "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        room = facility("ROOM", "Kelas");
        aula = facility("AULA", "Aula");
    }

    @ParameterizedTest
    @ValueSource(ints = {360, 390, 780})
    void sixHoursOrLongerWithoutProposalIsRejected(int minutes) {
        assertThatThrownBy(() -> submissions.submit(EMAIL, form(room, minutes), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Proposal");
        assertThat(reservations.count()).isZero();
    }

    @Test
    void aulaWithoutProposalIsRejected() {
        assertThatThrownBy(() -> submissions.submit(EMAIL, form(aula, 780), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Proposal");
        assertThat(reservations.count()).isZero();
    }

    @Test
    void emptyProposalDoesNotSatisfyRequirement() {
        MultipartFile empty = new MockMultipartFile("proposal", "proposal.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> submissions.submit(EMAIL, form(room, 360), empty))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Proposal");
        assertThat(reservations.count()).isZero();
    }

    @Test
    void belowSixHoursWithoutProposalRemainsOptional() {
        Reservation saved = submissions.submit(EMAIL, form(room, 330), null);
        reservations.flush();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(saved.getProposalPath()).isNull();
    }

    @Test
    void qualifyingSubmissionsStoreProposalAndRemainPendingWithoutStaffValidation() throws Exception {
        assertPendingWithProposal(room, 360);
        assertPendingWithProposal(aula, 780);
    }

    @Test
    void optionalProposalCanAlsoBeSubmitted() throws Exception {
        assertPendingWithProposal(room, 330);
    }

    @Test
    void templateTextCannotBeUploadedWithoutConvertingToAcceptedFormat() {
        MultipartFile text = new MockMultipartFile("proposal", "proposal-template.txt", "text/plain", "proposal".getBytes());
        assertThatThrownBy(() -> submissions.submit(EMAIL, form(room, 360), text))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("gagal disimpan");
        assertThat(reservations.count()).isZero();
    }

    private void assertPendingWithProposal(Facility facility, int minutes) throws Exception {
        byte[] content = "%PDF-1.4 proposal fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MultipartFile proposal = new MockMultipartFile("proposal", "signed-proposal.pdf", "application/pdf", content);
        Reservation saved = submissions.submit(EMAIL, form(facility, minutes), proposal);
        reservations.flush();
        Reservation stored = reservations.findById(saved.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(stored.getProposalValidatedAt()).isNull();
        assertThat(stored.getProposalValidatedBy()).isNull();
        assertThat(stored.getProcessedAt()).isNull();
        assertThat(stored.getProposalPath()).endsWith(".pdf");
        assertThat(Files.readAllBytes(storageRoot.resolve(stored.getProposalPath()))).isEqualTo(content);
    }

    private Facility facility(String code, String typeName) {
        FacilityType type = facilityTypes.save(new FacilityType(code, typeName, null));
        return facilities.save(new Facility(code, "Fasilitas " + code, type, "Gedung A", 100, null,
                AdministrativeStatus.ACTIVE));
    }

    private ReservationForm form(Facility facility, int minutes) {
        LocalDateTime start = NOW.toLocalDate().plusDays(1).atTime(7, 0);
        ReservationForm form = new ReservationForm();
        form.setFacilityId(facility.getId());
        form.setStartAt(start.toString());
        form.setEndAt(start.plusMinutes(minutes).toString());
        form.setPurpose("Kegiatan kampus");
        return form;
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock proposalClock() {
            ZoneId zone = ZoneId.of("Asia/Jakarta");
            return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
        }
    }
}
