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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    Facility room;
    Reservation reservation;

    @BeforeEach void seed() {
        User user = users.save(new User("Pengguna UI", "ui@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        room = facilities.save(new Facility("LAB-UI", "Laboratorium Informatika", "Laboratorium", "Gedung Informatika, Lantai 2", 30, "Ruang kegiatan akademik dan diskusi mahasiswa.", AdministrativeStatus.ACTIVE));
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0);
        reservation = reservations.save(new Reservation(user, room, start, start.plusHours(2), "Diskusi kelompok mahasiswa", "proposal.pdf", ReservationStatus.PENDING, start.minusHours(12)));
    }

    String render(String url, String snapshot) throws Exception {
        String html = mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("/css/app.css", "id=\"main-content\"")
            .doesNotContain("th:field=", "th:replace=", "sec:authorize=");
        assertThat(html.split("<main", -1)).hasSize(2);
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
            .doesNotContain(">Beranda</a>")
            .doesNotContain("href=\"/admin/users\"", "href=\"/staff/dashboard\"");
        render("/facilities/" + room.getId(), "facility");
        assertThat(render("/login?error", "login"))
            .contains("name=\"username\"", "name=\"password\"", "name=\"_csrf\"", "href=\"/\"", "/images/prism-dark.svg", "/images/prism-light.svg")
            .doesNotContain("data-navigation");
        assertThat(render("/auth/register", "register"))
            .contains("name=\"email\"", "name=\"_csrf\"", "href=\"/\"", "/images/prism-dark.svg", "/images/prism-light.svg")
            .doesNotContain("data-navigation");
        mvc.perform(get("/images/prism-light.svg")).andExpect(status().isOk());
        mvc.perform(get("/images/prism-dark.svg")).andExpect(status().isOk());
    }

    @Test @WithMockUser(username="ui@example.test", roles="PENGGUNA")
    void reservationFormsKeepBindingsAndConditionalActions() throws Exception {
        assertThat(render("/reservations/new", "reservation-form")).contains("multipart/form-data", "name=\"facilityId\"", "name=\"startAt\"", "name=\"endAt\"", "name=\"purpose\"", "name=\"proposal\"", "name=\"_csrf\"");
        render("/reservations", "history");
        String detail = render("/reservations/" + reservation.getId(), "reservation-detail");
        assertThat(detail).contains("/cancel", "/proposal", "status-pending").doesNotContain("href=\"/admin/users\"");
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservations.saveAndFlush(reservation);
        assertThat(render("/reservations/" + reservation.getId(), "reservation-completed")).doesNotContain("/cancel");
        mvc.perform(post("/reservations/" + reservation.getId() + "/cancel")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles="PETUGAS")
    void staffActionsRenderWithoutAdminLinks() throws Exception {
        assertThat(render("/staff/dashboard?sort=start", "staff")).contains("/approve", "/reject", "name=\"reasonDetail\"", "Batas pending", "name=\"_csrf\"")
            .doesNotContain("href=\"/admin/users\"");
    }

    @Test @WithMockUser(roles="ADMIN")
    void adminPagesRenderWithFormsAndExports() throws Exception {
        users.save(new User("Pendaftar", "pending@example.test", "hash", Role.PENGGUNA, AccountStatus.PENDING));
        users.save(new User("Pengguna Nonaktif", "nonaktif@example.test", "hash", Role.PENGGUNA, AccountStatus.INACTIVE));
        assertThat(render("/admin/users", "users")).contains("/verify", "/reject", "/deactivate", "name=\"role\"", "name=\"filterStatus\"", "name=\"sort\"", "name=\"_csrf\"");
        assertThat(render("/admin/users?filterStatus=INACTIVE&sort=name&direction=asc", "users-filtered"))
            .contains("nonaktif@example.test", "Tidak Aktif").doesNotContain("ui@example.test");
        assertThat(render("/admin/facilities", "admin-facilities")).contains("name=\"code\"", "name=\"capacity\"", "/deactivate");
        assertThat(render("/admin/recap?startDate=2026-09-01&endDate=2026-09-30", "recap"))
            .contains("name=\"format\"", "value=\"csv\"", "value=\"xlsx\"", "value=\"pdf\"");
        assertThat(render("/staff/dashboard", "admin-dashboard")).contains("href=\"/admin/users\"", "href=\"/admin/recap\"");
    }

    @Test @WithMockUser(username="admin-ui@example.test", roles="ADMIN")
    void adminHtmxMutationsReturnFragmentsAndKeepValidationInDialogs() throws Exception {
        User admin = users.save(new User("Admin UI", "admin-ui@example.test", "hash", Role.ADMIN, AccountStatus.ACTIVE));
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
                .param("type", "Auditorium").param("location", "Gedung B").param("capacity", "100"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(createdFacility).contains("Fasilitas berhasil ditambahkan", "hx-swap-oob=\"outerHTML\"", "Auditorium HTMX");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test @WithMockUser(username="ui@example.test", roles="PENGGUNA")
    void emptyAndHtmxResultsRender() throws Exception {
        reservations.deleteAll();
        assertThat(render("/reservations", "history-empty")).contains("Belum ada reservasi");
        String html = mvc.perform(get("/facilities?type=nonexistent").header("HX-Request", "true"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("id=\"facility-results\"", "Tidak ada fasilitas ditemukan");
    }
}
