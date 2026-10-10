package com.github.kafeyangasli.prism.feature.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:proposal-validation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(ReservationProcessingServiceIntegrationTest.FixedClockConfiguration.class)
@Transactional
class ProposalValidationIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 10, 0);
    private static final byte[] PROPOSAL = "Proposal untuk ditinjau".getBytes(StandardCharsets.UTF_8);
    @TempDir static Path directory;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("prism.storage.proposals", () -> directory.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationProcessingService service;
    @Autowired ProposalStorageService proposalStorage;
    private User requester;
    private User staff;
    private User admin;
    private Facility facility;
    private Reservation pending;

    @BeforeEach
    void setUp() throws Exception {
        requester = actor("requester", Role.PENGGUNA);
        staff = actor("staff", Role.PETUGAS);
        admin = actor("admin", Role.ADMIN);
        facility = new Facility("PROP-101", "Ruang Proposal", "Kelas", "Gedung A", 30, null,
                AdministrativeStatus.ACTIVE);
        entityManager.persist(facility);
        String path = proposalStorage.store(new MockMultipartFile("proposal", "proposal.pdf", "application/pdf", PROPOSAL));
        pending = reservations.saveAndFlush(new Reservation(requester, facility, NOW.plusDays(1).withHour(7),
                NOW.plusDays(1).withHour(13), "Kegiatan", path, ReservationStatus.PENDING, NOW.plusHours(12)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PETUGAS", "ADMIN"})
    void staffRolesValidateThroughSameRouteWithoutApproving(String role) throws Exception {
        User actor = role.equals("ADMIN") ? admin : staff;
        mvc.perform(post("/staff/reservations/{id}/approve", pending.getId())
                        .with(user(actor.getEmail()).roles(role)).with(csrf()))
                .andExpect(redirectedUrl("/staff/reservations")).andExpect(flash().attributeExists("error"));
        validate(actor, role).andExpect(flash().attributeExists("success"));

        entityManager.flush();
        entityManager.clear();
        Reservation saved = reservations.findById(pending.getId()).orElseThrow();
        assertThat(saved.getProposalValidatedAt()).isEqualTo(NOW);
        assertThat(saved.getProposalValidatedBy().getId()).isEqualTo(actor.getId());
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(saved.getProcessedAt()).isNull();
        assertThat(saved.getProcessedBy()).isNull();

        mvc.perform(post("/staff/reservations/{id}/approve", pending.getId())
                        .with(user(actor.getEmail()).roles(role)).with(csrf()))
                .andExpect(redirectedUrl("/staff/reservations")).andExpect(flash().attributeExists("success"));
        entityManager.flush();
        entityManager.clear();
        assertThat(reservations.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.APPROVED);
    }

    @Test
    @WithMockUser(roles = "PENGGUNA")
    void penggunaCannotValidateOrDownloadThroughRouteOrService() throws Exception {
        assertThatThrownBy(() -> service.validateProposal(pending.getId(), staff.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.proposalForReview(pending.getId(), staff.getId()))
                .isInstanceOf(AccessDeniedException.class);
        mvc.perform(post(validateUrl()).with(user(requester.getEmail()).roles("PENGGUNA")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get(proposalUrl()).with(user(requester.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isForbidden());
        assertThat(pending.getProposalValidatedAt()).isNull();
    }

    @Test
    void anonymousAndCsrfLessRequestsCannotValidate() throws Exception {
        mvc.perform(post(validateUrl()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get(proposalUrl())).andExpect(status().is3xxRedirection());
        mvc.perform(post(validateUrl()).with(user(staff.getEmail()).roles("PETUGAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolverRejectsAuthenticationWhoseStoredActorIsNotStaff() throws Exception {
        validate(requester, "PETUGAS").andExpect(flash().attributeExists("error"));
        assertThat(pending.getProposalValidatedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "blank", "missing", "traversal"})
    void proposalMustExistAndBeReadableBeforeValidation(String pathCase) throws Exception {
        pending.setProposalPath(switch (pathCase) {
            case "null" -> null;
            case "blank" -> " ";
            case "traversal" -> "../outside.pdf";
            default -> "missing.pdf";
        });
        reservations.saveAndFlush(pending);
        validate(staff, "PETUGAS").andExpect(flash().attributeExists("error"));
        assertThat(pending.getProposalValidatedAt()).isNull();
        assertThat(pending.getProposalValidatedBy()).isNull();
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING);
        mvc.perform(get(proposalUrl()).with(user(staff.getEmail()).roles("PETUGAS")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "CANCELLED", "EXPIRED", "COMPLETED", "deadline", "start"})
    void onlyPendingProcessableReservationsCanBeValidated(String state) throws Exception {
        if (state.equals("deadline")) {
            pending.setExpiresAt(NOW);
        } else if (state.equals("start")) {
            pending.setStartAt(NOW);
        } else {
            pending.setStatus(ReservationStatus.valueOf(state));
        }
        reservations.saveAndFlush(pending);
        validate(staff, "PETUGAS").andExpect(flash().attributeExists("error"));
        assertThat(pending.getProposalValidatedAt()).isNull();
        assertThat(pending.getProposalValidatedBy()).isNull();
    }

    @Test
    void downloadUsesStaffRouteAndReturnsStoredProposal() throws Exception {
        for (User actor : new User[] {staff, admin}) {
            mvc.perform(get(proposalUrl()).with(user(actor.getEmail()).roles(actor.getRole().name())))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                    .andExpect(content().bytes(PROPOSAL));
        }
    }

    @Test
    void repeatedValidationPreservesOriginalAudit() throws Exception {
        validate(staff, "PETUGAS").andExpect(flash().attributeExists("success"));
        validate(admin, "ADMIN").andExpect(flash().attributeExists("success"));
        assertThat(pending.getProposalValidatedBy().getId()).isEqualTo(staff.getId());
        assertThat(pending.getProposalValidatedAt()).isEqualTo(NOW);
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void aulaRequiresValidationEvenForShortReservation() throws Exception {
        facility.setType("Aula");
        pending.setEndAt(pending.getStartAt().plusHours(1));
        entityManager.flush();
        mvc.perform(post("/staff/reservations/{id}/approve", pending.getId())
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        validate(staff, "PETUGAS").andExpect(flash().attributeExists("success"));
        mvc.perform(post("/staff/reservations/{id}/approve", pending.getId())
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void validationDoesNotBypassApprovalConflictRules() throws Exception {
        reservations.saveAndFlush(new Reservation(requester, facility, pending.getStartAt(), pending.getEndAt(),
                "Konflik", null, ReservationStatus.APPROVED, NOW.plusHours(12)));
        validate(staff, "PETUGAS").andExpect(flash().attributeExists("success"));
        mvc.perform(post("/staff/reservations/{id}/approve", pending.getId())
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(flash().attribute("error", "Sudah ada reservasi disetujui yang bertumpang tindih"));
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void staffQueueShowsReviewAndValidationActions() throws Exception {
        String html = mvc.perform(get("/staff/reservations").with(user(staff.getEmail()).roles("PETUGAS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains(proposalUrl(), validateUrl(), "Validasi proposal", "Unduh proposal", "disabled");
        validate(staff, "PETUGAS").andExpect(flash().attributeExists("success"));
        String validatedHtml = mvc.perform(get("/staff/reservations").with(user(staff.getEmail()).roles("PETUGAS")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(validatedHtml).contains("Valid").doesNotContain(validateUrl());
    }

    private User actor(String name, Role role) {
        User actor = new User(name, name + "@proposal.test", "hash", role, AccountStatus.ACTIVE);
        entityManager.persist(actor);
        return actor;
    }

    private org.springframework.test.web.servlet.ResultActions validate(User actor, String role) throws Exception {
        return mvc.perform(post(validateUrl()).with(user(actor.getEmail()).roles(role)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/reservations"));
    }

    private String validateUrl() { return proposalUrl() + "/validate"; }
    private String proposalUrl() { return "/staff/reservations/" + pending.getId() + "/proposal"; }
}
