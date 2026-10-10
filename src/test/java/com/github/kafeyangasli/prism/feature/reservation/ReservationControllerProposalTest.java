package com.github.kafeyangasli.prism.feature.reservation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;

import com.github.kafeyangasli.prism.feature.administration.service.ProposalTemplateSettingService;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.controller.ReservationController;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationAvailabilityService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationLifecycleService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationQueryService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationSubmissionService;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;

class ReservationControllerProposalTest {
    private ReservationSubmissionService submissions;
    private ReservationLifecycleService lifecycle;
    private ReservationQueryService queries;
    private ProposalStorageService storage;
    private ProposalTemplateSettingService templateSettings;
    private ReservationAvailabilityService availability;
    private FacilityRepository facilities;
    private ReservationController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        submissions = mock(ReservationSubmissionService.class);
        lifecycle = mock(ReservationLifecycleService.class);
        queries = mock(ReservationQueryService.class);
        storage = mock(ProposalStorageService.class);
        templateSettings = mock(ProposalTemplateSettingService.class);
        availability = mock(ReservationAvailabilityService.class);
        facilities = mock(FacilityRepository.class);
        controller = new ReservationController(submissions, lifecycle, queries, availability, facilities, storage,
                templateSettings);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void oldTemplateEndpointRedirectsToConfiguredExternalLink() throws Exception {
        when(templateSettings.getUrl()).thenReturn(Optional.of("https://docs.example.test/template"));

        mvc.perform(get("/reservations/proposal-template"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "https://docs.example.test/template"))
                .andExpect(content().string(""));
        verifyNoInteractions(submissions, lifecycle, queries, storage);
    }

    @Test
    void reservationFormReceivesConfiguredExternalTemplateLink() {
        LocalDate today = LocalDate.of(2026, 10, 10);
        when(facilities.findByAdministrativeStatusOrderByNameAsc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(availability.minimumDate()).thenReturn(today);
        when(availability.maximumDate()).thenReturn(today.plusMonths(6));
        when(templateSettings.getUrl()).thenReturn(Optional.of("https://docs.example.test/template"));
        ConcurrentModel model = new ConcurrentModel();

        controller.newReservation(null, null, model);

        org.assertj.core.api.Assertions.assertThat(model.getAttribute("proposalTemplateUrl"))
                .isEqualTo("https://docs.example.test/template");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void submittedProposalHasNoUserReplacementOrDeletionRoute(String method) throws Exception {
        MockMultipartFile replacement = new MockMultipartFile("proposal", "replacement.pdf",
                "application/pdf", "%PDF-replacement".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/reservations/42/proposal").file(replacement)
                        .principal(new TestingAuthenticationToken("owner@example.test", "unused", "ROLE_PENGGUNA"))
                        .with(request -> { request.setMethod(method); return request; }))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(submissions, lifecycle, queries, storage);
    }

    @Test
    void userDownloadReadsOriginalProposalThroughOwnershipQuery() throws Exception {
        Reservation reservation = mock(Reservation.class);
        byte[] original = "%PDF-original".getBytes(StandardCharsets.UTF_8);
        when(reservation.getProposalPath()).thenReturn("original.pdf");
        when(queries.findOwnReservation("owner@example.test", 42)).thenReturn(reservation);
        when(storage.load("original.pdf")).thenReturn(new ByteArrayResource(original));
        mvc.perform(get("/reservations/42/proposal")
                        .principal(new TestingAuthenticationToken("owner@example.test", "unused", "ROLE_PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().bytes(original));
        verifyNoInteractions(submissions, lifecycle);
    }
}
