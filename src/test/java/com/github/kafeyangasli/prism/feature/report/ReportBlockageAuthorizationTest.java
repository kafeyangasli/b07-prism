package com.github.kafeyangasli.prism.feature.report;

import com.github.kafeyangasli.prism.feature.administration.service.StaffActorResolver;
import com.github.kafeyangasli.prism.feature.blockage.controller.BlockageController;
import com.github.kafeyangasli.prism.feature.blockage.controller.BlockageTypeController;
import com.github.kafeyangasli.prism.feature.blockage.model.*;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.blockage.service.*;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.controller.*;
import com.github.kafeyangasli.prism.feature.report.model.*;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.report.service.ReportService;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.security.SecurityConfig;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import com.github.kafeyangasli.prism.feature.report.service.ReportPhotoStorage;
import com.github.kafeyangasli.prism.feature.report.service.ReportReservationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(ReportBlockageAuthorizationTest.Config.class)
class ReportBlockageAuthorizationTest {
    @TempDir static Path photoDirectory;
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ReportController.class, StaffReportController.class, ReportPageController.class, ReportExceptionHandler.class,
            BlockageController.class, BlockageTypeController.class,
            ReportService.class, ReportReservationService.class, BlockageService.class, StaffActorResolver.class})
    static class Config {
        @Bean ReportPhotoStorage photos() { return new ReportPhotoStorage(photoDirectory.toString()); }
        @Bean org.thymeleaf.spring6.SpringTemplateEngine templateEngine() {
            var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setSuffix(".html");
            resolver.setCharacterEncoding("UTF-8");
            var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            engine.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());
            return engine;
        }
        @Bean org.thymeleaf.spring6.view.ThymeleafViewResolver viewResolver(org.thymeleaf.spring6.SpringTemplateEngine engine) {
            var resolver = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
            resolver.setTemplateEngine(engine);
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ReportRepository reports() { return mock(ReportRepository.class); }
        @Bean FacilityRepository facilities() { return mock(FacilityRepository.class); }
        @Bean ReservationRepository reservations() { return mock(ReservationRepository.class); }
        @Bean java.time.Clock clock() { return java.time.Clock.system(java.time.ZoneId.of("Asia/Jakarta")); }
        @Bean FacilityBlockageRepository blockages() { return mock(FacilityBlockageRepository.class); }
        @Bean BlockageTypeService types() { return mock(BlockageTypeService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ReportRepository reports;
    @Autowired FacilityRepository facilities;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeService types;
    @Autowired ReportService reportService;
    @Autowired ReportPhotoStorage photoStorage;
    @Autowired BlockageService blockageService;
    MockMvc mvc;
    User owner;
    Facility facility;
    Report report;
    FacilityBlockage blockage;

    @BeforeEach
    void setUp() {
        reset(users, reports, facilities, reservations, blockages, types);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = account(42L, "owner@example.com", Role.PENGGUNA);
        facility = new Facility("F01", "Room", "Room", "Floor", 10, "Room", AdministrativeStatus.ACTIVE);
        ReflectionTestUtils.setField(facility, "id", 10L);
        report = new Report(owner, facility, "AC", "Broken", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);
        BlockageType type = new BlockageType("PLANNED_MAINTENANCE", "Maintenance", "Maintenance");
        blockage = new FacilityBlockage(facility, type, null, LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2030, 1, 2, 0, 0), BlockageStatus.ACTIVE, "Maintenance", null, owner);
        when(facilities.findById(10L)).thenReturn(Optional.of(facility));
        when(facilities.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(types.validateAndGetActiveBlockageType(11L)).thenReturn(type);
        when(reports.findById(100L)).thenReturn(Optional.of(report));
        when(reports.findByIdForUpdate(100L)).thenReturn(Optional.of(report));
        when(reports.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(blockages.findById(200L)).thenReturn(Optional.of(blockage));
        when(blockages.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createAndReadReportsIgnoreClientOwnerIds() throws Exception {
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json", """
                {"facilityId":10,"category":"AC","description":"Broken","userId":999,"ownerId":999}
                """.getBytes());
        mvc.perform(multipart("/api/reports").file(data).file(photo()).with(user("owner@example.com").roles("PENGGUNA")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(42));
        when(reports.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(report));
        when(reports.findByIdAndUserId(100L, 42L)).thenReturn(Optional.of(report));
        mvc.perform(get("/api/reports").param("userId", "999").with(user("owner@example.com").roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].user.id").value(42));
        mvc.perform(get("/api/reports/100").param("userId", "999").with(user("owner@example.com").roles("PENGGUNA")))
                .andExpect(status().isOk());
        verify(reports).findByUserIdOrderByCreatedAtDesc(42L);
        verify(reports).findByIdAndUserId(100L, 42L);
        verify(users, never()).findById(999L);
    }

    @Test
    @WithMockUser(username = "other@example.com", roles = "PENGGUNA")
    void anotherOwnerCannotReadReport() {
        when(users.findByEmailIgnoreCase("other@example.com"))
                .thenReturn(Optional.of(account(84L, "other@example.com", Role.PENGGUNA)));
        assertThrows(ResourceNotFoundException.class, () -> reportService.getReportDetail(100L));
        verify(reports).findByIdAndUserId(100L, 84L);
        verify(reports, never()).findById(100L);
    }

    @Test
    @WithMockUser(roles = "PENGGUNA")
    void userCannotProcessReportsOrOperateBlockages() throws Exception {
        mvc.perform(patch("/api/staff/reports/100/status").param("status", "IN_PROGRESS").with(csrf()))
                .andExpect(status().isForbidden());
        for (String path : List.of("/api/staff/blockages", "/api/staff/blockages/200")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/staff/blockages/preview").contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/staff/blockages").contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/staff/blockages/200").contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/staff/blockages/200/complete").contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    @WithMockUser(roles = "PENGGUNA")
    void serviceAuthorizationAlsoRejectsOrdinaryUsers() {
        assertThrows(AccessDeniedException.class, () -> reportService.updateStatus(100L, ReportStatus.IN_PROGRESS, null));
        assertThrows(AccessDeniedException.class, () -> blockageService.getAllBlockages());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PETUGAS", "ADMIN"})
    void staffRolesCanProcessAndOperateWithRealAuditActor(String role) throws Exception {
        User staff = account(73L, "staff@example.com", Role.valueOf(role));
        when(users.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staff));
        when(users.findById(73L)).thenReturn(Optional.of(staff));
        mvc.perform(patch("/api/staff/reports/100/status").param("status", "IN_PROGRESS")
                        .param("staffId", "999").with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.handledBy.id").value(73));
        mvc.perform(get("/api/staff/blockages").with(user("staff@example.com").roles(role))).andExpect(status().isOk());
        mvc.perform(get("/api/staff/blockages/200").with(user("staff@example.com").roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/staff/blockages/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"facilityId\":10,\"startAt\":\"2030-01-01T00:00:00\"}")
                        .with(user("staff@example.com").roles(role)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/staff/blockages").contentType(MediaType.APPLICATION_JSON).content("""
                        {"facilityId":10,"blockageTypeId":11,"startAt":"2030-01-01T00:00:00",
                         "publicReason":"Maintenance","createdBy":999}
                        """).with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdBy.id").value(73));
        mvc.perform(put("/api/staff/blockages/200").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicReason\":\"Updated\"}").with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/staff/blockages/200/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"earlyCompletionReason\":\"Done\",\"endedBy\":999}")
                        .with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endedBy.id").value(73));
        verify(users, never()).findById(999L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENGGUNA", "PETUGAS", "ADMIN"})
    void blockageTypeManagementRemainsAdminOnly(String role) throws Exception {
        int expected = role.equals("ADMIN") ? 200 : 403;
        mvc.perform(get("/api/admin/blockage-types").with(user("actor@example.com").roles(role)))
                .andExpect(status().is(expected));
        mvc.perform(post("/api/admin/blockage-types").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(user("actor@example.com").roles(role)).with(csrf()))
                .andExpect(status().is(role.equals("ADMIN") ? 201 : 403));
    }

    @Test
    void anonymousCannotAccessReportsOrBlockages() throws Exception {
        mvc.perform(get("/api/reports")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/staff/blockages")).andExpect(status().is3xxRedirection());
    }

    @Test
    void userPagesRenderAndFormIgnoresInjectedOwnerAndStoragePath() throws Exception {
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(facilities.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(facility));
        mvc.perform(get("/reports/new").with(user("owner@example.com")))
                .andExpect(status().isOk()).andExpect(view().name("reports/new"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mvc.perform(multipart("/reports").file(photo()).param("facilityId", "10")
                        .param("category", "AC").param("description", "Broken")
                        .param("userId", "999").param("photoPath", "../private.txt")
                        .with(user("owner@example.com").roles("PENGGUNA")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        var saved = org.mockito.ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertEquals(42L, saved.getValue().getUser().getId());
        assertTrue(saved.getValue().getPhotoPath().matches("[0-9a-f-]{36}\\.png"));
        when(reports.findByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(report));
        when(reports.findByIdAndUserId(100L, 42L)).thenReturn(Optional.of(report));
        mvc.perform(get("/reports").with(user("owner@example.com"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lihat laporan")));
        mvc.perform(get("/reports/100").with(user("owner@example.com"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Broken")));
    }

    @Test
    void missingPhotoAndInvalidFieldsCannotCreateReports() throws Exception {
        when(facilities.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(facility));
        mvc.perform(multipart("/reports").param("facilityId", "10").param("category", "AC").param("description", "Broken")
                        .with(user("owner@example.com")).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("reportForm"));
        mvc.perform(multipart("/reports").file(photo()).param("category", " ").param("description", " ")
                        .with(user("owner@example.com")).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("reportForm"));
        verify(reports, never()).save(any());
    }

    @Test
    void photosAreOwnerScopedAndNeverCached() throws Exception {
        report.setPhotoPath(photoStorage.store(photo()));
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(reports.findByIdAndUserId(100L, 42L)).thenReturn(Optional.of(report));
        mvc.perform(get("/reports/100/photo").with(user("owner@example.com")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        when(users.findByEmailIgnoreCase("other@example.com"))
                .thenReturn(Optional.of(account(84L, "other@example.com", Role.PENGGUNA)));
        mvc.perform(get("/reports/100/photo").with(user("other@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get("/reports/100").with(user("other@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get("/reports/100/photo")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/uploads/" + report.getPhotoPath()).with(user("owner@example.com"))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PETUGAS", "ADMIN"})
    void staffCanViewDataPhotoAndUseFormsWithTerminalProtection(String role) throws Exception {
        User staff = account(73L, "staff@example.com", Role.valueOf(role));
        when(users.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staff));
        when(users.findById(73L)).thenReturn(Optional.of(staff));
        report.setPhotoPath(photoStorage.store(photo()));
        mvc.perform(get("/staff/reports/100").with(user("staff@example.com").roles(role)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Mulai penanganan")));
        mvc.perform(get("/reports/100/photo").with(user("staff@example.com").roles(role))).andExpect(status().isOk());
        mvc.perform(get("/api/staff/reports/100").with(user("staff@example.com").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Broken"))
                .andExpect(jsonPath("$.photoPath").doesNotExist()).andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        mvc.perform(post("/staff/reports/100/status").param("status", "IN_PROGRESS")
                        .with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        mvc.perform(post("/staff/reports/100/status").param("status", "RESOLVED").param("resolutionNote", " ")
                        .with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertEquals(ReportStatus.IN_PROGRESS, report.getStatus());
        mvc.perform(post("/staff/reports/100/status").param("status", "RESOLVED").param("resolutionNote", "Fixed")
                        .with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertEquals(73L, report.getHandledBy().getId());
        assertNotNull(report.getHandledAt());
        assertNotNull(report.getResolvedAt());
        mvc.perform(post("/staff/reports/100/status").param("status", "REJECTED")
                        .with(user("staff@example.com").roles(role)).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(get("/staff/reports/100").with(user("staff@example.com").roles(role)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Selesaikan laporan"))));
    }

    @Test
    void formActionsEnforceStaffRoleAndCsrf() throws Exception {
        mvc.perform(post("/staff/reports/100/status").param("status", "IN_PROGRESS")
                        .with(user("owner@example.com").roles("PENGGUNA")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/reports/100").with(user("owner@example.com").roles("PENGGUNA")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/staff/reports/100/status").param("status", "IN_PROGRESS")
                        .with(user("staff@example.com").roles("PETUGAS"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/reports").file(photo()).with(user("owner@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/reports/new")).andExpect(status().is3xxRedirection());
        verify(reports, never()).save(any());
    }

    @Test
    void modalRequestsReturnFragmentsKeepErrorsAndRedirectAfterSuccess() throws Exception {
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(facilities.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(facility));
        mvc.perform(get("/reports/new").header("HX-Request", "true").with(user("owner@example.com")))
                .andExpect(status().isOk()).andExpect(view().name("reports/form :: report-modal"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"report-dialog\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hx-encoding=\"multipart/form-data\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))));
        mvc.perform(multipart("/reports").header("HX-Request", "true").param("facilityId", "10")
                        .param("category", "AC").param("description", "Broken").with(user("owner@example.com")).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("reports/form :: report-modal"))
                .andExpect(model().attributeHasErrors("reportForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Foto laporan wajib diunggah")));
        when(reports.save(any())).thenAnswer(inv -> {
            Report created = inv.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 123L);
            return created;
        });
        mvc.perform(multipart("/reports").file(photo()).header("HX-Request", "true")
                        .param("facilityId", "10").param("category", "AC").param("description", "Broken")
                        .with(user("owner@example.com")).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(header().string("HX-Redirect", "/reports/123"));
    }

    @Test
    void reservationModalDerivesFacilityAndSubmissionRechecksExpiry() throws Exception {
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        var end = LocalDateTime.now(java.time.ZoneId.of("Asia/Jakarta")).minusHours(1);
        var reservation = new com.github.kafeyangasli.prism.feature.reservation.model.Reservation(owner, facility,
                end.minusHours(1), end, "Meeting", null,
                com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus.COMPLETED, null);
        when(reservations.findByIdAndUserId(25L, 42L)).thenReturn(Optional.of(reservation));
        mvc.perform(get("/reports/new").param("reservationId", "25").header("HX-Request", "true")
                        .with(user("owner@example.com")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Fasilitas reservasi")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"reservationId\" value=\"25\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<select"))));
        mvc.perform(multipart("/reports").file(photo()).param("reservationId", "25").param("facilityId", "999")
                        .param("category", "AC").param("description", "Broken").header("HX-Request", "true")
                        .with(user("owner@example.com")).with(csrf()))
                .andExpect(status().isNoContent());
        var saved = org.mockito.ArgumentCaptor.forClass(Report.class);
        verify(reports).save(saved.capture());
        assertEquals(10L, saved.getValue().getFacility().getId());
        clearInvocations(reports);
        reservation.setEndAt(end.minusDays(1));
        mvc.perform(multipart("/reports").file(photo()).param("reservationId", "25")
                        .param("category", "AC").param("description", "Broken").header("HX-Request", "true")
                        .with(user("owner@example.com")).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("reportForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("24 jam")));
        verify(reports, never()).save(any());
        when(users.findByEmailIgnoreCase("other@example.com"))
                .thenReturn(Optional.of(account(84L, "other@example.com", Role.PENGGUNA)));
        mvc.perform(get("/reports/new").param("reservationId", "25").with(user("other@example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/reports").file(photo()).param("reservationId", "25")
                        .param("category", "AC").param("description", "Broken").header("HX-Request", "true")
                        .with(user("other@example.com")).with(csrf()))
                .andExpect(model().attributeHasErrors("reportForm"));
        verify(reports, never()).save(any());
    }

    private MockMultipartFile photo() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("photo", "../../client-name.html", "text/html", output.toByteArray());
    }

    private User account(long id, String email, Role role) {
        User account = new User("Actor", email, "hash", role, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
