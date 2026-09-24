package com.github.kafeyangasli.prism.feature.administration;

import com.github.kafeyangasli.prism.feature.administration.dto.RecapFilter;
import com.github.kafeyangasli.prism.feature.administration.dto.RecapResult;
import com.github.kafeyangasli.prism.feature.administration.service.AdministrationReportService;
import com.github.kafeyangasli.prism.feature.administration.service.ReportExportService;
import com.github.kafeyangasli.prism.feature.administration.service.StaffDashboardService;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:administration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdministrationFeaturesIntegrationTest.FixedClockConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class AdministrationFeaturesIntegrationTest {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final LocalDate REPORT_DATE = LocalDate.now(WIB);
    private static final LocalDateTime NOW = REPORT_DATE.atTime(10, 0);

    @Autowired StaffDashboardService dashboardService;
    @Autowired AdministrationReportService reportService;
    @Autowired ReportExportService exportService;
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired ReservationRepository reservations;
    @Autowired ReportRepository reports;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeRepository blockageTypes;
    @Autowired MockMvc mockMvc;

    private User requester;
    private User staff;
    private Facility room;

    @BeforeEach
    void setUp() {
        requester = users.save(new User("Pemohon", "dash-user@example.test", "hash",
                Role.PENGGUNA, AccountStatus.ACTIVE));
        staff = users.save(new User("Petugas", "dash-staff@example.test", "hash",
                Role.PETUGAS, AccountStatus.ACTIVE));
        room = facilities.save(new Facility("LAB-1", "Laboratorium 1", "Laboratorium",
                "Gedung B", 25, null, AdministrativeStatus.ACTIVE));
    }

    @Test
    @WithMockUser(roles = "PETUGAS")
    void dashboardExcludesExpiredAndReachedStartAndShowsOnlyUnresolvedReportsWithSorting() {
        Reservation laterUse = reservations.save(new Reservation(requester, room,
                NOW.plusDays(2).withHour(9), NOW.plusDays(2).withHour(10), "B", null,
                ReservationStatus.PENDING, NOW.plusHours(4)));
        Reservation earlierUse = reservations.save(new Reservation(requester, room,
                NOW.plusDays(1).withHour(9), NOW.plusDays(1).withHour(10), "A", null,
                ReservationStatus.PENDING, NOW.plusHours(4)));
        reservations.save(new Reservation(requester, room, NOW.plusDays(1).withHour(11),
                NOW.plusDays(1).withHour(12), "Expired", null,
                ReservationStatus.PENDING, NOW.minusMinutes(1)));
        reservations.save(new Reservation(requester, room, NOW.minusHours(1), NOW.plusHours(1),
                "Started", null, ReservationStatus.PENDING, NOW.plusHours(1)));
        reports.save(new Report(requester, room, "AC", "Tidak dingin", null, ReportStatus.NEW));
        reports.save(new Report(requester, room, "Kursi", "Rusak", null, ReportStatus.IN_PROGRESS));
        reports.save(new Report(requester, room, "Lampu", "Mati", null, ReportStatus.RESOLVED));

        var byStart = dashboardService.load("start");
        var byCreated = dashboardService.load("created");

        assertThat(byStart.pendingReservations()).extracting("id")
                .containsExactly(earlierUse.getId(), laterUse.getId());
        assertThat(byCreated.pendingReservations()).extracting("id")
                .containsExactly(laterUse.getId(), earlierUse.getId());
        assertThat(byStart.unresolvedReports()).hasSize(2);
        assertThat(byStart.unresolvedReports()).extracting("status")
                .containsExactly(ReportStatus.NEW, ReportStatus.IN_PROGRESS);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recapCountsApprovedAndCompletedSlotsSubtractsBlockageAndAppliesFilters() {
        LocalDateTime open = REPORT_DATE.atTime(7, 0);
        reservations.save(new Reservation(requester, room, open, open.plusHours(1), "Approved", null,
                ReservationStatus.APPROVED, NOW.minusDays(1)));
        reservations.save(new Reservation(requester, room, open.plusHours(1), open.plusHours(2), "Completed", null,
                ReservationStatus.COMPLETED, NOW.minusDays(1)));
        reservations.save(new Reservation(requester, room, open.plusHours(2), open.plusHours(3), "Cancelled", null,
                ReservationStatus.CANCELLED, NOW.minusDays(1)));
        reservations.save(new Reservation(requester, room, open.plusHours(3), open.plusHours(4), "Rejected", null,
                ReservationStatus.REJECTED, NOW.minusDays(1)));
        BlockageType type = blockageTypes.save(new BlockageType("MAINTENANCE", "Pemeliharaan", null));
        blockages.save(new FacilityBlockage(room, type, null, open.plusHours(4), open.plusHours(5),
                BlockageStatus.COMPLETED, "Pemeliharaan", null, staff));
        reports.save(new Report(requester, room, "Peralatan", "Rusak", null, ReportStatus.NEW));

        RecapResult recap = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                room.getId(), "Laboratorium", "Gedung B"));

        assertThat(recap.rows()).hasSize(1);
        assertThat(recap.rows().get(0).approvedSlots()).isEqualTo(4);
        assertThat(recap.rows().get(0).bookableSlots()).isEqualTo(24);
        assertThat(recap.rows().get(0).issueCount()).isEqualTo(1);
        assertThat(recap.rows().get(0).occupancyPercent()).isEqualByComparingTo("16.67");

        RecapResult filteredOut = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                null, "Aula", null));
        assertThat(filteredOut.rows()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void csvXlsxAndPdfExportsAreValidWithDataAndWhenEmpty() throws Exception {
        RecapResult empty = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                null, "Tidak Ada", null));

        byte[] csv = exportService.toCsv(empty);
        byte[] xlsx = exportService.toXlsx(empty);
        byte[] pdf = exportService.toPdf(empty);

        assertThat(new String(csv, StandardCharsets.UTF_8)).contains("Kode Fasilitas", "Frekuensi Laporan");
        assertThat(xlsx).startsWith(new byte[]{'P', 'K'});
        assertThat(readZipEntry(xlsx, "xl/worksheets/sheet1.xml"))
                .contains("Kode Fasilitas", "Frekuensi Laporan");
        assertThat(new String(pdf, 0, 8, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-1.4");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("Kode Fasilitas");
    }

    @Test
    @WithMockUser(roles = "PETUGAS")
    void dashboardTemplateRenders() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/dashboard"));
    }

    @Test
    @WithMockUser(roles = "PENGGUNA")
    void facilityCatalogueRendersSharedLayoutAndRoleAwareNavigation() throws Exception {
        String html = mockMvc.perform(get("/facilities"))
                .andExpect(status().isOk())
                .andExpect(view().name("facilities/list"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("/css/app.css", "/vendor/htmx.min.js", "hx-target=\"#facility-results\"",
                        "Reservasi Saya", "status-active")
                .doesNotContain(">Dashboard<", ">Pengguna<", ">Kelola Fasilitas<");
    }

    @Test
    void localHtmxAssetIsPubliclyAvailable() throws Exception {
        mockMvc.perform(get("/vendor/htmx.min.js"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recapTemplateRenders() throws Exception {
        mockMvc.perform(get("/admin/recap"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/recap"));
    }

    @Test
    @WithMockUser(roles = "PETUGAS")
    void recapRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/recap"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PENGGUNA")
    void dashboardRejectsOrdinaryUser() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().isForbidden());
    }

    private String readZipEntry(byte[] bytes, String expectedName) throws Exception {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (expectedName.equals(entry.getName())) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("Missing XLSX entry " + expectedName);
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
