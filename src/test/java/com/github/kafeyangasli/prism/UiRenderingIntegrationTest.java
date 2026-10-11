package com.github.kafeyangasli.prism;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.github.kafeyangasli.prism.support.WithPrismUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ui-rendering;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class UiRenderingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired ReservationRepository reservations;
    @Autowired com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository facilityTypes;
    @Autowired com.github.kafeyangasli.prism.feature.report.repository.ReportRepository reports;
    @Autowired com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository blockageTypes;
    Facility room;
    Reservation reservation;

    @BeforeEach void seed() {
        User user = users.save(new User("Pengguna UI", "ui@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var type = facilityTypes.save(new com.github.kafeyangasli.prism.feature.facility.model.FacilityType("LAB-UI", "Laboratorium", null));
        room = facilities.save(new Facility("LAB-UI", "Laboratorium Informatika", type, "Gedung Informatika, Lantai 2", 30, "Ruang kegiatan akademik dan diskusi mahasiswa.", AdministrativeStatus.ACTIVE));
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0);
        reservation = reservations.save(new Reservation(user, room, start, start.plusHours(2), "Diskusi kelompok mahasiswa", "proposal.pdf", ReservationStatus.PENDING, start.minusHours(12)));
    }

    String render(String url, String snapshot) throws Exception {
        String html = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("/css/app.css", "id=\"main-content\"")
            .doesNotContain("th:field=", "th:replace=", "sec:authorize=");
        assertThat(html.split("<main", -1)).hasSize(2);
        assertThat(html).contains("data-theme-toggle", "/js/theme.js");
        assertThat(html.indexOf("/js/theme.js")).isLessThan(html.indexOf("/css/app.css"));
        if (Boolean.getBoolean("prism.ui.snapshots")) {
            Path directory = Path.of("target/ui-preview");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(snapshot + ".html"), html);
        }
        return html;
    }

    @Test void publicPagesAndLogoRemainPublic() throws Exception {
        assertThat(render("/", "landing"))
            .contains("Mulai Reservasi", "Lihat Fasilitas", "Platform for Reservation and Issue Management", "/images/prism-light.svg", "aria-label=\"Beranda PRISM\"")
            .doesNotContain("data-navigation");
        String catalog = render("/facilities", "catalog");
        assertThat(catalog).contains("id=\"facility-results\"", "hx-target=\"#facility-results\"", "/images/prism-light.svg", "aria-label=\"Beranda PRISM\"")
            .doesNotContain("id=\"dashboard-sidebar\"", ">Beranda</a>")
            .doesNotContain("href=\"/admin/users\"", "href=\"/staff/dashboard\"");
        assertThat(render("/facilities/" + room.getId(), "facility")).contains("/availability", "Cek ketersediaan");
        render("/facilities/" + room.getId() + "/availability", "public-availability");
        assertThat(render("/login?error", "login"))
            .contains("name=\"username\"", "name=\"password\"", "name=\"_csrf\"", "href=\"/\"", "/images/prism-dark.svg", "/images/prism-light.svg")
            .doesNotContain("data-navigation");
        assertThat(render("/auth/register", "register"))
            .contains("name=\"email\"", "name=\"_csrf\"", "href=\"/\"", "/images/prism-dark.svg", "/images/prism-light.svg")
            .doesNotContain("data-navigation");
        mvc.perform(get("/images/prism-light.svg")).andExpect(status().isOk());
        mvc.perform(get("/images/prism-dark.svg")).andExpect(status().isOk());
        room.setAdministrativeStatus(AdministrativeStatus.INACTIVE);
        facilities.saveAndFlush(room);
        assertThat(render("/facilities/" + room.getId(), "facility-inactive"))
                .contains("tidak dapat menerima reservasi baru").doesNotContain("Cek ketersediaan", "Masuk untuk reservasi");
    }

    @Test void catalogueCombinesFiltersAndExplainsInvalidIntervals() throws Exception {
        var other = facilityTypes.save(new com.github.kafeyangasli.prism.feature.facility.model.FacilityType("CLASS-UI", "Kelas", null));
        facilities.save(new Facility("OTHER-UI", "Kelas berbeda", other, "Gedung B", 60, null, AdministrativeStatus.ACTIVE));
        String date = LocalDate.now().plusDays(3).toString();
        String html = render("/facilities?type=Laboratorium&location=Informatika&capacity=20&startAt=" + date + "T09:00&endAt=" + date + "T10:00", "catalog-combined");
        assertThat(html).contains("Laboratorium Informatika").doesNotContain("Kelas berbeda");
        assertThat(render("/facilities?startAt=" + date + "T09:00", "catalog-invalid"))
                .contains("Isi waktu mulai dan selesai bersama").doesNotContain("0 fasilitas ditemukan");
        assertThat(render("/facilities?startAt=" + date + "T10:00&endAt=" + date + "T09:00", "catalog-reversed"))
                .contains("Waktu mulai dan selesai yang valid");
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void availabilityCarriesDateAndCancellationMatchesDeadline() throws Exception {
        String date = LocalDate.now().plusDays(2).toString();
        assertThat(render("/reservations/new?facilityId=" + room.getId() + "&date=" + date, "reservation-prefilled"))
                .contains("value=\"" + date + "\"", "name=\"startAt\"", "Pengajuan akan menunggu");
        assertThat(render("/reservations/" + reservation.getId(), "reservation-cancellation"))
                .contains("Konfirmasi pembatalan", "tidak dapat dipulihkan", "Belum divalidasi");
        reservation.setStatus(ReservationStatus.APPROVED);
        reservation.setStartAt(LocalDateTime.now().plusHours(12));
        reservation.setEndAt(LocalDateTime.now().plusHours(13));
        reservations.saveAndFlush(reservation);
        assertThat(render("/reservations/" + reservation.getId(), "reservation-cutoff"))
                .contains("Batas pembatalan telah terlewati").doesNotContain("/cancel");
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void reservationSearchUsesSharedContextAndPreservesSelectionAfterValidationErrors() throws Exception {
        var inactive = facilities.save(new Facility("INACTIVE-PICKER", "Fasilitas nonaktif", room.getFacilityType(),
                "Gedung X", 10, null, AdministrativeStatus.INACTIVE));
        String generic = render("/reservations/new", "reservation-search");
        assertThat(generic).contains("type=\"search\"", "data-facility-picker", "data-select-facility",
                "data-facility-context-template", "data-selected-facility-input", "Laboratorium Informatika")
                .doesNotContain("<select", "Fasilitas nonaktif", "data-facility-id=\"" + inactive.getId() + "\"");
        String fixed = render("/reservations/new?facilityId=" + room.getId(), "reservation-fixed-context");
        assertThat(fixed).contains("ui-facility-context", "data-selected-facility-id=\"" + room.getId() + "\"")
                .doesNotContain("data-facility-picker", "<select");
        String invalid = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/reservations")
                        .with(csrf()).param("facilityId", room.getId().toString()).param("facilityFixed", "false")
                        .param("date", LocalDate.now().plusDays(2).toString()).param("purpose", ""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(invalid).contains("data-selected-facility-context", "data-selected-facility-id=\"" + room.getId() + "\"",
                "value=\"Laboratorium Informatika\"").doesNotContain("<select");
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void userHistoriesExposeStatusSearchAndInclusiveDateFilters() throws Exception {
        var approved = new Reservation(reservation.getUser(), room, reservation.getStartAt().plusDays(3),
                reservation.getEndAt().plusDays(3), "Kegiatan lain", null, ReservationStatus.APPROVED, reservation.getExpiresAt());
        reservations.save(approved);
        reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), room,
                "Kursi", "Kursi rusak", null, com.github.kafeyangasli.prism.feature.report.model.ReportStatus.RESOLVED));
        String history = render("/reservations", "reservation-history-filters");
        assertThat(history).contains("data-record-from", "data-record-to", "data-history-reset",
                "data-record-status=\"PENDING\"", "data-record-status=\"APPROVED\"",
                "data-record-date=\"" + reservation.getStartAt().toLocalDate() + "\"");
        assertThat(render("/reports", "report-history-filters")).contains("data-record-from", "data-record-to",
                "data-record-status=\"RESOLVED\"", "Tanggal laporan dari", "data-date-range-error");
    }

    @ParameterizedTest @ValueSource(strings = {"PETUGAS", "ADMIN"})
    void managementHistoriesRenderProcessedRecordsAndFilters(String role) throws Exception {
        var approved = new Reservation(reservation.getUser(), room, reservation.getStartAt(), reservation.getEndAt(),
                "Kegiatan disetujui", null, ReservationStatus.APPROVED, reservation.getExpiresAt());
        reservations.save(approved);
        var rejected = new Reservation(reservation.getUser(), room, reservation.getStartAt().plusDays(1),
                reservation.getEndAt().plusDays(1), "Kegiatan ditolak", null, ReservationStatus.REJECTED, reservation.getExpiresAt());
        rejected.setReasonDetail("Jadwal tidak sesuai");
        reservations.save(rejected);
        reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), room,
                "Kursi", "Perbaikan selesai", null, com.github.kafeyangasli.prism.feature.report.model.ReportStatus.RESOLVED));
        var actor = com.github.kafeyangasli.prism.support.PrismTestUsers.user("history-" + role + "@example.test").roles(role);
        String html = mvc.perform(get("/staff/reservations").with(actor).header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Riwayat reservasi", "Kegiatan disetujui", "Jadwal tidak sesuai",
                "staff-reservation-history-records", "data-record-from", "data-record-status=\"APPROVED\"");
        assertThat(html.substring(html.indexOf("id=\"reservation-history-title\"")))
                .doesNotContain("data-record-filter=\"PENDING\"", "data-record-status=\"PENDING\"", "/approve", "/reject");
        String issues = mvc.perform(get("/staff/reports").with(actor)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(issues).contains("staff-report-history-records", "data-record-from", "data-record-status=\"RESOLVED\"");
        if (Boolean.getBoolean("prism.ui.snapshots")) {
            Files.createDirectories(Path.of("target/ui-preview"));
            Files.writeString(Path.of("target/ui-preview/staff-history-filters.html"), html);
            Files.writeString(Path.of("target/ui-preview/staff-issue-history-filters.html"), issues);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"PETUGAS", "ADMIN"})
    void staffReservationDetailsSupportCancellationWithRequiredReasonAndAudit(String role) throws Exception {
        reservation.setStatus(ReservationStatus.APPROVED);
        reservations.saveAndFlush(reservation);
        String actorEmail = "cancel-" + role + "@example.test";
        var actor = com.github.kafeyangasli.prism.support.PrismTestUsers.user(actorEmail).roles(role);
        String detailUrl = "/staff/reservations/" + reservation.getId();
        String cancelUrl = detailUrl + "/cancel";
        String history = mvc.perform(get("/staff/reservations").with(actor)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(history).contains("Lihat reservasi", "href=\"" + detailUrl + "\"")
                .doesNotContain("<td data-label=\"Catatan\"");
        String before = mvc.perform(get(detailUrl).with(actor)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(before).contains("Detail Reservasi", "Pengguna UI", "Diskusi kelompok mahasiswa",
                "data-active-nav=\"staff-reservations\"", "name=\"reason\"", "maxlength=\"2000\"",
                "staff-cancel-reservation", "name=\"_csrf\"", "action=\"" + cancelUrl + "\"");
        if (Boolean.getBoolean("prism.ui.snapshots")) {
            Files.createDirectories(Path.of("target/ui-preview"));
            Files.writeString(Path.of("target/ui-preview/staff-reservation-detail.html"), before);
        }
        mvc.perform(post(cancelUrl).with(actor).param("reason", "Valid reason"))
                .andExpect(status().isForbidden());
        mvc.perform(post(cancelUrl).with(actor).with(csrf()).param("reason", " "))
                .andExpect(redirectedUrl(detailUrl)).andExpect(flash().attributeExists("error"));
        mvc.perform(post(cancelUrl).with(actor).with(csrf()).param("reason", "x".repeat(2001)))
                .andExpect(redirectedUrl(detailUrl)).andExpect(flash().attributeExists("error"));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.APPROVED);
        mvc.perform(post(cancelUrl).with(actor).with(csrf()).param("reason", "  Fasilitas sedang diperbaiki  "))
                .andExpect(redirectedUrl(detailUrl)).andExpect(flash().attributeExists("success"));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getReasonDetail()).isEqualTo("Fasilitas sedang diperbaiki");
        assertThat(reservation.getCancelledBy().getId()).isEqualTo(users.findByEmailIgnoreCase(actorEmail).orElseThrow().getId());
        assertThat(reservation.getCancelledAt()).isNotNull();
        String after = mvc.perform(get(detailUrl).with(actor)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(after).contains("Dibatalkan oleh", "Waktu pembatalan", "Alasan pembatalan", "Fasilitas sedang diperbaiki")
                .doesNotContain("id=\"staff-cancel-reservation\"");
        mvc.perform(post(cancelUrl).with(actor).with(csrf()).param("reason", "Repeated request"))
                .andExpect(redirectedUrl(detailUrl)).andExpect(flash().attributeExists("error"));
        assertThat(reservation.getReasonDetail()).isEqualTo("Fasilitas sedang diperbaiki");
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void staffReservationDetailAndCancellationRejectOrdinaryUsers() throws Exception {
        mvc.perform(get("/staff/reservations/" + reservation.getId())).andExpect(status().isForbidden());
        mvc.perform(post("/staff/reservations/" + reservation.getId() + "/cancel").with(csrf()).param("reason", "Not allowed"))
                .andExpect(status().isForbidden());
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test @WithPrismUser(roles = "PETUGAS")
    void staffReservationDetailsDoNotOfferCancellationForOtherStatuses() throws Exception {
        for (ReservationStatus state : java.util.List.of(ReservationStatus.PENDING, ReservationStatus.REJECTED,
                ReservationStatus.CANCELLED, ReservationStatus.EXPIRED, ReservationStatus.COMPLETED)) {
            reservation.setStatus(state);
            reservations.saveAndFlush(reservation);
            String html = mvc.perform(get("/staff/reservations/" + reservation.getId())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).doesNotContain("id=\"staff-cancel-reservation\"", "data-open-dialog=\"staff-cancel-reservation\"");
            mvc.perform(post("/staff/reservations/" + reservation.getId() + "/cancel").with(csrf()).param("reason", "Cannot cancel"))
                    .andExpect(redirectedUrl("/staff/reservations/" + reservation.getId())).andExpect(flash().attributeExists("error"));
            assertThat(reservation.getStatus()).isEqualTo(state);
        }
        mvc.perform(get("/staff/reservations/999999")).andExpect(status().isNotFound());
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void modalSubmissionCarriesSuccessFeedbackToDetailOnce() throws Exception {
        String date = LocalDate.now().plusDays(4).toString();
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/reservations")
                .with(csrf()).header("HX-Request", "true")
                .param("facilityId", room.getId().toString()).param("date", date)
                .param("startAt", date + "T09:00").param("endAt", date + "T10:00")
                .param("purpose", "Pengujian alur konfirmasi"))
                .andExpect(status().isNoContent()).andReturn();
        String url = result.getResponse().getHeader("HX-Redirect");
        assertThat(url).startsWith("/reservations/");
        var session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession();
        String detail = mvc.perform(get(url).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(detail).contains("Reservasi berhasil diajukan dan berstatus Menunggu.", "Pengujian alur konfirmasi");
        assertThat(mvc.perform(get(url).session(session)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("Reservasi berhasil diajukan dan berstatus Menunggu.");
    }

    @Test @WithPrismUser(username="staff-ui@example.test", roles="PETUGAS")
    void reportDetailCarriesContextIntoBlockageAndKeepsStaffNavigation() throws Exception {
        var report = reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), room,
                "AC", "Pendingin tidak berfungsi", null, com.github.kafeyangasli.prism.feature.report.model.ReportStatus.NEW));
        var repair = blockageTypes.save(new com.github.kafeyangasli.prism.feature.blockage.model.BlockageType("REPAIR", "Perbaikan", null));
        assertThat(render("/staff/reports/" + report.getId(), "report-staff"))
                .contains("data-active-nav=\"staff-reports\"", "/staff/blockages?reportId=" + report.getId(), "Buat blokir perbaikan");
        String html = render("/staff/blockages?reportId=" + report.getId(), "blockage-from-report");
        assertThat(html).contains("value=\"" + report.getId() + "\"", "Perbaikan", "Alasan publik");
        var result = mvc.perform(get("/staff/blockages").param("reportId", report.getId().toString())).andReturn();
        var form = (com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageRequest) result.getModelAndView().getModel().get("blockageForm");
        assertThat(form.getFacilityId()).isEqualTo(room.getId());
        assertThat(form.getBlockageTypeId()).isEqualTo(repair.getId());
        assertThat(form.getReportId()).isEqualTo(report.getId());
        mvc.perform(get("/admin/blockage-types")).andExpect(status().isForbidden());
    }

    @Test @WithPrismUser(roles="ADMIN")
    void blockageTypeManagementUsesHtmlAndExistingService() throws Exception {
        assertThat(render("/admin/blockage-types", "blockage-types-empty"))
                .contains("Belum ada jenis blokir", "Tambah jenis blokir").doesNotContain("href=\"/api/admin/blockage-types\"");
        mvc.perform(post("/admin/blockage-types").with(csrf()).param("code", "UX-MAINT").param("name", "Pemeliharaan UI"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/blockage-types"));
        var type = blockageTypes.findByCodeIgnoreCase("UX-MAINT").orElseThrow();
        assertThat(render("/admin/blockage-types", "blockage-types")).contains("Pemeliharaan UI", "Ubah jenis blokir", "/deactivate");
        mvc.perform(post("/admin/blockage-types/" + type.getId() + "/update").with(csrf()).param("name", "Pemeliharaan diperbarui").param("description", "Catatan"))
                .andExpect(status().is3xxRedirection());
        assertThat(type.getName()).isEqualTo("Pemeliharaan diperbarui");
        mvc.perform(post("/admin/blockage-types/" + type.getId() + "/deactivate").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(type.isActive()).isFalse();
        mvc.perform(post("/admin/blockage-types").param("code", "CSRF").param("name", "Tidak tersimpan"))
                .andExpect(status().isForbidden());
        String invalid = mvc.perform(post("/admin/blockage-types").with(csrf()).param("code", "UX-MAINT").param("name", "Duplikat"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(invalid).contains("Kode jenis blokir sudah digunakan", "value=\"Duplikat\"");
    }

    @Test @WithPrismUser(roles="ADMIN")
    void administrativeNavigationKeepsFeedbackTargetAndHandlesManyLongRows() throws Exception {
        for (int index = 0; index < 24; index++) {
            users.save(new User("Pengguna dengan nama lengkap panjang untuk pemeriksaan tabel administrasi " + index,
                    "long-user-" + index + "@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        }
        String html = render("/admin/users", "users-many");
        assertThat(html).contains("id=\"management-feedback\"", "long-user-23@example.test")
                .doesNotContain("hx-swap-oob=");
        assertThat(html.split("data-label=\"Nama\"", -1).length - 1).isEqualTo(users.count());
        var navigation = mvc.perform(get("/admin/facilities").header("HX-Request", "true").header("HX-Target", "dashboard-content"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(navigation).contains("id=\"management-feedback\"").doesNotContain("hx-swap-oob=");
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void reservationFormsKeepBindingsAndConditionalActions() throws Exception {
        assertThat(render("/reservations/new", "reservation-form"))
            .contains("multipart/form-data", "name=\"facilityId\"", "name=\"date\"", "name=\"purpose\"", "name=\"proposal\"", "name=\"_csrf\"", "id=\"reservation-availability\"",
                "id=\"dashboard-sidebar\"", "id=\"dashboard-content\"", "Laporan Saya", "href=\"/account\"", "Pengaturan Akun")
            .doesNotContain("type=\"datetime-local\"");
        String day = LocalDate.now().plusDays(2).toString();
        String availability = mvc.perform(get("/reservations/availability")
                .param("facilityId", room.getId().toString()).param("date", day)
                .header("HX-Request", "true"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(availability).contains("name=\"startAt\"", "07:00", "19:30", "Tersedia");

        String modal = mvc.perform(get("/reservations/new")
                .param("facilityId", room.getId().toString()).header("HX-Request", "true"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(modal).contains("id=\"reservation-dialog\"", "open=\"open\"", "Laboratorium Informatika", "name=\"facilityId\"");
        render("/reservations", "history");
        String detail = render("/reservations/" + reservation.getId(), "reservation-detail");
        assertThat(detail).contains("/cancel", "/proposal", "status-pending").doesNotContain("href=\"/admin/users\"");
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservations.saveAndFlush(reservation);
        assertThat(render("/reservations/" + reservation.getId(), "reservation-completed")).doesNotContain("/cancel");
        mvc.perform(post("/reservations/" + reservation.getId() + "/cancel")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void penggunaHomeRendersReservationDashboard() throws Exception {
        LocalDateTime approvedStart = LocalDateTime.now().plusDays(3).withHour(13).withMinute(0);
        reservations.save(new Reservation(reservation.getUser(), room, approvedStart, approvedStart.plusHours(1),
                "Kegiatan disetujui", null, ReservationStatus.APPROVED, approvedStart.minusDays(1)));

        String html = mvc.perform(get("/"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Ringkasan reservasi", "Menunggu persetujuan", "Aktif &amp; akan datang",
                "Laboratorium Informatika", "id=\"dashboard-content\"")
            .doesNotContain("Platform for Reservation and Issue Management");
    }

    @Test @WithPrismUser(roles="PETUGAS")
    void staffActionsRenderWithoutAdminLinks() throws Exception {
        assertThat(render("/staff/dashboard?sort=start", "staff")).contains("/approve", "/reject", "name=\"reasonDetail\"", "Penolakan manual", "Batas pending", "name=\"_csrf\"", "Operasional", "Blokir", "Pengaturan Akun")
            .doesNotContain("href=\"/admin/users\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PETUGAS", "ADMIN"})
    void dashboardNavigationRendersOpenReportsForStaffAndAdmin(String role) throws Exception {
        var equipmentType = facilityTypes.save(new com.github.kafeyangasli.prism.feature.facility.model.FacilityType(
                "ALAT-UI", "Alat", null));
        var equipment = new Facility("SPEAKER-UI", "Speaker UI", equipmentType,
                "Gedung Informatika", 1, null, AdministrativeStatus.ACTIVE);
        equipment.setTotalStock(10);
        facilities.save(equipment);
        var equipmentReservation = new Reservation(reservation.getUser(), equipment,
                reservation.getStartAt(), reservation.getEndAt(), "Kegiatan audio", null,
                ReservationStatus.PENDING, reservation.getExpiresAt());
        equipmentReservation.setQuantity(3);
        reservations.save(equipmentReservation);
        reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), room,
                "Lampu rusak", "Lampu tidak menyala", null,
                com.github.kafeyangasli.prism.feature.report.model.ReportStatus.NEW));
        reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), equipment,
                "Kabel audio", "Kabel perlu diperiksa", null,
                com.github.kafeyangasli.prism.feature.report.model.ReportStatus.IN_PROGRESS));
        var account = com.github.kafeyangasli.prism.support.PrismTestUsers
                .user(role.toLowerCase(java.util.Locale.ROOT) + "-dashboard@example.test").roles(role);

        // Match the sidebar flow: open another page, then request the dashboard via HTMX.
        mvc.perform(get("/staff/reports").with(account)).andExpect(status().isOk());
        for (String sort : java.util.List.of("created", "start")) {
            for (boolean navigation : java.util.List.of(false, true)) {
                var request = get("/staff/dashboard").param("sort", sort).with(account);
                if (navigation) request.header("HX-Request", "true").header("HX-Target", "dashboard-content");
                String html = mvc.perform(request).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(html).contains("id=\"dashboard-content\"", "data-active-nav=\"dashboard\"",
                        "Lampu rusak", "Kabel audio", "Baru", "Sedang Ditangani", "Speaker UI · 3 unit");
                assertThat(html.strip()).endsWith("</html>");
            }
        }
    }

    @Test @WithPrismUser(roles="PETUGAS")
    void queueSortLinksRemainOnTheirOwnPage() throws Exception {
        for (String sort : java.util.List.of("created", "start")) {
            assertThat(render("/staff/dashboard?sort=" + sort, "dashboard-sort-" + sort))
                    .contains("href=\"/staff/dashboard?sort=created\"", "href=\"/staff/dashboard?sort=start\"",
                            "data-active-nav=\"dashboard\"")
                    .doesNotContain("href=\"/staff/reservations?sort=");
            assertThat(render("/staff/reservations?sort=" + sort, "reservations-sort-" + sort))
                    .contains("href=\"/staff/reservations?sort=created\"", "href=\"/staff/reservations?sort=start\"",
                            "data-active-nav=\"staff-reservations\"")
                    .doesNotContain("href=\"/staff/dashboard?sort=");
        }
    }

    @Test @WithPrismUser(username="staff-ui@example.test", roles="PETUGAS")
    void processedReportsRemainInHistoryAfterResolution() throws Exception {
        var report = reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), room,
                "History-resolved-UI", "Masalah untuk diperbaiki", null, com.github.kafeyangasli.prism.feature.report.model.ReportStatus.NEW));
        var rejected = reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), room,
                "History-rejected-UI", "Laporan ditolak", null, com.github.kafeyangasli.prism.feature.report.model.ReportStatus.REJECTED));
        mvc.perform(post("/staff/reports/" + report.getId() + "/status").with(csrf()).param("status", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection());
        assertThat(render("/staff/reports", "report-queue"))
                .contains("data-action-dialog", "name=\"resolutionNote\"", "name=\"_csrf\"", "Selesaikan laporan");
        render("/staff/reports/" + report.getId(), "report-in-progress");
        mvc.perform(post("/staff/reports/" + report.getId() + "/status").with(csrf())
                .param("status", "RESOLVED").param("resolutionNote", "Masalah sudah diperbaiki"))
                .andExpect(redirectedUrl("/staff/reports"));
        String html = render("/staff/reports", "reports-with-history");
        assertThat(html).contains("Tidak ada laporan terbuka", "Riwayat laporan");
        String history = html.substring(html.indexOf("id=\"report-history-title\""));
        assertThat(history).contains("History-resolved-UI", "History-rejected-UI",
                "/staff/reports/" + report.getId(), "/staff/reports/" + rejected.getId())
                .doesNotContain("/status", "Mulai penanganan");
    }

    @Test @WithPrismUser(username="staff-ui@example.test", roles="PETUGAS")
    void conflictingApprovalRequiresModalConfirmationAndCascadesAfterConfirmation() throws Exception {
        // This test exercises cascade approval; submitted proposals now need separate validation.
        reservation.setProposalPath(null);
        reservations.saveAndFlush(reservation);
        Reservation conflict = reservations.save(new Reservation(reservation.getUser(), room,
                reservation.getStartAt().plusMinutes(30), reservation.getEndAt().plusHours(1),
                "Kegiatan yang bertumpang tindih", null, ReservationStatus.PENDING,
                reservation.getExpiresAt()));

        String html = mvc.perform(get("/staff/reservations"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains(
                "id=\"approve-reservation-" + reservation.getId() + "\"",
                "id=\"reject-reservation-" + reservation.getId() + "\"",
                "Ada konflik jadwal", "akan otomatis ditolak",
                "name=\"confirmCascade\" value=\"true\"",
                "name=\"reasonDetail\"", "Alasan penolakan wajib diisi");

        mvc.perform(post("/staff/reservations/" + reservation.getId() + "/approve")
                .param("confirmCascade", "true").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/staff/reservations"));

        assertThat(reservations.findById(reservation.getId()).orElseThrow().getStatus())
            .isEqualTo(ReservationStatus.APPROVED);
        assertThat(reservations.findById(conflict.getId()).orElseThrow().getStatus())
            .isEqualTo(ReservationStatus.REJECTED);
    }

    @Test @WithPrismUser(roles="PETUGAS")
    void staffOperationalNavigationUsesDedicatedPagesAndIgnoresBlankFlashMessages() throws Exception {
        String reservationsPage = mvc.perform(get("/staff/reservations")
                .flashAttr("success", "   ")
                .flashAttr("errorMessage", ""))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(reservationsPage)
            .contains("data-active-nav=\"staff-reservations\"", "href=\"/staff/reservations\"",
                "href=\"/staff/reports\"", ">Dashboard</a>", "Reservasi menunggu")
            .doesNotContain("href=\"/staff/dashboard#pending-reservations\"",
                "href=\"/staff/dashboard#open-reports\"",
                "<div class=\"ui-alert ui-alert-success\" role=\"status\">",
                "<div class=\"ui-alert ui-alert-error\" role=\"alert\">");

        String reportsPage = mvc.perform(get("/staff/reports"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(reportsPage).contains("data-active-nav=\"staff-reports\"", "Laporan terbuka");

        mvc.perform(post("/staff/reservations/999/reject").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/staff/reservations"))
            .andExpect(flash().attribute("error", "Alasan penolakan wajib diisi"));
    }

    @Test @WithPrismUser(roles="ADMIN")
    void adminPagesRenderWithFormsAndExports() throws Exception {
        users.save(new User("Pendaftar", "pending@example.test", "hash", Role.PENGGUNA, AccountStatus.PENDING));
        users.save(new User("Pengguna Nonaktif", "nonaktif@example.test", "hash", Role.PENGGUNA, AccountStatus.INACTIVE));
        assertThat(render("/admin/users", "users")).contains("/verify", "/reject", "/deactivate", "name=\"role\"", "name=\"filterStatus\"", "name=\"sort\"", "name=\"_csrf\"");
        assertThat(render("/admin/users?filterStatus=INACTIVE&sort=name&direction=asc", "users-filtered"))
            .contains("nonaktif@example.test", "Tidak Aktif").doesNotContain("ui@example.test");
        assertThat(render("/admin/facilities", "admin-facilities")).contains("name=\"code\"", "name=\"capacity\"", "/deactivate");
        assertThat(render("/admin/recap?startDate=2026-09-01&endDate=2026-09-30", "recap"))
            .contains("name=\"format\"", "value=\"csv\"", "value=\"xlsx\"", "value=\"pdf\"");
        assertThat(render("/staff/dashboard", "admin-dashboard"))
                .contains("href=\"/admin/users\"", "href=\"/admin/recap\"", "href=\"/admin/settings\"", "Administrasi")
                .doesNotContain("href=\"/admin/facility-types\"", "href=\"/admin/blockage-types\"");
        assertThat(render("/admin/settings", "settings"))
                .contains("href=\"/admin/facility-types\"", "href=\"/admin/blockage-types\"", "Jenis Fasilitas", "Jenis Blokir");
        assertThat(render("/admin/facility-types", "facility-types-settings"))
                .contains("data-active-nav=\"admin-settings\"", "← Pengaturan");
        assertThat(render("/admin/blockage-types", "blockage-types-settings"))
                .contains("data-active-nav=\"admin-settings\"", "← Pengaturan");
    }

    @Test @WithPrismUser(roles="ADMIN")
    void adminUserNavigationReturnsDashboardContentWhileFiltersReturnOnlyResults() throws Exception {
        String navigation = mvc.perform(get("/admin/users")
                .header("HX-Request", "true")
                .header("HX-Target", "dashboard-content"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(navigation).contains("id=\"dashboard-content\"", "id=\"user-results\"");

        String filtering = mvc.perform(get("/admin/users")
                .header("HX-Request", "true")
                .header("HX-Target", "user-results"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(filtering).contains("id=\"user-results\"").doesNotContain("id=\"dashboard-content\"");
    }

    @Test @WithPrismUser(username="admin-ui@example.test", roles="ADMIN")
    void adminHtmxMutationsReturnFragmentsAndKeepValidationInDialogs() throws Exception {
        User admin = users.findByEmailIgnoreCase("admin-ui@example.test").orElseThrow();
        User inactive = users.save(new User("Akun Tidak Aktif", "inactive-ui@example.test", "hash", Role.PENGGUNA, AccountStatus.INACTIVE));

        String activation = mvc.perform(post("/admin/users/" + inactive.getId() + "/activate")
                .header("HX-Request", "true").with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(activation).contains("id=\"user-results\"", "Akun berhasil diaktifkan", "Nonaktifkan");
        assertThat(users.findById(inactive.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        String invalidUser = mvc.perform(post("/admin/users")
                .header("HX-Request", "true").with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(invalidUser).contains("id=\"user-dialog\"", "Nama lengkap wajib diisi", "open=\"open\"");

        String createdUser = mvc.perform(post("/admin/users")
                .header("HX-Request", "true").with(csrf())
                .param("name", "Petugas HTMX").param("email", "petugas-htmx@example.test")
                .param("password", "rahasia").param("role", "PETUGAS"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(createdUser).contains("Akun baru berhasil dibuat", "hx-swap-oob=\"outerHTML\"", "Petugas HTMX");

        String invalidFacility = mvc.perform(post("/admin/facilities")
                .header("HX-Request", "true").with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(invalidFacility).contains("id=\"facility-dialog\"", "Kode fasilitas wajib diisi", "open=\"open\"");

        String createdFacility = mvc.perform(post("/admin/facilities")
                .header("HX-Request", "true").with(csrf())
                .param("code", "AUD-HTMX").param("name", "Auditorium HTMX")
                .param("facilityTypeId", room.getFacilityType().getId().toString()).param("location", "Gedung B").param("capacity", "100"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(createdFacility).contains("Fasilitas berhasil ditambahkan", "hx-swap-oob=\"outerHTML\"", "Auditorium HTMX");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void emptyAndHtmxResultsRender() throws Exception {
        reservations.deleteAll();
        assertThat(render("/reservations", "history-empty")).contains("Belum ada reservasi");
        String html = mvc.perform(get("/facilities?type=nonexistent").header("HX-Request", "true"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("id=\"facility-results\"", "Tidak ada fasilitas ditemukan");
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void redesignedReportPageAndModalKeepTheSameSubmissionContract() throws Exception {
        String page = render("/reports/new", "report-form");
        assertThat(page).contains("action=\"/reports\"", "multipart/form-data", "name=\"facilityId\"",
                "name=\"category\"", "name=\"description\"", "name=\"photo\"", "name=\"_csrf\"",
                "data-character-count", "Jelaskan kondisi fasilitas").doesNotContain("<dialog");
        String modal = mvc.perform(get("/reports/new").header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(modal).contains("id=\"report-dialog\"", "hx-post=\"/reports\"", "name=\"facilityId\"",
                "name=\"category\"", "name=\"description\"", "name=\"photo\"", "name=\"_csrf\"");
        render("/reports", "my-reports-empty");
        var report = reports.save(new com.github.kafeyangasli.prism.feature.report.model.Report(reservation.getUser(), room,
                "AC", "Pendingin tidak berfungsi", null, com.github.kafeyangasli.prism.feature.report.model.ReportStatus.NEW));
        assertThat(render("/reports", "my-reports")).contains("data-record-status=\"NEW\"", "data-record-filter=\"IN_PROGRESS\"");
        render("/reports/" + report.getId(), "my-report-detail");
    }

    @Test @WithPrismUser(username="ui@example.test", roles="PENGGUNA")
    void accountViewsKeepProfilePasswordAndDeactivationForms() throws Exception {
        assertThat(render("/account", "account")).contains("/account/profile", "/account/avatar", "name=\"name\"", "name=\"image\"", "name=\"_csrf\"");
        assertThat(render("/account/password", "account-password")).contains("name=\"currentPassword\"", "name=\"newPassword\"", "name=\"confirmPassword\"", "data-password-toggle");
        assertThat(render("/account/delete", "account-delete")).contains("name=\"confirmed\"", "name=\"currentPassword\"", "name=\"_csrf\"");
    }
}
