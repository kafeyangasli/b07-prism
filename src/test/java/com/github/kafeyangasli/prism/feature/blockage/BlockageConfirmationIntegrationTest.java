package com.github.kafeyangasli.prism.feature.blockage;

import com.github.kafeyangasli.prism.feature.blockage.dto.*;
import com.github.kafeyangasli.prism.feature.blockage.model.*;
import com.github.kafeyangasli.prism.feature.blockage.repository.*;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageService;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:blockage-confirmation;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class BlockageConfirmationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired BlockageService service;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository facilityTypes;
    @Autowired BlockageTypeRepository types;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean ReservationRepository reservations;
    User staff;
    User owner;
    Facility facility;
    BlockageType type;
    LocalDateTime start = LocalDateTime.of(2030, 1, 1, 8, 0);
    LocalDateTime end = start.plusHours(3);

    @BeforeEach void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        staff = users.save(new User("Staff", "staff-" + suffix + "@example.test", "hash", Role.PETUGAS, AccountStatus.ACTIVE));
        owner = users.save(new User("Owner", "owner-" + suffix + "@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var facilityType = facilityTypes.save(new FacilityType("ROOM_" + suffix, "Room " + suffix, null));
        facility = facilities.save(new Facility("BLOCK_" + suffix, "Room", facilityType, "Floor", 10, null, AdministrativeStatus.ACTIVE));
        type = types.save(new BlockageType("PLANNED_MAINTENANCE_" + suffix, "Maintenance", null));
        authenticate(staff);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(User actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getEmail(), "unused", AuthorityUtils.createAuthorityList("ROLE_" + actor.getRole())));
    }
    private BlockageImpactPreviewRequest request() {
        var request = new BlockageImpactPreviewRequest(facility.getId(), start, end);
        request.setBlockageTypeId(type.getId());
        request.setPublicReason("Maintenance");
        return request;
    }
    private Reservation reservation(ReservationStatus status, LocalDateTime from, LocalDateTime until) {
        return reservations.save(new Reservation(owner, facility, from, until, "Meeting", null, status, null));
    }
    private String token(BlockageImpactPreviewRequest request) {
        var preview = service.previewBlockageImpact(request);
        request.setConfirmationToken(preview.getConfirmationToken());
        request.setConfirmed(true);
        return preview.getConfirmationToken();
    }

    @Test void previewPageShowsSeparateCountsAndRequiresExplicitConfirmation() throws Exception {
        reservation(ReservationStatus.APPROVED, start, start.plusHours(1));
        reservation(ReservationStatus.PENDING, start.plusHours(1), end);
        mvc.perform(get("/staff/blockages").with(user(staff.getEmail()).roles("PETUGAS")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Lihat dampak reservasi")))
                .andExpect(content().string(not(containsString("Konfirmasi dan simpan"))));
        var response = mvc.perform(post("/staff/blockages/preview")
                        .param("facilityId", facility.getId().toString()).param("blockageTypeId", type.getId().toString())
                        .param("startAt", start.toString()).param("plannedEndAt", end.toString()).param("publicReason", "Maintenance")
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("staff/blockages"))
                .andExpect(content().string(containsString("APPROVED yang akan dibatalkan")))
                .andExpect(content().string(containsString("PENDING yang akan ditolak")))
                .andExpect(content().string(containsString("name=\"confirmed\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andReturn();
        var preview = (BlockageImpactPreviewResponse) response.getModelAndView().getModel().get("preview");
        assertEquals(1, preview.getApprovedCount());
        assertEquals(1, preview.getPendingCount());
        assertNotNull(preview.getConfirmationToken());
        mvc.perform(post("/staff/blockages")
                        .param("facilityId", facility.getId().toString()).param("blockageTypeId", type.getId().toString())
                        .param("startAt", start.toString()).param("plannedEndAt", end.toString()).param("publicReason", "Maintenance")
                        .param("confirmationToken", preview.getConfirmationToken())
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("konfirmasikan secara eksplisit")));
        assertTrue(blockages.findByFacilityIdOrderByStartAtAsc(facility.getId()).isEmpty());
        mvc.perform(post("/staff/blockages")
                        .param("facilityId", facility.getId().toString()).param("blockageTypeId", type.getId().toString())
                        .param("startAt", start.toString()).param("plannedEndAt", end.toString()).param("publicReason", "Maintenance")
                        .param("confirmationToken", preview.getConfirmationToken()).param("confirmed", "true")
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/blockages"))
                .andExpect(flash().attributeExists("success"));
        assertEquals(1, blockages.findByFacilityIdOrderByStartAtAsc(facility.getId()).size());
    }

    @Test void confirmedCreateCommitsBothImpactsAndAuditFields() {
        Long approved = reservation(ReservationStatus.APPROVED, start, start.plusHours(1)).getId();
        Long pending = reservation(ReservationStatus.PENDING, start.plusHours(1), end).getId();
        var request = request();
        token(request);
        var saved = service.createBlockage(request);
        entityManager.flush();
        entityManager.clear();
        assertNotNull(blockages.findById(saved.getId()).orElseThrow());
        var cancelled = reservations.findById(approved).orElseThrow();
        var rejected = reservations.findById(pending).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
        assertEquals(ReservationStatus.REJECTED, rejected.getStatus());
        assertEquals("BLOCKAGE_" + type.getCode(), cancelled.getReasonCode());
        assertEquals(staff.getId(), cancelled.getCancelledBy().getId());
        assertNotNull(cancelled.getCancelledAt());
        assertEquals(staff.getId(), rejected.getProcessedBy().getId());
        assertNotNull(rejected.getProcessedAt());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void changedImpactLeavesDatabaseUntouchedAndNewPreviewSucceeds() {
        Long approved = reservation(ReservationStatus.APPROVED, start, start.plusHours(1)).getId();
        var request = request();
        token(request);
        Long pending = reservation(ReservationStatus.PENDING, start.plusHours(1), end).getId();
        assertEquals("IMPACT_CHANGED", assertThrows(com.github.kafeyangasli.prism.shared.exception.BusinessRuleException.class,
                () -> service.createBlockage(request)).getCode());
        assertTrue(blockages.findByFacilityIdOrderByStartAtAsc(facility.getId()).isEmpty());
        assertEquals(ReservationStatus.APPROVED, reservations.findById(approved).orElseThrow().getStatus());
        assertEquals(ReservationStatus.PENDING, reservations.findById(pending).orElseThrow().getStatus());
        token(request);
        service.createBlockage(request);
        assertEquals(ReservationStatus.CANCELLED, reservations.findById(approved).orElseThrow().getStatus());
        assertEquals(ReservationStatus.REJECTED, reservations.findById(pending).orElseThrow().getStatus());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reservationWriteFailureRollsBackBlockageAndAllPriorImpacts() {
        Long approved = reservation(ReservationStatus.APPROVED, start, start.plusHours(1)).getId();
        Long pending = reservation(ReservationStatus.PENDING, start.plusHours(1), end).getId();
        var request = request();
        token(request);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Forced impact write failure"))
                .when(reservations).save(argThat(r -> r != null && r.getId().equals(pending)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.createBlockage(request));
        assertTrue(blockages.findByFacilityIdOrderByStartAtAsc(facility.getId()).isEmpty());
        assertEquals(ReservationStatus.APPROVED, reservations.findById(approved).orElseThrow().getStatus());
        assertEquals(ReservationStatus.PENDING, reservations.findById(pending).orElseThrow().getStatus());
    }

    @Test void updatePreviewOnlyCoversExtensionAndCompletionNeverRestores() throws Exception {
        var request = request();
        token(request);
        var saved = service.createBlockage(request);
        Long id = saved.getId();
        // Boundary touching an existing end must be included by the extension, not by original creation.
        Long extraApproved = reservation(ReservationStatus.APPROVED, end, end.plusHours(1)).getId();
        Long extraPending = reservation(ReservationStatus.PENDING, end.plusHours(1), end.plusHours(2)).getId();
        var update = new UpdateBlockageRequest(end.plusHours(2), "Extended", null);
        var preview = service.previewUpdate(id, update);
        assertEquals(end, preview.getStartAt());
        assertEquals(1, preview.getApprovedCount());
        assertEquals(1, preview.getPendingCount());
        update.setConfirmationToken(preview.getConfirmationToken());
        update.setConfirmed(true);
        service.updateOrExtendBlockage(id, update);
        service.earlyCompleteBlockage(id, new EarlyCompletionRequest("Repairs complete"));
        entityManager.flush();
        entityManager.clear();
        var completed = blockages.findById(id).orElseThrow();
        assertEquals(BlockageStatus.COMPLETED, completed.getStatus());
        assertEquals("Repairs complete", completed.getEarlyCompletionReason());
        assertEquals(staff.getId(), completed.getEndedBy().getId());
        assertNotNull(completed.getActualEndAt());
        assertEquals(ReservationStatus.CANCELLED, reservations.findById(extraApproved).orElseThrow().getStatus());
        assertEquals(ReservationStatus.REJECTED, reservations.findById(extraPending).orElseThrow().getStatus());
    }

    @Test void apiAndStaffPageEnforceRolesCsrfAndConfirmation() throws Exception {
        String json = "{\"facilityId\":" + facility.getId() + ",\"blockageTypeId\":" + type.getId()
                + ",\"startAt\":\"2030-01-01T08:00:00\",\"publicReason\":\"Maintenance\"}";
        mvc.perform(post("/api/staff/blockages").contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIRMATION_REQUIRED"));
        mvc.perform(post("/api/staff/blockages/preview").contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/staff/blockages").with(user(owner.getEmail()).roles("PENGGUNA"))).andExpect(status().isForbidden());
        mvc.perform(post("/staff/blockages/preview").with(user(staff.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/blockages").with(user(staff.getEmail()).roles("ADMIN"))).andExpect(status().isOk());
    }
}
