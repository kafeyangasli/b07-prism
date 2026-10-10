package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.dto.*;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.feature.user.service.AccountSettingsService;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.report.model.*;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.github.kafeyangasli.prism.support.WithPrismUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static com.github.kafeyangasli.prism.support.PrismTestUsers.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-settings;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(AccountSettingsIntegrationTest.FixedClockConfiguration.class)
@Transactional
class AccountSettingsIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 10, 0);
    static final String EMAIL = "account@example.test";
    static final String OLD_PASSWORD = "old-password";
    static final String NEW_PASSWORD = "new-password";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository facilityTypes;
    @Autowired ReservationRepository reservations;
    @Autowired ReportRepository reports;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountSettingsService service;
    User owner;

    @BeforeEach void seed() {
        owner = users.saveAndFlush(new User("Original", EMAIL, encoder.encode(OLD_PASSWORD),
                Role.PENGGUNA, AccountStatus.ACTIVE));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void allRolesCanViewAndUpdateTheirOwnName(Role role) throws Exception {
        owner.setRole(role);
        users.saveAndFlush(owner);
        String html = mvc.perform(get("/account").with(user(EMAIL).roles(role.name())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Pengaturan Akun", EMAIL, role.name(), "Aktif", "name=\"_csrf\"")
                .doesNotContain(owner.getPasswordHash(), "passwordHash", "th:field=");
        if (role == Role.PENGGUNA) assertThat(html).contains("Zona Bahaya", "href=\"/account/delete\"");
        else assertThat(html).doesNotContain("Zona Bahaya", "href=\"/account/delete\"");
        mvc.perform(post("/account/profile").with(user(EMAIL).roles(role.name())).with(csrf())
                        .param("name", "  Updated  "))
                .andExpect(redirectedUrl("/account"));
        assertThat(users.findById(owner.getId()).orElseThrow().getName()).isEqualTo("Updated");
        mvc.perform(get("/account").with(user(EMAIL).roles(role.name())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Updated\"")));
    }

    @ParameterizedTest @ValueSource(strings = {"", "   ", "\u2003"})
    void blankNamesRejected(String name) throws Exception {
        mvc.perform(post("/account/profile").with(user(EMAIL).roles("PENGGUNA")).with(csrf()).param("name", name))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("profileForm", "name"));
        assertThat(owner.getName()).isEqualTo("Original");
    }

    @Test void longNameRejectedAndSafeValueRetained() throws Exception {
        String name = "x".repeat(121);
        mvc.perform(post("/account/profile").with(user(EMAIL).roles("PENGGUNA")).with(csrf()).param("name", name))
                .andExpect(model().attributeHasFieldErrors("profileForm", "name"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(name)));
        assertThat(owner.getName()).isEqualTo("Original");
    }

    @Test void manipulatedIdsAndProtectedFieldsIgnored() throws Exception {
        User other = users.saveAndFlush(new User("Other", "other@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        mvc.perform(post("/account/profile").with(user(EMAIL).roles("PENGGUNA")).with(csrf())
                        .param("id", other.getId().toString()).param("userId", other.getId().toString())
                        .param("email", other.getEmail()).param("role", "ADMIN").param("accountStatus", "INACTIVE")
                        .param("passwordHash", "injected").param("name", "Changed"))
                .andExpect(redirectedUrl("/account"));
        assertThat(owner.getName()).isEqualTo("Changed");
        assertThat(owner.getEmail()).isEqualTo(EMAIL);
        assertThat(owner.getRole()).isEqualTo(Role.PENGGUNA);
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(encoder.matches(OLD_PASSWORD, owner.getPasswordHash())).isTrue();
        assertThat(other.getName()).isEqualTo("Other");
    }

    @ParameterizedTest @EnumSource(Role.class)
    void passwordChangeEncodesAndEndsCurrentSessionForEveryRole(Role role) throws Exception {
        owner.setRole(role);
        users.saveAndFlush(owner);
        MockHttpSession session = login(OLD_PASSWORD);
        mvc.perform(post("/account/password").session(session).with(csrf())
                        .param("currentPassword", OLD_PASSWORD).param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD).param("id", "9999"))
                .andExpect(redirectedUrl("/login?passwordChanged")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        assertThat(owner.getPasswordHash()).isNotEqualTo(NEW_PASSWORD);
        assertThat(encoder.matches(NEW_PASSWORD, owner.getPasswordHash())).isTrue();
        mvc.perform(formLogin().user(EMAIL).password(OLD_PASSWORD)).andExpect(unauthenticated());
        mvc.perform(formLogin().user(EMAIL).password(NEW_PASSWORD)).andExpect(authenticated().withUsername(EMAIL));
        mvc.perform(get("/login?passwordChanged")).andExpect(content().string(org.hamcrest.Matchers.containsString("Kata sandi berhasil diubah")));
    }

    @Test void wrongCurrentPasswordDoesNotChangeHashOrEndSession() throws Exception {
        passwordFailure("wrong-password", NEW_PASSWORD, NEW_PASSWORD, "Kata sandi saat ini tidak sesuai.");
    }
    @Test void mismatchedConfirmationDoesNotChangeHash() throws Exception {
        passwordFailure(OLD_PASSWORD, NEW_PASSWORD, "mismatch-password", "Konfirmasi kata sandi baru tidak cocok.");
    }
    @Test void identicalPasswordRejected() throws Exception {
        passwordFailure(OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD, "Kata sandi baru harus berbeda");
    }
    @Test void shortPasswordRejected() throws Exception {
        passwordFailure(OLD_PASSWORD, "short", "short", "8–72 karakter");
    }
    @Test void oversizedUtf8PasswordRejected() throws Exception {
        passwordFailure(OLD_PASSWORD, "é".repeat(37), "é".repeat(37), "maksimal 72 byte UTF-8");
    }
    @Test void missingPasswordInputsRejectedWithoutSecretsInModel() throws Exception {
        mvc.perform(post("/account/password").with(user(EMAIL).roles("PENGGUNA")).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertThat(encoder.matches(OLD_PASSWORD, owner.getPasswordHash())).isTrue();
    }

    @Test void passwordFallbackLeavesOtherSessionActiveButRechecksLatestHash() throws Exception {
        MockHttpSession first = login(OLD_PASSWORD);
        MockHttpSession second = login(OLD_PASSWORD);
        mvc.perform(post("/account/password").session(first).with(csrf())
                .param("currentPassword", OLD_PASSWORD).param("newPassword", NEW_PASSWORD)
                .param("confirmPassword", NEW_PASSWORD)).andExpect(redirectedUrl("/login?passwordChanged"));
        mvc.perform(get("/account").session(second)).andExpect(status().isOk());
        String hash = owner.getPasswordHash();
        mvc.perform(post("/account/password").session(second).with(csrf())
                .param("currentPassword", OLD_PASSWORD).param("newPassword", "another-password")
                .param("confirmPassword", "another-password"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertThat(owner.getPasswordHash()).isEqualTo(hash);
        assertThat(second.isInvalid()).isFalse();
    }

    private void passwordFailure(String current, String next, String confirmation, String message) throws Exception {
        String hash = owner.getPasswordHash();
        MockHttpSession session = login(OLD_PASSWORD);
        var result = mvc.perform(post("/account/password").session(session).with(csrf())
                        .param("currentPassword", current).param("newPassword", next).param("confirmPassword", confirmation))
                .andExpect(status().isOk()).andExpect(view().name("account/password")).andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(message)
                .doesNotContain("value=\"" + current + "\"", "value=\"" + next + "\"");
        assertThat(result.getModelAndView().getModel()).containsOnlyKeys("account", "errorMessage",
                "org.springframework.validation.BindingResult.account");
        assertThat(owner.getPasswordHash()).isEqualTo(hash);
        assertThat(session.isInvalid()).isFalse();
    }

    @Test void deletionPreservesHistoryAndAuditAndEndsSessionAndPreventsLogin() throws Exception {
        Facility room = room();
        Reservation history = reservations.saveAndFlush(new Reservation(owner, room, NOW.minusDays(2), NOW.minusDays(1),
                "History", null, ReservationStatus.COMPLETED, NOW.minusDays(3)));
        history.setProcessedBy(owner);
        reservations.saveAndFlush(history);
        Report report = reports.saveAndFlush(new Report(owner, room, "AC", "Broken", null, ReportStatus.RESOLVED));
        report.setHandledBy(owner);
        reports.saveAndFlush(report);
        MockHttpSession session = login(OLD_PASSWORD);
        mvc.perform(post("/account/delete").session(session).with(csrf())
                        .param("confirmed", "true").param("currentPassword", OLD_PASSWORD))
                .andExpect(redirectedUrl("/login?accountDeactivated")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        assertThat(users.findById(owner.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(reservations.findById(history.getId()).orElseThrow().getUser().getId()).isEqualTo(owner.getId());
        assertThat(reservations.findById(history.getId()).orElseThrow().getProcessedBy().getId()).isEqualTo(owner.getId());
        assertThat(reports.findById(report.getId()).orElseThrow().getUser().getId()).isEqualTo(owner.getId());
        assertThat(reports.findById(report.getId()).orElseThrow().getHandledBy().getId()).isEqualTo(owner.getId());
        mvc.perform(formLogin().user(EMAIL).password(OLD_PASSWORD)).andExpect(unauthenticated());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void futureAndOngoingApprovedReservationsBlockDeletion(boolean ongoing) throws Exception {
        reservations.saveAndFlush(new Reservation(owner, room(), ongoing ? NOW.minusHours(1) : NOW.plusHours(1),
                NOW.plusHours(2), "Active", null, ReservationStatus.APPROVED, NOW.plusHours(1)));
        mvc.perform(post("/account/delete").with(user(EMAIL).roles("PENGGUNA")).with(csrf())
                        .param("confirmed", "true").param("currentPassword", OLD_PASSWORD))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Selesaikan reservasi disetujui")));
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reservations.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst().getStatus()).isEqualTo(ReservationStatus.APPROVED);
    }

    @ParameterizedTest @EnumSource(value = ReservationStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void otherStatusesDoNotBlockDeletion(ReservationStatus status) throws Exception {
        reservations.saveAndFlush(new Reservation(owner, room(), NOW.plusHours(1), NOW.plusHours(2),
                "Other status", null, status, NOW.plusHours(1)));
        deleteSuccessfully();
        assertThat(reservations.findByUserIdOrderByCreatedAtDesc(owner.getId()).getFirst().getStatus()).isEqualTo(status);
    }

    @Test void approvedReservationEndingExactlyNowDoesNotBlockDeletion() throws Exception {
        reservations.saveAndFlush(new Reservation(owner, room(), NOW.minusHours(1), NOW,
                "Ended", null, ReservationStatus.APPROVED, NOW.minusDays(1)));
        deleteSuccessfully();
    }

    @Test void invalidPasswordBlocksDeletionWithoutEndingSession() throws Exception {
        MockHttpSession session = login(OLD_PASSWORD);
        mvc.perform(post("/account/delete").session(session).with(csrf())
                        .param("confirmed", "true").param("currentPassword", "wrong-password"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(session.isInvalid()).isFalse();
    }
    @Test void explicitConfirmationRequired() throws Exception {
        mvc.perform(post("/account/delete").with(user(EMAIL).roles("PENGGUNA")).with(csrf())
                        .param("currentPassword", OLD_PASSWORD))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
    @Test void manipulatedDeletionIdentifierCannotDeactivateOtherAccount() throws Exception {
        User other = users.saveAndFlush(new User("Other", "other-delete@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        mvc.perform(post("/account/delete").with(user(EMAIL).roles("PENGGUNA")).with(csrf())
                .param("id", other.getId().toString()).param("userId", other.getId().toString())
                .param("confirmed", "true").param("currentPassword", OLD_PASSWORD))
                .andExpect(redirectedUrl("/login?accountDeactivated"));
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(other.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "PETUGAS"})
    void privilegedDeletionRejectedByHttpAndService(Role role) throws Exception {
        owner.setRole(role);
        users.saveAndFlush(owner);
        mvc.perform(get("/account/delete").with(user(EMAIL).roles(role.name()))).andExpect(status().isForbidden());
        mvc.perform(post("/account/delete").with(user(EMAIL).roles(role.name())).with(csrf())
                        .param("confirmed", "true").param("currentPassword", OLD_PASSWORD)).andExpect(status().isForbidden());
        // Even an incorrect/stale authority cannot bypass the persistent role check.
        mvc.perform(post("/account/delete").with(user(EMAIL).roles("PENGGUNA")).with(csrf())
                        .param("confirmed", "true").param("currentPassword", OLD_PASSWORD)).andExpect(status().isForbidden());
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test @WithPrismUser(username = EMAIL, roles = "PENGGUNA")
    void alreadyInactiveAccountCannotMutate() {
        owner.setAccountStatus(AccountStatus.INACTIVE);
        users.saveAndFlush(owner);
        var profile = new AccountProfileForm(); profile.setName("Changed");
        assertThatThrownBy(() -> service.updateProfile(profile)).isInstanceOf(AccessDeniedException.class);
        var deletion = new AccountDeletionForm(); deletion.setConfirmed(true); deletion.setCurrentPassword(OLD_PASSWORD);
        assertThatThrownBy(() -> service.deactivateAccount(deletion)).isInstanceOf(AccessDeniedException.class);
        var password = new AccountPasswordForm();
        password.setCurrentPassword(OLD_PASSWORD); password.setNewPassword(NEW_PASSWORD); password.setConfirmPassword(NEW_PASSWORD);
        assertThatThrownBy(() -> service.changePassword(password)).isInstanceOf(AccessDeniedException.class);
        assertThat(encoder.matches(OLD_PASSWORD, owner.getPasswordHash())).isTrue();
        assertThat(owner.getName()).isEqualTo("Original");
    }

    @Test void deactivationRevokesAnotherRealLoginSessionOnNextRequest() throws Exception {
        MockHttpSession first = login(OLD_PASSWORD);
        MockHttpSession second = login(OLD_PASSWORD);
        mvc.perform(post("/account/delete").session(first).with(csrf()).param("confirmed", "true")
                .param("currentPassword", OLD_PASSWORD)).andExpect(redirectedUrl("/login?accountDeactivated"));
        mvc.perform(get("/account").session(second)).andExpect(redirectedUrl("/login?inactive"));
        assertThat(second.isInvalid()).isTrue();
    }
    @Test void administrativeDeactivationAlsoRevokesExistingRealLogin() throws Exception {
        MockHttpSession session = login(OLD_PASSWORD);
        owner.setAccountStatus(AccountStatus.INACTIVE); users.saveAndFlush(owner);
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login?inactive"));
        assertThat(session.isInvalid()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"/account", "/account/password", "/account/delete"})
    void unauthenticatedViewsRedirectToLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }
    @ParameterizedTest @ValueSource(strings = {"/account/profile", "/account/password", "/account/delete"})
    void unauthenticatedMutationsRedirectAndCsrfRequired(String path) throws Exception {
        mvc.perform(post(path).with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(post(path).with(user(EMAIL).roles("PENGGUNA"))).andExpect(status().isForbidden());
        assertThat(owner.getName()).isEqualTo("Original");
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private MockHttpSession login(String password) throws Exception {
        SecurityContextHolder.clearContext();
        return (MockHttpSession) mvc.perform(formLogin().user(EMAIL).password(password))
                .andExpect(authenticated().withUsername(EMAIL)).andReturn().getRequest().getSession(false);
    }
    private void deleteSuccessfully() throws Exception {
        mvc.perform(post("/account/delete").with(user(EMAIL).roles("PENGGUNA")).with(csrf())
                .param("confirmed", "true").param("currentPassword", OLD_PASSWORD))
                .andExpect(redirectedUrl("/login?accountDeactivated"));
        assertThat(owner.getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
    }
    private Facility room() {
        FacilityType type = facilityTypes.saveAndFlush(new FacilityType("ACC-CLASS", "Kelas", null));
        return facilities.saveAndFlush(new Facility("ACC-ROOM", "Room", type, "Gedung C", 20,
                null, AdministrativeStatus.ACTIVE));
    }
    @TestConfiguration static class FixedClockConfiguration {
        @Bean @Primary Clock fixedClock() {
            ZoneId zone = ZoneId.of("Asia/Jakarta");
            return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
        }
    }
}
