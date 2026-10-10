package com.github.kafeyangasli.prism.feature.report;

import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.report.model.*;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:report-workflow;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ReportWorkflowIntegrationTest {
    @TempDir static Path photoDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("prism.storage.reports", () -> photoDirectory.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository types;
    @Autowired ReportRepository reports;
    @Autowired com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository reservations;
    @Autowired java.time.Clock clock;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test void modalReportSuccessSurvivesHtmxRedirect() throws Exception {
        var owner = users.save(new User("Owner", "report-flash@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var type = types.save(new FacilityType("FLASH_ROOM", "Flash Room", null));
        var facility = facilities.save(new Facility("FLASH01", "Flash Facility", type, "Floor 1", 10, null, AdministrativeStatus.ACTIVE));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        var result = mvc.perform(multipart("/reports").file(new MockMultipartFile("photo", "photo.png", "image/png", output.toByteArray()))
                .param("facilityId", facility.getId().toString()).param("category", "AC").param("description", "Broken AC")
                .header("HX-Request", "true").with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf()))
                .andExpect(status().isNoContent()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession();
        mvc.perform(get(result.getResponse().getHeader("HX-Redirect")).session(session).with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Laporan berhasil dikirim.")));
    }

    @Test void submitViewAndProcessThroughRealRepositories() throws Exception {
        var owner = users.save(new User("Owner", "report-owner@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var staff = users.save(new User("Staff", "report-staff@example.test", "hash", Role.PETUGAS, AccountStatus.ACTIVE));
        var type = types.save(new FacilityType("REPORT_ROOM", "Report Room", null));
        var facility = facilities.save(new Facility("REPORT01", "Report Facility", type, "Floor 1", 10, null, AdministrativeStatus.ACTIVE));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        mvc.perform(multipart("/reports").file(new MockMultipartFile("photo", "client.png", "image/png", output.toByteArray()))
                        .param("facilityId", facility.getId().toString()).param("category", "AC").param("description", "Broken AC")
                        .param("userId", staff.getId().toString()).with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        entityManager.flush();
        entityManager.clear();
        var report = reports.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst();
        Long id = report.getId();
        assertEquals(owner.getId(), report.getUser().getId());
        assertNotNull(report.getCreatedAt());
        assertTrue(report.getPhotoPath().matches("[0-9a-f-]{36}\\.png"));
        mvc.perform(get("/reports/" + id).with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Broken AC")));
        mvc.perform(get("/reports/" + id + "/photo").with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().bytes(output.toByteArray()));
        mvc.perform(get("/staff/reports").with(user(staff.getEmail()).roles("PETUGAS")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Mulai penanganan")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/staff/reports/" + id + "/status").param("status", "IN_PROGRESS")
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post("/staff/reports/" + id + "/status").param("status", "RESOLVED").param("resolutionNote", "AC fixed")
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        entityManager.flush();
        entityManager.clear();
        var resolved = reports.findById(id).orElseThrow();
        assertEquals(ReportStatus.RESOLVED, resolved.getStatus());
        assertEquals("AC fixed", resolved.getResolutionNote());
        assertEquals(staff.getId(), resolved.getHandledBy().getId());
        assertNotNull(resolved.getHandledAt());
        assertNotNull(resolved.getResolvedAt());
        assertNotNull(resolved.getUpdatedAt());
        mvc.perform(post("/staff/reports/" + id + "/status").param("status", "REJECTED")
                        .with(user(staff.getEmail()).roles("PETUGAS")).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertEquals(ReportStatus.RESOLVED, reports.findById(id).orElseThrow().getStatus());
    }

    @Test void reservationViewsOfferModalOnlyDuringReportingWindowAndApiEnforcesIt() throws Exception {
        var owner = users.save(new User("Owner", "modal-owner@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var other = users.save(new User("Other", "modal-other@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var type = types.save(new FacilityType("MODAL_ROOM", "Modal Room", null));
        var facility = facilities.save(new Facility("MODAL01", "Reserved Facility", type, "Floor 1", 10, null, AdministrativeStatus.ACTIVE));
        var otherFacility = facilities.save(new Facility("MODAL02", "Other Facility", type, "Floor 2", 10, null, AdministrativeStatus.ACTIVE));
        var now = java.time.LocalDateTime.now(clock);
        var completed = reservations.save(new com.github.kafeyangasli.prism.feature.reservation.model.Reservation(owner, facility,
                now.minusHours(2), now.minusHours(1), "Meeting", null,
                com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus.COMPLETED, null));
        var expired = reservations.save(new com.github.kafeyangasli.prism.feature.reservation.model.Reservation(owner, facility,
                now.minusHours(27), now.minusHours(26), "Past meeting", null,
                com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus.COMPLETED, null));
        var upcoming = reservations.save(new com.github.kafeyangasli.prism.feature.reservation.model.Reservation(owner, facility,
                now.plusHours(1), now.plusHours(2), "Future meeting", null,
                com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus.APPROVED, null));
        String url = "/reports/new?reservationId=" + completed.getId();
        mvc.perform(get("/reservations").with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().string(containsString(url)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/reports/new?reservationId=" + expired.getId()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/reports/new?reservationId=" + upcoming.getId()))));
        mvc.perform(get("/reservations/" + completed.getId()).with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Laporkan masalah")))
                .andExpect(content().string(containsString("hx-target=\"#report-modal-region\"")));
        mvc.perform(get("/reservations/" + expired.getId()).with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Laporkan masalah"))));
        mvc.perform(get(url).header("HX-Request", "true").with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(view().name("reports/form :: report-modal"))
                .andExpect(content().string(containsString("Reserved Facility")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<select"))));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        var data = new MockMultipartFile("data", "", "application/json", ("{\"reservationId\":" + completed.getId()
                + ",\"facilityId\":" + otherFacility.getId() + ",\"category\":\"AC\",\"description\":\"Broken\"}").getBytes());
        mvc.perform(multipart("/api/reports").file(data)
                        .file(new MockMultipartFile("photo", "photo.png", "image/png", output.toByteArray()))
                        .with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.facility.id").value(facility.getId()));
        mvc.perform(multipart("/api/reports").file(data)
                        .file(new MockMultipartFile("photo", "photo.png", "image/png", output.toByteArray()))
                        .with(user(other.getEmail()).roles("PENGGUNA")).with(csrf()))
                .andExpect(status().isNotFound());
        var expiredData = new MockMultipartFile("data", "", "application/json", ("{\"reservationId\":" + expired.getId()
                + ",\"category\":\"AC\",\"description\":\"Broken\"}").getBytes());
        mvc.perform(multipart("/api/reports").file(expiredData)
                        .file(new MockMultipartFile("photo", "photo.png", "image/png", output.toByteArray()))
                        .with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf()))
                .andExpect(status().isBadRequest());
        assertEquals(1, reports.findByUserIdOrderByCreatedAtDesc(owner.getId()).size());
    }
}
