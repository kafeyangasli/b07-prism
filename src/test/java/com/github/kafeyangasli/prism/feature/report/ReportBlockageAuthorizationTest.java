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
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ReportController.class, StaffReportController.class,
            BlockageController.class, BlockageTypeController.class,
            ReportService.class, BlockageService.class, StaffActorResolver.class})
    static class Config {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ReportRepository reports() { return mock(ReportRepository.class); }
        @Bean FacilityRepository facilities() { return mock(FacilityRepository.class); }
        @Bean ReservationRepository reservations() { return mock(ReservationRepository.class); }
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
        mvc.perform(multipart("/api/reports").file(data).with(user("owner@example.com").roles("PENGGUNA")).with(csrf()))
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

    private User account(long id, String email, Role role) {
        User account = new User("Actor", email, "hash", role, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
