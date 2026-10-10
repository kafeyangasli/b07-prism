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
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository;
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
import com.github.kafeyangasli.prism.support.WithPrismUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.persistence.EntityManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static com.github.kafeyangasli.prism.support.PrismTestUsers.user;
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
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 9, 22);
    private static final LocalDateTime NOW = REPORT_DATE.atTime(10, 0);

    @Autowired StaffDashboardService dashboardService;
    @Autowired AdministrationReportService reportService;
    @Autowired ReportExportService exportService;
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository facilityTypes;
    @Autowired ReservationRepository reservations;
    @Autowired ReportRepository reports;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeRepository blockageTypes;
    @Autowired MockMvc mockMvc;
    @Autowired EntityManager entityManager;

    private User requester;
    private User staff;
    private Facility room;

    @BeforeEach
    void setUp() {
        requester = users.save(new User("Pemohon", "dash-user@example.test", "hash",
                Role.PENGGUNA, AccountStatus.ACTIVE));
        staff = users.save(new User("Petugas", "dash-staff@example.test", "hash",
                Role.PETUGAS, AccountStatus.ACTIVE));
        FacilityType laboratory = facilityTypes.save(new FacilityType("LAB", "Laboratorium", null));
        room = facilities.save(new Facility("LAB-1", "Laboratorium 1", laboratory,
                "Gedung B", 25, null, AdministrativeStatus.ACTIVE));
        // This historical fixture existed and was active at the start of the reporting period.
        var created = REPORT_DATE.atStartOfDay();
        org.springframework.test.util.ReflectionTestUtils.setField(room, "createdAt", created);
        org.springframework.test.util.ReflectionTestUtils.setField(room.getStatusHistory().getFirst(), "effectiveAt", created);
        entityManager.createNativeQuery("update facilities set created_at = :at where id = :id")
                .setParameter("at", created).setParameter("id", room.getId()).executeUpdate();
    }

    @Test
    @WithPrismUser(roles = "PETUGAS")
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
    @WithPrismUser(roles = "ADMIN")
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
        Report issue = reports.save(new Report(requester, room, "Peralatan", "Rusak", null, ReportStatus.NEW));
        issue.setCreatedAt(REPORT_DATE.atTime(9, 0));
        entityManager.createNativeQuery("update reports set created_at = :createdAt where id = :id")
                .setParameter("createdAt", issue.getCreatedAt()).setParameter("id", issue.getId()).executeUpdate();

        RecapResult recap = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                room.getId(), "Laboratorium", "Gedung B"));

        assertThat(recap.rows()).hasSize(1);
        assertThat(recap.rows().get(0).approvedSlots()).isEqualTo(4);
        assertThat(recap.rows().get(0).bookableSlots()).isEqualTo(24);
        assertThat(recap.rows().get(0).issueCount()).isEqualTo(1);
        assertThat(recap.rows().get(0).occupancyPercent()).isEqualByComparingTo("16.67");
        assertThat(recap.rows().get(0).facilityType()).isEqualTo("Laboratorium");

        RecapResult byCode = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                room.getId(), " lab ", " gedung b "));
        assertThat(byCode.rows()).isEqualTo(recap.rows());
        assertThat(reportService.generate(new RecapFilter(REPORT_DATE.plusDays(1), REPORT_DATE.plusDays(1),
                room.getId(), "LAB", null)).rows().getFirst().approvedSlots()).isZero();
        assertThat(reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                room.getId(), "LAB", "Gedung A")).rows()).isEmpty();
        assertThat(reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                Long.MAX_VALUE, "LAB", null)).rows()).isEmpty();

        RecapResult filteredOut = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                null, "Aula", null));
        assertThat(filteredOut.rows()).isEmpty();
    }

    @Test
    @WithPrismUser(roles = "ADMIN")
    void relationNamesCodesAndInactiveTypesRemainAvailableForHistoricalRecaps() throws Exception {
        FacilityType type = room.getFacilityType();
        type.updateDetails("Laboratorium Baru", null);
        type.deactivate();
        facilityTypes.saveAndFlush(type);
        FacilityType otherType = facilityTypes.save(new FacilityType("HALL", "Aula", null));
        facilities.save(new Facility("HALL-2", "Aula Lain", otherType, "Gedung A", 100, null,
                AdministrativeStatus.ACTIVE));
        assertThat(reportService.typesForFilter()).extracting(FacilityType::getCode).contains("LAB", "HALL");
        assertThat(reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE, null, "lab", null)).rows())
                .extracting("facilityName", "facilityType")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Laboratorium 1", "Laboratorium Baru"));
        assertThat(reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE, null, "laboratorium baru", null)).rows())
                .hasSize(1);
        assertThat(reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE, null, "Laboratorium", null)).rows())
                .isEmpty();
        for (String selection : new String[] {"LAB", " lab ", "Laboratorium Baru"}) {
            String html = mockMvc.perform(get("/admin/recap").param("facilityType", selection)
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).contains("<select id=\"recap-type\"", "Semua tipe fasilitas", "value=\"HALL\"")
                    .doesNotContain("<input id=\"recap-type\"", "<datalist")
                    .containsPattern("(?s)<option(?=[^>]*value=\"LAB\")(?=[^>]*selected=\"selected\")[^>]*>\\s*Laboratorium Baru\\s*</option>");
        }
    }

    @Test
    @WithPrismUser(roles = "ADMIN")
    void filteredExportsConsumeTheSameRecapRows() throws Exception {
        FacilityType other = facilityTypes.save(new FacilityType("HALL", "Aula", null));
        facilities.save(new Facility("HALL-2", "Excluded Hall", other, "Gedung A", 100, null,
                AdministrativeStatus.ACTIVE));
        RecapResult expected = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE,
                room.getId(), "lab", "Gedung B"));
        assertThat(expected.rows()).hasSize(1);
        byte[] expectedCsv = exportService.toCsv(expected);
        byte[] expectedPdf = exportService.toPdf(expected);
        String expectedSheet = readZipEntry(exportService.toXlsx(expected), "xl/worksheets/sheet1.xml");
        for (String format : new String[] {"csv", "xlsx", "pdf"}) {
            byte[] actual = mockMvc.perform(get("/admin/recap/export")
                            .with(user("admin").roles("ADMIN"))
                            .param("startDate", REPORT_DATE.toString()).param("endDate", REPORT_DATE.toString())
                            .param("facilityId", room.getId().toString()).param("facilityType", "lab")
                            .param("location", "Gedung B").param("format", format))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            if (format.equals("xlsx")) {
                assertThat(readZipEntry(actual, "xl/worksheets/sheet1.xml"))
                        .isEqualTo(expectedSheet);
            } else if (format.equals("pdf")) {
                assertThat(pdfText(actual)).isEqualTo(pdfText(expectedPdf));
            } else {
                assertThat(actual).isEqualTo(expectedCsv);
            }
        }
        var model = mockMvc.perform(get("/admin/recap").param("facilityType", "LAB")
                        .with(user("admin").roles("ADMIN"))
                        .param("facilityId", room.getId().toString()).param("location", "Gedung B"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        RecapFilter defaultPeriod = (RecapFilter) model.get("filter");
        assertThat(defaultPeriod.facilityId()).isEqualTo(room.getId());
        assertThat(defaultPeriod.facilityType()).isEqualTo("LAB");
        assertThat(defaultPeriod.location()).isEqualTo("Gedung B");
    }

    @Test
    @WithPrismUser(roles = "ADMIN")
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
        assertThat(pdfText(pdf)).contains("Kode", "Fasilitas", "Tidak ada data");
    }

    @Test
    @WithPrismUser(roles = "PETUGAS")
    void dashboardTemplateRenders() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/dashboard"));
    }

    @Test
    @WithPrismUser(roles = "PETUGAS")
    void operationalPagesRenderIndependently() throws Exception {
        mockMvc.perform(get("/staff/reservations"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/reservations"));

        mockMvc.perform(get("/staff/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/reports"));
    }

    @Test
    @WithPrismUser(roles = "PENGGUNA")
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
                .doesNotContain("href=\"/staff/dashboard\"", "href=\"/admin/users\"", "href=\"/admin/facilities\"");
    }

    @Test
    void localHtmxAssetIsPubliclyAvailable() throws Exception {
        mockMvc.perform(get("/vendor/htmx.min.js"))
                .andExpect(status().isOk());
    }

    @Test
    @WithPrismUser(roles = "ADMIN")
    void recapTemplateRenders() throws Exception {
        mockMvc.perform(get("/admin/recap"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/recap"));
    }

    @Test
    @WithPrismUser(roles = "PETUGAS")
    void recapRejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/recap"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithPrismUser(roles = "PENGGUNA")
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

    private String pdfText(byte[] bytes) throws Exception {
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
        }
    }

    @Test @WithPrismUser(roles = "ADMIN")
    void laterDeactivationCannotEraseHistoricalCapacity() {
        var filter = new RecapFilter(REPORT_DATE, REPORT_DATE, room.getId(), null, null);
        var start = REPORT_DATE.atTime(9, 0);
        reservations.save(new Reservation(requester, room, start, start.plusHours(1), "Historical", null,
                ReservationStatus.COMPLETED, NOW.minusDays(1)));
        var before = reportService.generate(filter).rows().getFirst();
        assertThat(before.bookableSlots()).isEqualTo(26);
        room.changeAdministrativeStatus(AdministrativeStatus.INACTIVE, REPORT_DATE.plusDays(1).atStartOfDay());
        facilities.saveAndFlush(room);
        entityManager.clear();
        assertThat(reportService.generate(filter).rows().getFirst()).isEqualTo(before);
        var inactive = reportService.generate(new RecapFilter(REPORT_DATE.plusDays(1), REPORT_DATE.plusDays(1), room.getId(), null, null)).rows().getFirst();
        assertThat(inactive.bookableSlots()).isZero();
        assertThat(inactive.occupancyPercent()).isNull();
    }

    @Test @WithPrismUser(roles = "ADMIN")
    void historicalWeekendReservationsCountWithoutChangingBookingCalendar() {
        LocalDate saturday = LocalDate.of(2026, 9, 26);
        var start = saturday.atTime(9, 0);
        reservations.save(new Reservation(requester, room, start, start.plusHours(1), "Legacy", null,
                ReservationStatus.APPROVED, NOW));
        reservations.save(new Reservation(requester, room, start.plusMinutes(30), start.plusHours(1), "Duplicate interval", null,
                ReservationStatus.COMPLETED, NOW));
        var row = reportService.generate(new RecapFilter(saturday, saturday, room.getId(), null, null)).rows().getFirst();
        assertThat(row.approvedSlots()).isEqualTo(2);
        assertThat(row.bookableSlots()).isZero();
        assertThat(row.occupancyPercent()).isNull();
        assertThat(row.occupancyDisplay()).contains("kapasitas nol");
    }

    @Test @WithPrismUser(roles = "ADMIN")
    void partialOverlappingBlockagesRemoveEachTouchedSlotOnlyOnce() {
        var open = REPORT_DATE.atTime(7, 0);
        var type = blockageTypes.save(new BlockageType("PARTIAL", "Partial", null));
        blockages.save(new FacilityBlockage(room, type, null, open.plusMinutes(10), open.plusMinutes(20),
                BlockageStatus.COMPLETED, "Partial", null, staff));
        blockages.save(new FacilityBlockage(room, type, null, open.plusMinutes(15), open.plusMinutes(45),
                BlockageStatus.COMPLETED, "Overlap", null, staff));
        blockages.save(new FacilityBlockage(room, type, null, open.plusHours(1), open.plusHours(2),
                BlockageStatus.CANCELLED, "Cancelled", null, staff));
        var row = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE, room.getId(), null, null)).rows().getFirst();
        assertThat(row.bookableSlots()).isEqualTo(24);
        assertThat(row.approvedSlots()).isZero();
        assertThat(row.occupancyPercent()).isEqualByComparingTo("0.00");
    }

    @Test @WithPrismUser(roles = "ADMIN")
    void missingLegacyStatusHistoryIsIndeterminateInAllFormatsAndTotals() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(room.getStatusHistory().getFirst(), "effectiveAt", REPORT_DATE.plusDays(1).atStartOfDay());
        org.springframework.test.util.ReflectionTestUtils.setField(room.getStatusHistory().getFirst(), "atCreation", false);
        var start = REPORT_DATE.atTime(9, 0);
        reservations.save(new Reservation(requester, room, start, start.plusHours(1), "Legacy", null,
                ReservationStatus.APPROVED, NOW));
        var recap = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE, room.getId(), null, null));
        assertThat(recap.rows().getFirst().approvedSlots()).isEqualTo(2);
        assertThat(recap.rows().getFirst().bookableSlots()).isNull();
        assertThat(recap.rows().getFirst().occupancyPercent()).isNull();
        assertThat(recap.totalBookableSlots()).isNull();
        assertThat(new String(exportService.toCsv(recap), StandardCharsets.UTF_8)).contains("Tidak diketahui");
        assertThat(readZipEntry(exportService.toXlsx(recap), "xl/worksheets/sheet1.xml")).contains("Tidak diketahui");
        assertThat(pdfText(exportService.toPdf(recap))).contains("Tidak diketahui");
    }

    @Test @WithPrismUser(roles = "ADMIN")
    void intradayStatusTransitionsRequireWholeSlotsToBeActive() {
        room.changeAdministrativeStatus(AdministrativeStatus.INACTIVE, REPORT_DATE.atTime(7, 15));
        room.changeAdministrativeStatus(AdministrativeStatus.ACTIVE, REPORT_DATE.atTime(7, 45));
        facilities.saveAndFlush(room);
        var row = reportService.generate(new RecapFilter(REPORT_DATE, REPORT_DATE, room.getId(), null, null)).rows().getFirst();
        assertThat(row.bookableSlots()).isEqualTo(24);
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
